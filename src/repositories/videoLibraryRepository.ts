import type { LibrarySnapshot, VideoFolder, VideoItem } from '../types/media';

const DB_NAME = 'ag_video_player_db';
const DB_VERSION = 1;
const STORE_VIDEOS = 'imported_videos';
const BUNDLED_OVERRIDES_KEY = 'ag_player_bundled_overrides_v1';

interface BundledAssetSeed {
  id: number;
  uriString: string;
  fileName: string;
  folderName: string;
  relativePath: string;
  fallbackSizeBytes: number;
}

const BUNDLED_PREVIEW_SEEDS: BundledAssetSeed[] = [
  {
    id: 90001,
    uriString: '/preview_videos/Downloads/Sample_Lecture_Clip.mp4',
    fileName: 'Sample_Lecture_Clip.mp4',
    folderName: 'Downloads',
    relativePath: 'Downloads/',
    fallbackSizeBytes: 328000,
  },
  {
    id: 90002,
    uriString: '/preview_videos/Movies/Cinema_Test_Reel.mp4',
    fileName: 'Cinema_Test_Reel.mp4',
    folderName: 'Movies',
    relativePath: 'Movies/',
    fallbackSizeBytes: 194000,
  },
  {
    id: 90003,
    uriString: '/preview_videos/Movies/Neon_Horizon_Preview.mp4',
    fileName: 'Neon_Horizon_Preview.mp4',
    folderName: 'Movies',
    relativePath: 'Movies/',
    fallbackSizeBytes: 146000,
  },
  {
    id: 90004,
    uriString: '/preview_videos/Movies/Ocean_Pulse_Clip.mp4',
    fileName: 'Ocean_Pulse_Clip.mp4',
    folderName: 'Movies',
    relativePath: 'Movies/',
    fallbackSizeBytes: 170000,
  },
];

interface BundledOverride {
  title?: string;
  displayName?: string;
  folderName?: string;
  relativePath?: string;
  bucketId?: string;
  deleted?: boolean;
}

interface StoredImportedVideo {
  uriString: string;
  id: number;
  title: string;
  displayName: string;
  bucketId: string;
  folderName: string;
  relativePath: string;
  durationMs: number;
  sizeBytes: number;
  width: number;
  height: number;
  dateModifiedSec: number;
  mimeType: string;
  blob: Blob;
}

const objectUrlRegistry = new Map<string, string>();
const BUNDLED_STATIC_METADATA: Record<
  string,
  { durationMs: number; width: number; height: number; sizeBytes: number }
> = {
  '/preview_videos/Downloads/Sample_Lecture_Clip.mp4': {
    durationMs: 25000,
    width: 480,
    height: 270,
    sizeBytes: 327828,
  },
  '/preview_videos/Movies/Cinema_Test_Reel.mp4': {
    durationMs: 40000,
    width: 480,
    height: 270,
    sizeBytes: 193983,
  },
  '/preview_videos/Movies/Neon_Horizon_Preview.mp4': {
    durationMs: 30000,
    width: 480,
    height: 270,
    sizeBytes: 145494,
  },
  '/preview_videos/Movies/Ocean_Pulse_Clip.mp4': {
    durationMs: 35000,
    width: 480,
    height: 270,
    sizeBytes: 170375,
  },
};

const metadataCache = new Map<
  string,
  { durationMs: number; width: number; height: number; sizeBytes: number }
>(Object.entries(BUNDLED_STATIC_METADATA));

export function resolvePlayableUrl(uriString: string): string {
  return objectUrlRegistry.get(uriString) ?? uriString;
}

function getBundledOverrides(): Record<string, BundledOverride> {
  try {
    const raw = localStorage.getItem(BUNDLED_OVERRIDES_KEY);
    if (!raw) return {};
    return JSON.parse(raw) as Record<string, BundledOverride>;
  } catch {
    return {};
  }
}

function saveBundledOverrides(map: Record<string, BundledOverride>): void {
  try {
    localStorage.setItem(BUNDLED_OVERRIDES_KEY, JSON.stringify(map));
  } catch {
    // Ignore
  }
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_VIDEOS)) {
        db.createObjectStore(STORE_VIDEOS, { keyPath: 'uriString' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function getAllImportedVideos(): Promise<StoredImportedVideo[]> {
  try {
    const db = await openDb();
    return await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_VIDEOS, 'readonly');
      const store = tx.objectStore(STORE_VIDEOS);
      const req = store.getAll();
      req.onsuccess = () => resolve((req.result as StoredImportedVideo[]) ?? []);
      req.onerror = () => reject(req.error);
    });
  } catch {
    return [];
  }
}

async function putImportedVideo(record: StoredImportedVideo): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_VIDEOS, 'readwrite');
    tx.objectStore(STORE_VIDEOS).put(record);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

async function deleteImportedVideo(uriString: string): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_VIDEOS, 'readwrite');
    tx.objectStore(STORE_VIDEOS).delete(uriString);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export function probeVideoMetadata(
  srcUrl: string,
  fallbackSize = 0
): Promise<{ durationMs: number; width: number; height: number; sizeBytes: number }> {
  const cached = metadataCache.get(srcUrl);
  if (cached) return Promise.resolve(cached);

  return new Promise((resolve) => {
    const video = document.createElement('video');
    video.preload = 'metadata';
    video.muted = true;
    let settled = false;

    const finish = (durationMs: number, width: number, height: number, sizeBytes: number) => {
      if (settled) return;
      settled = true;
      video.removeAttribute('src');
      video.load();
      const result = { durationMs, width, height, sizeBytes };
      metadataCache.set(srcUrl, result);
      resolve(result);
    };

    const timer = window.setTimeout(() => {
      finish(15000, 1280, 720, fallbackSize);
    }, 3500);

    video.onloadedmetadata = async () => {
      window.clearTimeout(timer);
      const dur = Number.isFinite(video.duration) && video.duration > 0 ? Math.round(video.duration * 1000) : 15000;
      const w = video.videoWidth || 1280;
      const h = video.videoHeight || 720;
      let sizeBytes = fallbackSize;
      if (!sizeBytes && srcUrl.startsWith('/')) {
        try {
          const head = await fetch(srcUrl, { method: 'HEAD' });
          const len = head.headers.get('content-length');
          if (len) sizeBytes = parseInt(len, 10) || fallbackSize;
        } catch {
          // ignore
        }
      }
      finish(dur, w, h, sizeBytes);
    };

    video.onerror = () => {
      window.clearTimeout(timer);
      finish(15000, 1280, 720, fallbackSize);
    };

    video.src = srcUrl;
  });
}

export async function importLocalVideoFiles(
  files: File[],
  folderName = 'Local Videos'
): Promise<VideoItem[]> {
  const imported: VideoItem[] = [];
  const nowSec = Math.floor(Date.now() / 1000);

  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    if (!file.type.startsWith('video/') && !/\.(mp4|webm|mkv|mov|m4v|avi|3gp)$/i.test(file.name)) {
      continue;
    }

    const uriString = `idb://local/${Date.now()}_${i}_${encodeURIComponent(file.name)}`;
    const blobUrl = URL.createObjectURL(file);
    objectUrlRegistry.set(uriString, blobUrl);

    const meta = await probeVideoMetadata(blobUrl, file.size);
    const title = file.name.replace(/\.[^/.]+$/, '') || file.name;
    const cleanFolder = folderName.trim().replace(/^\/+|\/+$/g, '') || 'Local Videos';
    const leafFolder = cleanFolder.split('/').pop() || 'Local Videos';
    const bucketId = `local_${cleanFolder}`;

    const record: StoredImportedVideo = {
      id: Date.now() + i,
      uriString,
      title,
      displayName: file.name,
      bucketId,
      folderName: leafFolder,
      relativePath: `${cleanFolder}/`,
      durationMs: meta.durationMs,
      sizeBytes: file.size,
      width: meta.width,
      height: meta.height,
      dateModifiedSec: Math.floor((file.lastModified || Date.now()) / 1000) || nowSec,
      mimeType: file.type || 'video/mp4',
      blob: file,
    };

    await putImportedVideo(record);
    imported.push({
      id: record.id,
      uriString: record.uriString,
      title: record.title,
      displayName: record.displayName,
      bucketId: record.bucketId,
      folderName: record.folderName,
      relativePath: record.relativePath,
      filePath: `${record.relativePath}${record.displayName}`,
      durationMs: record.durationMs,
      sizeBytes: record.sizeBytes,
      width: record.width,
      height: record.height,
      dateModifiedSec: record.dateModifiedSec,
      mimeType: record.mimeType,
      isBundled: false,
    });
  }

  return imported;
}

export async function loadLibrarySnapshot(showHiddenFiles: boolean): Promise<LibrarySnapshot> {
  const overrides = getBundledOverrides();
  const nowSec = Math.floor(Date.now() / 1000);

  const bundledPromises = BUNDLED_PREVIEW_SEEDS.map(async (seed, index): Promise<VideoItem | null> => {
    const ov = overrides[seed.uriString];
    if (ov?.deleted) return null;

    const meta = await probeVideoMetadata(seed.uriString, seed.fallbackSizeBytes);
    const displayName = ov?.displayName ?? seed.fileName;
    const title = ov?.title ?? displayName.replace(/\.[^/.]+$/, '');
    const relativePath = ov?.relativePath ?? seed.relativePath;
    const folderName = ov?.folderName ?? seed.folderName;
    const bucketId = ov?.bucketId ?? `preview_${folderName}`;

    return {
      id: seed.id,
      uriString: seed.uriString,
      title,
      displayName,
      bucketId,
      folderName,
      relativePath,
      filePath: `${relativePath}${displayName}`,
      durationMs: meta.durationMs,
      sizeBytes: meta.sizeBytes,
      width: meta.width,
      height: meta.height,
      dateModifiedSec: nowSec - (index + 1) * 600,
      mimeType: 'video/mp4',
      isBundled: true,
    };
  });

  const bundled = (await Promise.all(bundledPromises)).filter((v): v is VideoItem => v !== null);
  const storedImported = await getAllImportedVideos();

  const importedItems: VideoItem[] = storedImported.map((rec) => {
    if (!objectUrlRegistry.has(rec.uriString) && rec.blob) {
      objectUrlRegistry.set(rec.uriString, URL.createObjectURL(rec.blob));
    }
    return {
      id: rec.id,
      uriString: rec.uriString,
      title: rec.title,
      displayName: rec.displayName,
      bucketId: rec.bucketId,
      folderName: rec.folderName,
      relativePath: rec.relativePath,
      filePath: `${rec.relativePath}${rec.displayName}`,
      durationMs: rec.durationMs,
      sizeBytes: rec.sizeBytes,
      width: rec.width,
      height: rec.height,
      dateModifiedSec: rec.dateModifiedSec,
      mimeType: rec.mimeType,
      isBundled: false,
    };
  });

  const allRaw = [...importedItems, ...bundled];
  const visible = showHiddenFiles
    ? allRaw
    : allRaw.filter((v) => {
        const hasHiddenName = v.displayName.startsWith('.');
        const hasHiddenSegment = v.relativePath.split('/').some((seg) => seg.startsWith('.'));
        return !hasHiddenName && !hasHiddenSegment;
      });

  const videosByFolder: Record<string, VideoItem[]> = {};
  for (const item of visible) {
    if (!videosByFolder[item.bucketId]) {
      videosByFolder[item.bucketId] = [];
    }
    videosByFolder[item.bucketId].push(item);
  }

  const folders: VideoFolder[] = Object.entries(videosByFolder)
    .map(([bucketId, items]) => {
      const first = items[0];
      const latestModifiedSec = Math.max(...items.map((i) => i.dateModifiedSec));
      const totalSizeBytes = items.reduce((acc, i) => acc + i.sizeBytes, 0);
      const pathHint = first.relativePath.replace(/\/+$/, '') || first.folderName;
      return {
        bucketId,
        name: first.folderName,
        pathHint,
        videoCount: items.length,
        totalSizeBytes,
        latestModifiedSec,
        previewVideoId: first.id,
        previewUri: first.uriString,
      };
    })
    .sort((a, b) => b.latestModifiedSec - a.latestModifiedSec);

  return {
    folders,
    videosByFolder,
    allVideos: visible,
  };
}

export async function renameVideoItem(
  video: VideoItem,
  newTitleInput: string
): Promise<{ ok: boolean; message: string }> {
  const cleanBase = newTitleInput.trim().replace(/[\\/:*?"<>|]/g, '_');
  if (!cleanBase) {
    return { ok: false, message: 'Name cannot be empty.' };
  }

  const ext = video.displayName.includes('.') ? video.displayName.split('.').pop() || '' : '';
  const newDisplayName =
    ext && !cleanBase.toLowerCase().endsWith(`.${ext.toLowerCase()}`) ? `${cleanBase}.${ext}` : cleanBase;
  const newTitle = newDisplayName.replace(/\.[^/.]+$/, '');

  if (video.isBundled) {
    const overrides = getBundledOverrides();
    overrides[video.uriString] = {
      ...overrides[video.uriString],
      title: newTitle,
      displayName: newDisplayName,
    };
    saveBundledOverrides(overrides);
    return { ok: true, message: `Renamed to ${newDisplayName}` };
  }

  const all = await getAllImportedVideos();
  const target = all.find((v) => v.uriString === video.uriString);
  if (!target) {
    return { ok: false, message: 'Source file not found.' };
  }
  await putImportedVideo({
    ...target,
    title: newTitle,
    displayName: newDisplayName,
  });
  return { ok: true, message: `Renamed to ${newDisplayName}` };
}

export async function moveVideoItems(
  videos: VideoItem[],
  targetPathInput: string
): Promise<{ ok: boolean; message: string }> {
  if (videos.length === 0) {
    return { ok: false, message: 'No videos selected.' };
  }

  const cleanPath =
    targetPathInput
      .trim()
      .replace(/^\/+|\/+$/g, '')
      .replace(/\\/g, '/')
      .replace(/[:*?"<>|]/g, '_') || 'Movies/Moved';

  const leafFolder = cleanPath.split('/').pop() || cleanPath;
  const newRelativePath = `${cleanPath}/`;
  const overrides = getBundledOverrides();
  const imported = await getAllImportedVideos();
  let movedCount = 0;

  for (const v of videos) {
    const newBucketId = v.isBundled ? `preview_${cleanPath}` : `local_${cleanPath}`;
    if (v.isBundled) {
      overrides[v.uriString] = {
        ...overrides[v.uriString],
        folderName: leafFolder,
        relativePath: newRelativePath,
        bucketId: newBucketId,
      };
      movedCount++;
    } else {
      const rec = imported.find((r) => r.uriString === v.uriString);
      if (rec) {
        await putImportedVideo({
          ...rec,
          folderName: leafFolder,
          relativePath: newRelativePath,
          bucketId: newBucketId,
        });
        movedCount++;
      }
    }
  }

  saveBundledOverrides(overrides);
  if (movedCount > 0) {
    return { ok: true, message: `Moved ${movedCount} video(s) to ${cleanPath}` };
  }
  return { ok: false, message: 'Could not move selected video(s).' };
}

export async function deleteVideoItems(
  videos: VideoItem[]
): Promise<{ ok: boolean; message: string }> {
  if (videos.length === 0) {
    return { ok: false, message: 'No videos selected.' };
  }

  const overrides = getBundledOverrides();
  let deletedCount = 0;

  for (const v of videos) {
    if (v.isBundled) {
      overrides[v.uriString] = {
        ...overrides[v.uriString],
        deleted: true,
      };
      deletedCount++;
    } else {
      await deleteImportedVideo(v.uriString);
      const url = objectUrlRegistry.get(v.uriString);
      if (url) {
        URL.revokeObjectURL(url);
        objectUrlRegistry.delete(v.uriString);
      }
      deletedCount++;
    }
  }

  saveBundledOverrides(overrides);
  return { ok: true, message: `Deleted ${deletedCount} video(s)` };
}

export async function restoreBundledSamples(): Promise<void> {
  localStorage.removeItem(BUNDLED_OVERRIDES_KEY);
}
