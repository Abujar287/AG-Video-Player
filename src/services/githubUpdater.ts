import type { GitHubReleaseInfo, UpdateUiState } from '../types/media';

export const APP_VERSION_NAME = '1.0.33';
export const APP_VERSION_CODE = 33;
export const DEFAULT_GITHUB_REPO_SLUG =
  (import.meta.env.VITE_GITHUB_REPO_SLUG as string | undefined)?.trim() || 'Abujar287/AG-Video-Player';

export function getCurrentAppVersionLabel(): string {
  return `v${APP_VERSION_NAME}`;
}

export function isNewerVersion(
  currentVersionName: string,
  currentVersionCode: number,
  remoteVersionName: string,
  remoteVersionCode: number | null
): boolean {
  if (remoteVersionCode !== null) {
    if (remoteVersionCode > currentVersionCode) return true;
    if (remoteVersionCode < currentVersionCode) return false;
  }
  const parseParts = (v: string) =>
    v.split('.').map((p) => {
      const digits = p.replace(/\D/g, '');
      return digits ? parseInt(digits, 10) : 0;
    });

  const currParts = parseParts(currentVersionName);
  const remParts = parseParts(remoteVersionName);
  const maxLen = Math.max(currParts.length, remParts.length);

  for (let i = 0; i < maxLen; i++) {
    const c = currParts[i] ?? 0;
    const r = remParts[i] ?? 0;
    if (r > c) return true;
    if (r < c) return false;
  }
  return false;
}

function compareVersions(
  nameA: string,
  codeA: number | null,
  nameB: string,
  codeB: number | null
): number {
  if (codeA !== null && codeB !== null && codeA !== codeB) {
    return codeA - codeB;
  }
  const parseParts = (v: string) =>
    v.split('.').map((p) => {
      const digits = p.replace(/\D/g, '');
      return digits ? parseInt(digits, 10) : 0;
    });

  const partsA = parseParts(nameA);
  const partsB = parseParts(nameB);
  const maxLen = Math.max(partsA.length, partsB.length);

  for (let i = 0; i < maxLen; i++) {
    const a = partsA[i] ?? 0;
    const b = partsB[i] ?? 0;
    if (a !== b) return a - b;
  }
  return (codeA ?? 0) - (codeB ?? 0);
}

function parseOptionalVersionCode(notes: string): number | null {
  const match = notes.match(/versionCode\s*[:=]\s*(\d+)/i);
  return match?.[1] ? parseInt(match[1], 10) : null;
}

interface GitHubAssetJson {
  name?: string;
  browser_download_url?: string;
  size?: number;
}

interface GitHubReleaseJson {
  tag_name?: string;
  name?: string;
  body?: string;
  html_url?: string;
  draft?: boolean;
  assets?: GitHubAssetJson[];
}

function parseRelease(releaseObj: GitHubReleaseJson, cleanedSlug: string): GitHubReleaseInfo | null {
  if (releaseObj.draft) return null;
  const tagName = (releaseObj.tag_name ?? '').trim();
  if (!tagName) return null;

  const apkAsset = (releaseObj.assets ?? []).find((a) =>
    (a.name ?? '').toLowerCase().endsWith('.apk')
  );
  const downloadUrl = apkAsset?.browser_download_url;
  if (!downloadUrl) return null;

  const remoteVersionName = tagName.replace(/^v/i, '') || '0.0.0';
  const releaseTitle = (releaseObj.name ?? tagName).trim() || tagName;
  const releaseNotes = releaseObj.body ?? '';
  const htmlUrl = releaseObj.html_url || `https://github.com/${cleanedSlug}/releases/tag/${tagName}`;
  const remoteVersionCode = parseOptionalVersionCode(releaseNotes);

  return {
    versionName: remoteVersionName,
    versionCode: remoteVersionCode,
    releaseTitle,
    releaseNotes,
    apkDownloadUrl: downloadUrl,
    apkFileName: apkAsset?.name || 'AG Video Player.apk',
    apkSizeBytes: apkAsset?.size ?? 0,
    htmlUrl,
  };
}

export async function checkForUpdate(repoSlug: string): Promise<UpdateUiState> {
  const cleanedSlug = (repoSlug || DEFAULT_GITHUB_REPO_SLUG)
    .trim()
    .replace(/^https?:\/\/github\.com\//i, '')
    .replace(/^\/+|\/+$/g, '');

  if (!cleanedSlug.includes('/') || cleanedSlug.toLowerCase() === 'owner/ag-video-player') {
    return { status: 'error', message: 'GitHub repository not linked in this build.' };
  }

  try {
    const candidates: GitHubReleaseInfo[] = [];
    let networkSuccess = false;

    const [latestRes, listRes] = await Promise.allSettled([
      fetch(`https://api.github.com/repos/${cleanedSlug}/releases/latest`, {
        headers: { Accept: 'application/vnd.github+json' },
        cache: 'no-store',
      }),
      fetch(`https://api.github.com/repos/${cleanedSlug}/releases?per_page=30`, {
        headers: { Accept: 'application/vnd.github+json' },
        cache: 'no-store',
      }),
    ]);

    if (latestRes.status === 'fulfilled' && latestRes.value.ok) {
      networkSuccess = true;
      const json = (await latestRes.value.json()) as GitHubReleaseJson;
      const parsed = parseRelease(json, cleanedSlug);
      if (parsed) candidates.push(parsed);
    }

    if (listRes.status === 'fulfilled' && listRes.value.ok) {
      networkSuccess = true;
      const arr = (await listRes.value.json()) as GitHubReleaseJson[];
      for (const item of arr) {
        const parsed = parseRelease(item, cleanedSlug);
        if (parsed) candidates.push(parsed);
      }
    }

    let bestRelease: GitHubReleaseInfo | null = null;
    for (const c of candidates) {
      if (
        !bestRelease ||
        compareVersions(c.versionName, c.versionCode, bestRelease.versionName, bestRelease.versionCode) > 0
      ) {
        bestRelease = c;
      }
    }

    if (bestRelease) {
      if (!isNewerVersion(APP_VERSION_NAME, APP_VERSION_CODE, bestRelease.versionName, bestRelease.versionCode)) {
        return { status: 'up_to_date', versionName: APP_VERSION_NAME };
      }
      return { status: 'available', release: bestRelease };
    }

    if (networkSuccess) {
      return { status: 'up_to_date', versionName: APP_VERSION_NAME };
    }

    return { status: 'error', message: 'Could not reach update server. Please try again.' };
  } catch {
    return { status: 'error', message: 'Unable to check for updates right now.' };
  }
}

export async function downloadApkInBrowser(
  release: GitHubReleaseInfo,
  onProgress: (downloaded: number, total: number, percent: number) => void,
  signal: AbortSignal
): Promise<'completed' | 'aborted' | 'fallback_link'> {
  try {
    const res = await fetch(release.apkDownloadUrl, { signal, cache: 'no-store' });
    if (!res.ok || !res.body) {
      return 'fallback_link';
    }
    const headerLen = Number(res.headers.get('content-length') || 0);
    const total = headerLen > 0 ? headerLen : release.apkSizeBytes;
    const reader = res.body.getReader();
    const chunks: BlobPart[] = [];
    let downloaded = 0;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (value) {
        chunks.push(value);
        downloaded += value.byteLength;
        const pct = total > 0 ? Math.min(99, Math.floor((downloaded * 100) / total)) : 0;
        onProgress(downloaded, total, pct);
      }
    }

    onProgress(downloaded, Math.max(total, downloaded), 100);
    const blob = new Blob(chunks, { type: 'application/vnd.android.package-archive' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = release.apkFileName || `AG-Video-Player-v${release.versionName}.apk`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
    return 'completed';
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      return 'aborted';
    }
    return 'fallback_link';
  }
}
