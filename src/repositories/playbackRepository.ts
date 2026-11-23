import type { PlaybackHistoryEntry } from '../types/media';

const HISTORY_KEY = 'ag_player_history_v1';

export function loadPlaybackHistory(): Record<string, PlaybackHistoryEntry> {
  try {
    const raw = localStorage.getItem(HISTORY_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Record<string, PlaybackHistoryEntry>;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

export function savePlaybackHistoryMap(map: Record<string, PlaybackHistoryEntry>): void {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(map));
  } catch {
    // Ignore storage quota errors
  }
}

export function computeSavedPlaybackPosition(positionMs: number, durationMs: number): number {
  if (durationMs <= 0) return 0;
  const clamped = Math.max(0, Math.min(positionMs, durationMs));
  if (durationMs > 5000 && clamped >= durationMs - 1500) {
    return 0;
  }
  return clamped;
}

export function getLatestHistoryEntry(
  historyMap: Record<string, PlaybackHistoryEntry>
): PlaybackHistoryEntry | null {
  const entries = Object.values(historyMap);
  if (entries.length === 0) return null;
  entries.sort((a, b) => b.lastPlayedAt - a.lastPlayedAt);
  return entries[0] ?? null;
}
