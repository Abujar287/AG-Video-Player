import { memo, useEffect, useState } from 'react';
import { Film } from 'lucide-react';
import { resolvePlayableUrl } from '../repositories/videoLibraryRepository';

const BUNDLED_STATIC_THUMBS: Record<string, string> = {
  '/preview_videos/Downloads/Sample_Lecture_Clip.mp4': '/preview_videos/Downloads/Sample_Lecture_Clip_thumb.jpg',
  '/preview_videos/Movies/Cinema_Test_Reel.mp4': '/preview_videos/Movies/Cinema_Test_Reel_thumb.jpg',
  '/preview_videos/Movies/Neon_Horizon_Preview.mp4': '/preview_videos/Movies/Neon_Horizon_Preview_thumb.jpg',
  '/preview_videos/Movies/Ocean_Pulse_Clip.mp4': '/preview_videos/Movies/Ocean_Pulse_Clip_thumb.jpg',
};

const thumbnailMemoryCache = new Map<string, string>();
const inFlightPromises = new Map<string, Promise<string | null>>();
const MAX_CACHE_SIZE = 150;

// Initialize cache from session storage if available
try {
  const stored = sessionStorage.getItem('ag_thumb_cache_v1');
  if (stored) {
    const parsed = JSON.parse(stored) as Record<string, string>;
    for (const [k, v] of Object.entries(parsed)) {
      thumbnailMemoryCache.set(k, v);
    }
  }
} catch {
  // Ignore quota or session storage errors
}

function persistThumbToStorage(uri: string, dataUrl: string) {
  thumbnailMemoryCache.set(uri, dataUrl);
  if (thumbnailMemoryCache.size > MAX_CACHE_SIZE) {
    const oldestKey = thumbnailMemoryCache.keys().next().value;
    if (oldestKey) thumbnailMemoryCache.delete(oldestKey);
  }
  // Throttled background save
  try {
    const obj: Record<string, string> = {};
    let count = 0;
    for (const [k, v] of thumbnailMemoryCache.entries()) {
      if (count++ > 30) break;
      obj[k] = v;
    }
    sessionStorage.setItem('ag_thumb_cache_v1', JSON.stringify(obj));
  } catch {
    // Ignore storage quota
  }
}

// Concurrency queue to protect mobile video hardware decoders (max 2 decoders at a time)
let activeDecoderCount = 0;
const decoderQueue: Array<() => void> = [];

function acquireDecoderSlot(): Promise<void> {
  if (activeDecoderCount < 2) {
    activeDecoderCount++;
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    decoderQueue.push(() => {
      activeDecoderCount++;
      resolve();
    });
  });
}

function releaseDecoderSlot(): void {
  activeDecoderCount--;
  const next = decoderQueue.shift();
  if (next) {
    next();
  }
}

async function extractVideoThumbnail(srcUrl: string): Promise<string | null> {
  await acquireDecoderSlot();

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
      releaseDecoderSlot();
    };

    const finish = (dataUrl: string | null) => {
      if (settled) return;
      settled = true;
      cleanup();
      resolve(dataUrl);
    };

    // Fast timeout of 3.5s to never block the UI
    const timer = window.setTimeout(() => finish(null), 3500);

    video.onloadedmetadata = () => {
      const targetTime =
        Number.isFinite(video.duration) && video.duration > 0
          ? Math.min(1.0, Math.max(0.1, video.duration * 0.2))
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

function getOrFetchThumbnail(uriString: string): Promise<string | null> {
  // Static bundled preview check
  if (BUNDLED_STATIC_THUMBS[uriString]) {
    return Promise.resolve(BUNDLED_STATIC_THUMBS[uriString]);
  }

  // Memory cache check
  const cached = thumbnailMemoryCache.get(uriString);
  if (cached) {
    return Promise.resolve(cached);
  }

  // In-flight request deduplication
  const inFlight = inFlightPromises.get(uriString);
  if (inFlight) {
    return inFlight;
  }

  const playableUrl = resolvePlayableUrl(uriString);
  const promise = extractVideoThumbnail(playableUrl)
    .then((result) => {
      if (result) {
        persistThumbToStorage(uriString, result);
      }
      return result;
    })
    .finally(() => {
      inFlightPromises.delete(uriString);
    });

  inFlightPromises.set(uriString, promise);
  return promise;
}

interface VideoThumbnailProps {
  uriString: string;
  alt?: string;
  className?: string;
}

export const VideoThumbnail = memo(function VideoThumbnail({
  uriString,
  alt = '',
  className = '',
}: VideoThumbnailProps) {
  const [thumbUrl, setThumbUrl] = useState<string | null>(() => {
    return BUNDLED_STATIC_THUMBS[uriString] ?? thumbnailMemoryCache.get(uriString) ?? null;
  });

  useEffect(() => {
    // If already has static or cached thumb, use it instantly
    const staticThumb = BUNDLED_STATIC_THUMBS[uriString];
    if (staticThumb) {
      setThumbUrl(staticThumb);
      return;
    }

    const cached = thumbnailMemoryCache.get(uriString);
    if (cached) {
      setThumbUrl(cached);
      return;
    }

    let cancelled = false;
    void getOrFetchThumbnail(uriString).then((url) => {
      if (!cancelled && url) {
        setThumbUrl(url);
      }
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
          loading="lazy"
          className="h-full w-full object-cover transition-opacity duration-200"
          draggable={false}
        />
      ) : (
        <Film className="h-7 w-7 text-[var(--primary)] opacity-70" />
      )}
    </div>
  );
});
