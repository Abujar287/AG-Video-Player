import { useState } from 'react';
import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  Calendar,
  Check,
  Folder,
  HardDrive,
  ListVideo,
  Play,
  Plus,
  SortAsc,
} from 'lucide-react';
import {
  type PlaybackHistoryEntry,
  SORT_FIELD_LABELS,
  SortDirection,
  SortField,
  type VideoFolder,
  type VideoItem,
} from '../types/media';
import { formatDuration, formatFileSize, formatResolution } from '../utils/format';

export function SortOptionsDialog({
  currentField,
  currentDirection,
  onDismiss,
  onApply,
}: {
  currentField: SortField;
  currentDirection: SortDirection;
  onDismiss: () => void;
  onApply: (field: SortField, dir: SortDirection) => void;
}) {
  const [field, setField] = useState<SortField>(currentField);
  const [direction, setDirection] = useState<SortDirection>(currentDirection);

  const hint = (dir: SortDirection) => {
    if (field === SortField.NAME || field === SortField.TYPE) {
      return dir === SortDirection.ASC ? 'A → Z' : 'Z → A';
    }
    if (field === SortField.SIZE) {
      return dir === SortDirection.ASC ? 'Smallest' : 'Largest';
    }
    return dir === SortDirection.ASC ? 'Oldest' : 'Newest';
  };

  const iconForField = (f: SortField) => {
    switch (f) {
      case SortField.NAME:
        return SortAsc;
      case SortField.SIZE:
        return HardDrive;
      case SortField.DATE:
        return Calendar;
      case SortField.TYPE:
        return Folder;
      case SortField.PLAYS:
        return Play;
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-[2px]"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-sm rounded-3xl border border-[var(--outline)]/40 bg-[var(--bg-surface)] p-5 text-[var(--text-primary)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Sort By"
      >
        <div className="mb-3 flex flex-col items-center gap-2 text-center">
          <ArrowUpDown className="h-6 w-6 text-[var(--primary)]" />
          <h2 className="text-base font-bold">Sort By</h2>
        </div>

        <div className="space-y-2">
          {Object.values(SortField).map((f) => {
            const selected = field === f;
            const Icon = iconForField(f);
            return (
              <button
                key={f}
                type="button"
                onClick={() => setField(f)}
                className={`flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5 text-sm transition-colors ${
                  selected
                    ? 'bg-[var(--primary-container)] font-semibold text-[var(--on-primary-container)]'
                    : 'bg-[var(--bg-surface-variant)]/55 text-[var(--text-primary)] hover:bg-[var(--bg-surface-variant)]'
                }`}
              >
                <Icon
                  className={`h-4 w-4 shrink-0 ${
                    selected ? 'text-[var(--primary)]' : 'text-[var(--text-muted)]'
                  }`}
                />
                <span className="flex-1 text-left">{SORT_FIELD_LABELS[f]}</span>
                {selected && <Check className="h-4 w-4 text-[var(--primary)]" />}
              </button>
            );
          })}

          <div className="grid grid-cols-2 gap-2 pt-1">
            {Object.values(SortDirection).map((dir) => {
              const selected = direction === dir;
              const DirIcon = dir === SortDirection.ASC ? ArrowUp : ArrowDown;
              return (
                <button
                  key={dir}
                  type="button"
                  onClick={() => setDirection(dir)}
                  className={`flex items-center justify-center gap-1 rounded-xl px-2 py-2 text-xs font-medium transition-colors ${
                    selected
                      ? 'bg-[var(--primary)] text-[var(--on-primary)]'
                      : 'bg-[var(--bg-surface-variant)] text-[var(--text-muted)] hover:text-[var(--text-primary)]'
                  }`}
                >
                  <DirIcon className="h-3.5 w-3.5 shrink-0" />
                  <span className="truncate">
                    {dir} ({hint(dir)})
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        <div className="mt-5 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onDismiss}
            className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)]"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => {
              onApply(field, direction);
              onDismiss();
            }}
            className="rounded-xl bg-[var(--primary)] px-4 py-2 text-xs font-semibold text-[var(--on-primary)] hover:opacity-90"
          >
            Apply
          </button>
        </div>
      </div>
    </div>
  );
}

export function VideoPropertiesDialog({
  video,
  history,
  onClearResume,
  onDismiss,
}: {
  video: VideoItem;
  history?: PlaybackHistoryEntry;
  onClearResume: (uri: string) => void;
  onDismiss: () => void;
}) {
  const folderDisplay = video.relativePath.replace(/\/+$/, '') || video.folderName;
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-[2px]"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-sm rounded-3xl border border-[var(--outline)]/40 bg-[var(--bg-surface)] p-5 text-[var(--text-primary)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Video Properties"
      >
        <h2 className="mb-3 text-base font-bold">Video Properties</h2>
        <div className="space-y-2 text-xs">
          <PropRow label="File Name" value={video.displayName} />
          <PropRow label="Folder" value={folderDisplay} />
          <PropRow label="Duration" value={formatDuration(video.durationMs)} mono />
          <PropRow label="Size" value={formatFileSize(video.sizeBytes)} mono />
          <PropRow label="Resolution" value={formatResolution(video.width, video.height)} mono />
          <PropRow label="Format" value={video.mimeType} />
        </div>
        {history && history.positionMs > 0 && (
          <button
            type="button"
            onClick={() => {
              onClearResume(video.uriString);
              onDismiss();
            }}
            className="mt-4 w-full rounded-xl border border-[var(--outline)] px-3 py-2 text-xs font-semibold text-[var(--primary)] hover:bg-[var(--bg-surface-variant)]"
          >
            Reset Resume ({formatDuration(history.positionMs)})
          </button>
        )}
        <div className="mt-4 flex justify-end">
          <button
            type="button"
            onClick={onDismiss}
            className="rounded-xl px-4 py-2 text-xs font-semibold text-[var(--primary)] hover:bg-[var(--bg-surface-variant)]"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

function PropRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="shrink-0 text-[var(--text-muted)]">{label}</span>
      <span className={`truncate text-right font-medium ${mono ? 'font-mono-label' : ''}`}>{value}</span>
    </div>
  );
}

export function RenameVideoDialog({
  video,
  onRename,
  onDismiss,
}: {
  video: VideoItem;
  onRename: (video: VideoItem, newTitle: string) => void;
  onDismiss: () => void;
}) {
  const [text, setText] = useState(video.title);
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-[2px]"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-sm rounded-3xl border border-[var(--outline)]/40 bg-[var(--bg-surface)] p-5 text-[var(--text-primary)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Rename Video"
      >
        <h2 className="mb-3 text-base font-bold">Rename Video</h2>
        <label className="block text-xs text-[var(--text-muted)]">
          New name
          <input
            type="text"
            value={text}
            onChange={(e) => setText(e.target.value)}
            autoFocus
            className="mt-1.5 w-full rounded-xl border border-[var(--outline)] bg-[var(--bg-canvas)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--primary)]"
          />
        </label>
        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onDismiss}
            className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)]"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={!text.trim()}
            onClick={() => {
              onRename(video, text);
              onDismiss();
            }}
            className="rounded-xl bg-[var(--primary)] px-4 py-2 text-xs font-semibold text-[var(--on-primary)] disabled:opacity-40"
          >
            Rename
          </button>
        </div>
      </div>
    </div>
  );
}

export function AddToPlaylistDialog({
  video,
  existingPlaylists,
  onAdd,
  onDismiss,
}: {
  video: VideoItem;
  existingPlaylists: string[];
  onAdd: (name: string, video: VideoItem) => void;
  onDismiss: () => void;
}) {
  const [newName, setNewName] = useState('');
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-[2px]"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-sm rounded-3xl border border-[var(--outline)]/40 bg-[var(--bg-surface)] p-5 text-[var(--text-primary)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Add to Playlist"
      >
        <h2 className="mb-3 text-base font-bold">Add to Playlist</h2>
        <div className="space-y-2">
          {existingPlaylists.map((pl) => (
            <button
              key={pl}
              type="button"
              onClick={() => {
                onAdd(pl, video);
                onDismiss();
              }}
              className="flex w-full items-center gap-2.5 rounded-xl bg-[var(--bg-surface-variant)] px-3 py-2.5 text-left text-sm hover:opacity-90"
            >
              <ListVideo className="h-4 w-4 text-[var(--primary)]" />
              <span className="truncate">{pl}</span>
            </button>
          ))}
          <label className="block pt-1 text-xs text-[var(--text-muted)]">
            New playlist name
            <input
              type="text"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              placeholder="e.g. Watch Later"
              className="mt-1.5 w-full rounded-xl border border-[var(--outline)] bg-[var(--bg-canvas)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--primary)]"
            />
          </label>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onDismiss}
            className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)]"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={!newName.trim()}
            onClick={() => {
              onAdd(newName, video);
              onDismiss();
            }}
            className="flex items-center gap-1 rounded-xl bg-[var(--primary)] px-4 py-2 text-xs font-semibold text-[var(--on-primary)] disabled:opacity-40"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>Create & Add</span>
          </button>
        </div>
      </div>
    </div>
  );
}

export function MoveVideosDialog({
  videos,
  folders,
  onMove,
  onDismiss,
}: {
  videos: VideoItem[];
  folders: VideoFolder[];
  onMove: (videos: VideoItem[], targetFolder: string) => void;
  onDismiss: () => void;
}) {
  const [targetFolder, setTargetFolder] = useState('Movies/Moved');
  const suggestions = Array.from(new Set(folders.map((f) => f.pathHint).filter(Boolean))).slice(0, 5);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-[2px]"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-sm rounded-3xl border border-[var(--outline)]/40 bg-[var(--bg-surface)] p-5 text-[var(--text-primary)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Move videos"
      >
        <h2 className="mb-3 text-base font-bold">
          Move {videos.length} video(s) to folder
        </h2>
        <div className="space-y-2.5">
          <label className="block text-xs text-[var(--text-muted)]">
            Target folder (e.g. Movies/Clips)
            <input
              type="text"
              value={targetFolder}
              onChange={(e) => setTargetFolder(e.target.value)}
              className="mt-1.5 w-full rounded-xl border border-[var(--outline)] bg-[var(--bg-canvas)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--primary)]"
            />
          </label>
          {suggestions.length > 0 && (
            <div className="space-y-1.5">
              <p className="text-xs text-[var(--text-muted)]">Or tap an existing folder:</p>
              {suggestions.map((path) => (
                <button
                  key={path}
                  type="button"
                  onClick={() => {
                    onMove(videos, path);
                    onDismiss();
                  }}
                  className="flex w-full items-center gap-2 rounded-xl bg-[var(--bg-surface-variant)] px-3 py-2 text-left text-xs hover:opacity-90"
                >
                  <Folder className="h-4 w-4 shrink-0 text-[var(--primary)]" />
                  <span className="truncate">{path}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onDismiss}
            className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)]"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={!targetFolder.trim()}
            onClick={() => {
              onMove(videos, targetFolder);
              onDismiss();
            }}
            className="rounded-xl bg-[var(--primary)] px-4 py-2 text-xs font-semibold text-[var(--on-primary)] disabled:opacity-40"
          >
            Move
          </button>
        </div>
      </div>
    </div>
  );
}

export function DeleteConfirmDialog({
  videos,
  onConfirm,
  onDismiss,
}: {
  videos: VideoItem[];
  onConfirm: (videos: VideoItem[]) => void;
  onDismiss: () => void;
}) {
  const title =
    videos.length === 1
      ? 'Are you sure to delete file'
      : `Are you sure to delete ${videos.length} files`;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-[2px]"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-sm rounded-3xl border border-[var(--outline)]/40 bg-[var(--bg-surface)] p-5 text-[var(--text-primary)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Delete confirmation"
      >
        <h2 className="text-base font-bold">{title}</h2>
        <p className="mt-2 text-sm text-[var(--text-muted)]">
          This file will be permanently removed from storage.
        </p>
        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onDismiss}
            className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)]"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => {
              onConfirm(videos);
              onDismiss();
            }}
            className="rounded-xl bg-[var(--error)] px-4 py-2 text-xs font-semibold text-white hover:opacity-90"
          >
            Delete
          </button>
        </div>
      </div>
    </div>
  );
}

export function ExitConfirmDialog({
  onConfirm,
  onDismiss,
}: {
  onConfirm: () => void;
  onDismiss: () => void;
}) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-[2px]"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-sm rounded-3xl border border-[var(--outline)]/40 bg-[var(--bg-surface)] p-5 text-[var(--text-primary)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Exit confirmation"
      >
        <div className="flex flex-col items-center text-center">
          <img
            src="/icon.png"
            alt="AG Video Player"
            className="h-14 w-14 object-contain drop-shadow-md mb-2 select-none"
          />
          <h2 className="text-base font-bold">Exit AG Video Player</h2>
        </div>
        <p className="mt-2 text-center text-sm text-[var(--text-muted)]">
          Are you sure you want to exit?
        </p>
        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onDismiss}
            className="rounded-xl px-4 py-2 text-xs font-semibold text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)]"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="rounded-xl bg-[var(--error)] px-4 py-2 text-xs font-semibold text-white hover:opacity-90"
          >
            Exit
          </button>
        </div>
      </div>
    </div>
  );
}

