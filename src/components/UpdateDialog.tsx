import { RefreshCw } from 'lucide-react';
import type { GitHubReleaseInfo, UpdateUiState } from '../types/media';
import { formatFileSize } from '../utils/format';

interface UpdateDialogProps {
  currentVersionLabel: string;
  state: UpdateUiState;
  onDismiss: () => void;
  onRetryCheck: () => void;
  onStartDownload: (release: GitHubReleaseInfo) => void;
  onCancelDownload: () => void;
}

export function UpdateDialog({
  currentVersionLabel,
  state,
  onDismiss,
  onRetryCheck,
  onStartDownload,
  onCancelDownload,
}: UpdateDialogProps) {
  const title =
    state.status === 'available'
      ? 'Update Available Now'
      : state.status === 'downloading'
        ? 'Downloading Update'
        : state.status === 'up_to_date'
          ? 'Up to Date'
          : state.status === 'error'
            ? 'Update Check'
            : 'Checking for Update';

  const cleanCurrent = currentVersionLabel.split(' ')[0] || currentVersionLabel;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-[2px]"
      onClick={state.status === 'downloading' ? undefined : onDismiss}
    >
      <div
        className="w-full max-w-sm rounded-3xl border border-[var(--outline)]/40 bg-[var(--bg-surface)] p-6 text-[var(--text-primary)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <h2 className="text-base font-bold">{title}</h2>

        <div className="mt-4">
          {(state.status === 'idle' || state.status === 'checking') && (
            <div className="flex items-center gap-3 py-1 text-sm text-[var(--text-muted)]">
              <RefreshCw className="h-4 w-4 animate-spin text-[var(--primary)]" />
              <span>Checking for latest version...</span>
            </div>
          )}

          {state.status === 'up_to_date' && (
            <p className="text-sm text-[var(--text-primary)]">
              You are using the latest version (v{state.versionName.replace(/^v/i, '')}).
            </p>
          )}

          {state.status === 'error' && (
            <p className="text-sm text-[var(--text-primary)]">{state.message}</p>
          )}

          {state.status === 'available' && (
            <div className="space-y-2 text-sm">
              <p>Current Version: {cleanCurrent}</p>
              <p className="font-semibold text-[var(--primary)]">
                New Version: v{state.release.versionName.replace(/^v/i, '')}
              </p>
            </div>
          )}

          {state.status === 'downloading' && (
            <div className="space-y-2.5">
              <div className="h-2 w-full overflow-hidden rounded-full bg-[var(--bg-surface-variant)]">
                <div
                  className="h-full rounded-full bg-[var(--primary)] transition-all duration-150"
                  style={{
                    width: `${
                      state.bytesTotal > 0
                        ? Math.min(100, Math.round((state.bytesDownloaded / state.bytesTotal) * 100))
                        : state.percent
                    }%`,
                  }}
                />
              </div>
              <div className="flex items-center justify-between text-xs tabular-nums">
                <span className="text-[var(--text-muted)]">
                  {formatFileSize(state.bytesDownloaded)} /{' '}
                  {state.bytesTotal > 0 ? formatFileSize(state.bytesTotal) : '...'}
                </span>
                <span className="font-bold text-[var(--primary)]">{state.percent}%</span>
              </div>
            </div>
          )}
        </div>

        <div className="mt-5 flex items-center justify-end gap-2">
          {state.status === 'available' && (
            <>
              <button
                type="button"
                onClick={onDismiss}
                className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)] hover:text-[var(--text-primary)]"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => onStartDownload(state.release)}
                className="rounded-xl bg-[var(--primary)] px-4 py-2 text-xs font-semibold text-[var(--on-primary)] transition-opacity hover:opacity-90"
              >
                Download
              </button>
            </>
          )}

          {state.status === 'downloading' && (
            <button
              type="button"
              onClick={onCancelDownload}
              className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)] hover:text-[var(--text-primary)]"
            >
              Cancel
            </button>
          )}

          {state.status === 'error' && (
            <>
              <button
                type="button"
                onClick={onDismiss}
                className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)] hover:text-[var(--text-primary)]"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={onRetryCheck}
                className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--primary)] hover:bg-[var(--bg-surface-variant)]"
              >
                Retry
              </button>
            </>
          )}

          {state.status === 'up_to_date' && (
            <button
              type="button"
              onClick={onDismiss}
              className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--primary)] hover:bg-[var(--bg-surface-variant)]"
            >
              OK
            </button>
          )}

          {(state.status === 'idle' || state.status === 'checking') && (
            <button
              type="button"
              onClick={onDismiss}
              className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)] hover:text-[var(--text-primary)]"
            >
              Cancel
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
