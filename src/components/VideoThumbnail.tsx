import { useEffect, useState } from 'react';
import { Film } from 'lucide-react';
import { resolvePlayableUrl } from '../repositories/videoLibraryRepository';

const thumbnailCache = new Map<string, string>();
const MAX_CACHE_SIZE = 100;

function extractVideoThumbnail(srcUrl: string): Promise<string | null> {
  return new Promise((resolve) => {
    const video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;
    video.preload = 'metadata';
    let settled = false;

    const cleanup = () => {
      video.onloadedmetadata = null;
      video.onseeked = null;
      video.onerror = null;
      video.removeAttribute('src');
      video.load();
    };

    const finish = (dataUrl: string | null) => {
      if (settled) return;
      settled = true;
      cleanup();
      resolve(dataUrl);
    };

    const timer = window.setTimeout(() => finish(null), 5000);

    video.onloadedmetadata = () => {
      const targetTime =
        Number.isFinite(video.duration) && video.duration > 0
          ? Math.min(1.0, Math.max(0.1, video.duration * 0.25))
          : 0.5;
      try {
        video.currentTime = targetTime;
      } catch {
        window.clearTimeout(timer);
        finish(null);
      }
    };

    video.onseeked = () => {
      window.clearTimeout(timer);
      try {
        const w = 320;
        const h = 180;
        const canvas = document.createElement('canvas');
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          finish(null);
          return;
        }
        const vw = video.videoWidth || w;
        const vh = video.videoHeight || h;
        const scale = Math.max(w / vw, h / vh);
        const dw = vw * scale;
        const dh = vh * scale;
        ctx.drawImage(video, (w - dw) / 2, (h - dh) / 2, dw, dh);
        const url = canvas.toDataURL('image/jpeg', 0.8);
        finish(url);
      } catch {
        finish(null);
      }
    };

    video.onerror = () => {
      window.clearTimeout(timer);
      finish(null);
    };

    video.src = srcUrl;
  });
}

interface VideoThumbnailProps {
  uriString: string;
  alt?: string;
  className?: string;
}

export function VideoThumbnail({ uriString, alt = '', className = '' }: VideoThumbnailProps) {
  const [thumbUrl, setThumbUrl] = useState<string | null>(() => thumbnailCache.get(uriString) ?? null);

  useEffect(() => {
    const cached = thumbnailCache.get(uriString);
    if (cached) {
      setThumbUrl(cached);
      return;
    }

    let cancelled = false;
    const playableUrl = resolvePlayableUrl(uriString);
    void extractVideoThumbnail(playableUrl).then((dataUrl) => {
      if (cancelled || !dataUrl) return;
      if (thumbnailCache.size >= MAX_CACHE_SIZE) {
        const oldestKey = thumbnailCache.keys().next().value;
        if (oldestKey) thumbnailCache.delete(oldestKey);
      }
      thumbnailCache.set(uriString, dataUrl);
      setThumbUrl(dataUrl);
    });

    return () => {
      cancelled = true;
    };
  }, [uriString]);

  return (
    <div
      className={`relative flex items-center justify-center overflow-hidden bg-[var(--bg-surface-variant)] ${className}`}
    >
      {thumbUrl ? (
        <img
          src={thumbUrl}
          alt={alt}
          className="h-full w-full object-cover"
          draggable={false}
        />
      ) : (
        <Film className="h-7 w-7 text-[var(--primary)] opacity-70" />
      )}
    </div>
  );
}
