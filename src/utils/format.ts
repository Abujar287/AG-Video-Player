export function formatDuration(durationMs: number): string {
  if (!Number.isFinite(durationMs) || durationMs <= 0) return '00:00';
  const totalSeconds = Math.floor(durationMs / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const mm = String(minutes).padStart(2, '0');
  const ss = String(seconds).padStart(2, '0');
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

export function formatSignedDelta(deltaMs: number): string {
  const sign = deltaMs >= 0 ? '+' : '-';
  return `${sign}${formatDuration(Math.abs(deltaMs))}`;
}

export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
  const kb = 1024;
  const mb = kb * 1024;
  const gb = mb * 1024;
  if (bytes >= gb) return `${(bytes / gb).toFixed(2)} GB`;
  if (bytes >= mb) return `${(bytes / mb).toFixed(1)} MB`;
  if (bytes >= kb) return `${Math.round(bytes / kb)} KB`;
  return `${bytes} B`;
}

export function formatResolution(width: number, height: number): string {
  if (width <= 0 || height <= 0) return 'Video';
  const minDim = Math.min(width, height);
  let badge: string;
  if (minDim >= 2160) badge = '4K';
  else if (minDim >= 1440) badge = '2K';
  else if (minDim >= 1080) badge = '1080p';
  else if (minDim >= 720) badge = '720p';
  else if (minDim >= 480) badge = '480p';
  else badge = `${minDim}p`;
  return `${badge} · ${width}×${height}`;
}
