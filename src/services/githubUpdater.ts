import type { GitHubReleaseInfo, UpdateUiState } from '../types/media';

export const APP_VERSION_NAME = '1.0.0';
export const APP_VERSION_CODE = 1;
export const DEFAULT_GITHUB_REPO_SLUG = 'Abujar287/AG-Video-Player';

export function getCurrentAppVersionLabel(): string {
  return `v${APP_VERSION_NAME} (${APP_VERSION_CODE})`;
}

export function isNewerVersion(
  currentVersionName: string,
  currentVersionCode: number,
  remoteVersionName: string,
  remoteVersionCode: number | null
): boolean {
  if (remoteVersionCode !== null && remoteVersionCode > currentVersionCode) {
    return true;
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

function hasApkAsset(release: GitHubReleaseJson): boolean {
  return (release.assets ?? []).some((a) => (a.name ?? '').toLowerCase().endsWith('.apk'));
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
    let releaseObj: GitHubReleaseJson | null = null;
    let apiRateLimited = false;

    const latestRes = await fetch(`https://api.github.com/repos/${cleanedSlug}/releases/latest`, {
      headers: { Accept: 'application/vnd.github+json' },
    });

    if (latestRes.ok) {
      releaseObj = (await latestRes.json()) as GitHubReleaseJson;
    } else if (latestRes.status === 404) {
      const listRes = await fetch(`https://api.github.com/repos/${cleanedSlug}/releases?per_page=10`, {
        headers: { Accept: 'application/vnd.github+json' },
      });
      if (listRes.ok) {
        const list = (await listRes.json()) as GitHubReleaseJson[];
        releaseObj = list.find((r) => !r.draft && hasApkAsset(r)) ?? list.find((r) => !r.draft) ?? null;
      } else if (listRes.status === 403 || listRes.status === 429) {
        apiRateLimited = true;
      }
    } else if (latestRes.status === 403 || latestRes.status === 429) {
      apiRateLimited = true;
    }

    if (releaseObj) {
      const tagName = (releaseObj.tag_name ?? '').trim();
      const remoteVersionName = tagName.replace(/^v/i, '') || '0.0.0';
      const releaseTitle = (releaseObj.name ?? tagName).trim() || tagName;
      const releaseNotes = releaseObj.body ?? '';
      const htmlUrl = releaseObj.html_url || `https://github.com/${cleanedSlug}/releases`;
      const remoteVersionCode = parseOptionalVersionCode(releaseNotes);

      if (!isNewerVersion(APP_VERSION_NAME, APP_VERSION_CODE, remoteVersionName, remoteVersionCode)) {
        return { status: 'up_to_date', versionName: APP_VERSION_NAME };
      }

      const apkAsset = (releaseObj.assets ?? []).find((a) =>
        (a.name ?? '').toLowerCase().endsWith('.apk')
      );
      const downloadUrl = apkAsset?.browser_download_url || htmlUrl;

      const release: GitHubReleaseInfo = {
        versionName: remoteVersionName,
        versionCode: remoteVersionCode,
        releaseTitle,
        releaseNotes,
        apkDownloadUrl: downloadUrl,
        apkFileName: apkAsset?.name || 'AG Video Player.apk',
        apkSizeBytes: apkAsset?.size ?? 0,
        htmlUrl,
      };

      return { status: 'available', release };
    }

    if (apiRateLimited) {
      return { status: 'up_to_date', versionName: APP_VERSION_NAME };
    }
    return { status: 'error', message: 'Could not reach update server. Please try again later.' };
  } catch {
    return { status: 'error', message: 'Unable to check for updates right now.' };
  }
}
