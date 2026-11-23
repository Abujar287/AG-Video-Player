import { AlertCircle, CheckCircle2, CloudDownload, RefreshCw } from 'lucide-react';
import type { UpdateUiState } from '../types/media';
import { formatFileSize } from '../utils/format';

interface UpdateDialogProps {
  currentVersionLabel: string;
  state: UpdateUiState;
  onDismiss: () => void;
  onRetryCheck: () => void;
}

export function UpdateDialog({
  currentVersionLabel,
  state,
  onDismiss,
  onRetryCheck,
}: UpdateDialogProps) {
  const title =
    state.status === 'available'
      ? 'New Update Available'
      : state.status === 'up_to_date'
        ? 'Up to Date'
        : state.status === 'error'
          ? 'Update Check'
          : 'Checking for Update…';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-[2px]"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-sm rounded-3xl border border-[var(--outline)]/40 bg-[var(--bg-surface)] p-6 text-[var(--text-primary)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="flex flex-col items-center text-center">
          <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-2xl bg-[var(--primary)]/15 text-[var(--primary)]">
            <RefreshCw className={`h-5 w-5 ${state.status === 'checking' ? 'animate-spin' : ''}`} />
          </div>
          <h2 className="text-base font-bold">{title}</h2>
        </div>

        <div className="mt-4">
          {(state.status === 'idle' || state.status === 'checking') && (
            <div className="flex items-center justify-center gap-3 py-3 text-sm text-[var(--text-muted)]">
              <span>Checking for latest version…</span>
            </div>
          )}

          {state.status === 'up_to_date' && (
            <div className="flex items-center gap-2.5 rounded-xl bg-[var(--bg-surface-variant)]/60 p-3 text-sm">
              <CheckCircle2 className="h-5 w-5 shrink-0 text-[var(--primary)]" />
              <span>You are using the latest version (v{state.versionName}).</span>
            </div>
          )}

          {state.status === 'error' && (
            <div className="flex items-start gap-2.5 rounded-xl bg-[var(--error)]/10 p-3 text-sm">
              <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-[var(--error)]" />
              <span>{state.message}</span>
            </div>
          )}

          {state.status === 'available' && (
            <div className="space-y-3">
              <div className="space-y-1.5 rounded-xl bg-[var(--bg-surface-variant)] p-3 text-xs">
                <div className="flex items-center justify-between">
                  <span className="text-[var(--text-muted)]">Current Version</span>
                  <span className="font-semibold tabular-nums">{currentVersionLabel}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[var(--text-muted)]">New Version</span>
                  <span className="font-bold text-[var(--primary)] tabular-nums">
                    v{state.release.versionName}
                  </span>
                </div>
                {state.release.apkSizeBytes > 0 && (
                  <div className="flex items-center justify-between">
                    <span className="text-[var(--text-muted)]">APK Size</span>
                    <span className="text-[var(--text-muted)] tabular-nums">
                      {formatFileSize(state.release.apkSizeBytes)}
                    </span>
                  </div>
                )}
              </div>

              <a
                href={state.release.apkDownloadUrl}
                target="_blank"
                rel="noopener noreferrer"
                onClick={onDismiss}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-[var(--primary)] px-4 py-2.5 text-sm font-semibold text-[var(--on-primary)] transition-opacity hover:opacity-90"
              >
                <CloudDownload className="h-4 w-4" />
                <span>Download AG Video Player.apk</span>
              </a>
            </div>
          )}
        </div>

        <div className="mt-5 flex items-center justify-end gap-2">
          {state.status === 'error' && (
            <button
              type="button"
              onClick={onRetryCheck}
              className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--primary)] hover:bg-[var(--bg-surface-variant)]"
            >
              Retry
            </button>
          )}
          <button
            type="button"
            onClick={onDismiss}
            className="rounded-xl px-3.5 py-2 text-xs font-semibold text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)] hover:text-[var(--text-primary)]"
          >
            {state.status === 'available' ? 'Cancel' : 'Close'}
          </button>
        </div>
      </div>
    </div>
  );
}
