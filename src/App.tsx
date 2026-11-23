import { useRef, useState } from 'react';
import { Headphones, Pause, Play, X } from 'lucide-react';
import type { PlaylistEntry, VideoItem } from './types/media';
import { useLibraryState } from './hooks/useLibraryState';
import { LibraryBrowserScreen } from './components/LibraryBrowserScreen';
import { PlayerScreen } from './components/PlayerScreen';
import { resolvePlayableUrl } from './repositories/videoLibraryRepository';

interface ActivePlayerSession {
  playlist: PlaylistEntry[];
  startIndex: number;
  initialPositionMs?: number;
}

interface BackgroundAudioSession {
  playlist: PlaylistEntry[];
  currentIndex: number;
  uriString: string;
  title: string;
  playbackRate: number;
  volumePercent: number;
  isPlaying: boolean;
}

export default function App() {
  const lib = useLibraryState();
  const filePickerRef = useRef<HTMLInputElement>(null);
  const bgAudioRef = useRef<HTMLAudioElement>(null);

  const [playerSession, setPlayerSession] = useState<ActivePlayerSession | null>(null);
  const [bgSession, setBgSession] = useState<BackgroundAudioSession | null>(null);
  const [actionMessageOverride, setActionMessageOverride] = useState<string | null>(null);

  const openPlayer = (video: VideoItem, list: VideoItem[], initialPositionMs?: number) => {
    // Stop background audio if running
    if (bgAudioRef.current) {
      bgAudioRef.current.pause();
    }
    setBgSession(null);

    const { entries, startIndex } = lib.buildPlaylistFor(video, list);
    setPlayerSession({
      playlist: entries,
      startIndex,
      initialPositionMs,
    });
  };

  const handleFilesSelected = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const added = await lib.importVideos(files);
    if (added.length === 1 && lib.snapshot.allVideos.length === 0) {
      openPlayer(added[0], added);
    }
  };

  const handlePlayerBack = (
    bgAudioInfo?: {
      uriString: string;
      title: string;
      currentTimeSec: number;
      playbackRate: number;
      volumePercent: number;
    }
  ) => {
    const activePlaylist = playerSession?.playlist ?? [];
    setPlayerSession(null);

    if (bgAudioInfo) {
      const idx = Math.max(
        0,
        activePlaylist.findIndex((p) => p.uriString === bgAudioInfo.uriString)
      );
      setBgSession({
        playlist: activePlaylist,
        currentIndex: idx,
        uriString: bgAudioInfo.uriString,
        title: bgAudioInfo.title,
        playbackRate: bgAudioInfo.playbackRate,
        volumePercent: bgAudioInfo.volumePercent,
        isPlaying: true,
      });

      window.setTimeout(() => {
        const audio = bgAudioRef.current;
        if (!audio) return;
        audio.src = resolvePlayableUrl(bgAudioInfo.uriString);
        audio.currentTime = bgAudioInfo.currentTimeSec;
        audio.playbackRate = bgAudioInfo.playbackRate;
        audio.volume = Math.min(1, bgAudioInfo.volumePercent / 100);
        void audio.play().catch(() => {});
      }, 20);
    }
  };

  const handleExpandBgPlayer = () => {
    if (!bgSession) return;
    const currentTimeMs = bgAudioRef.current ? Math.round(bgAudioRef.current.currentTime * 1000) : 0;
    bgAudioRef.current?.pause();
    const sessionToOpen: ActivePlayerSession = {
      playlist: bgSession.playlist,
      startIndex: bgSession.currentIndex,
      initialPositionMs: currentTimeMs,
    };
    setBgSession(null);
    setPlayerSession(sessionToOpen);
  };

  const handleStopBgPlayer = () => {
    bgAudioRef.current?.pause();
    setBgSession(null);
  };

  return (
    <>
      <input
        ref={filePickerRef}
        type="file"
        accept="video/*"
        multiple
        className="hidden"
        onChange={(e) => {
          void handleFilesSelected(e.target.files);
          e.target.value = '';
        }}
      />

      <audio
        ref={bgAudioRef}
        className="hidden"
        onPlay={() => setBgSession((s) => (s ? { ...s, isPlaying: true } : null))}
        onPause={() => setBgSession((s) => (s ? { ...s, isPlaying: false } : null))}
        onEnded={() => setBgSession((s) => (s ? { ...s, isPlaying: false } : null))}
      />

      <LibraryBrowserScreen
        isLoadingLibrary={lib.isLoadingLibrary}
        folders={lib.snapshot.folders}
        allVideos={lib.snapshot.allVideos}
        videosByFolder={lib.snapshot.videosByFolder}
        filteredFolders={lib.filteredFolders}
        currentVideos={lib.currentVideos}
        sortedAllVideos={lib.sortedAllVideos}
        destination={lib.destination}
        searchQuery={lib.searchQuery}
        sortField={lib.sortField}
        sortDirection={lib.sortDirection}
        selectedVideoUris={lib.selectedVideoUris}
        historyByUri={lib.historyByUri}
        lastHistoryEntry={lib.lastHistoryEntry}
        preferences={lib.preferences}
        updateState={lib.updateState}
        showUpdateDialog={lib.showUpdateDialog}
        actionMessage={actionMessageOverride ?? lib.actionMessage}
        currentAppVersionLabel={lib.currentAppVersionLabel}
        onRefresh={() => void lib.refreshLibrary()}
        onOpenDocumentPicker={() => filePickerRef.current?.click()}
        onDropFiles={(files) => void handleFilesSelected(files)}
        onToggleTheme={lib.toggleDarkMode}
        onToggleGridView={lib.toggleGridView}
        onApplySort={lib.setSortOption}
        onOpenUpdateCheck={() => void lib.openUpdateCheck()}
        onDismissUpdateDialog={lib.dismissUpdateDialog}
        onSearchQueryChange={lib.setSearchQuery}
        onFolderClick={lib.openFolder}
        onOpenFavorites={lib.openFavorites}
        onOpenPlaylist={lib.openPlaylist}
        onDeletePlaylist={lib.deletePlaylist}
        onBackToFolders={lib.navigateBackToFolders}
        onVideoClick={(video, list) => openPlayer(video, list)}
        onResumeLastPlayed={() => {
          const last = lib.getLastPlayedVideoAndPlaylist();
          if (last) openPlayer(last.video, last.list);
        }}
        onStartSelection={lib.startSelection}
        onToggleSelection={lib.toggleSelection}
        onSelectAll={lib.selectAllVisible}
        onClearSelection={lib.clearSelection}
        onToggleFavorite={lib.toggleFavorite}
        onAddToPlaylist={lib.addToPlaylist}
        onRemoveFromPlaylist={lib.removeFromPlaylist}
        onRenameVideo={(v, name) => void lib.renameVideo(v, name)}
        onMoveVideos={(vs, path) => void lib.moveVideos(vs, path)}
        onDeleteVideos={(vs) => void lib.deleteVideos(vs)}
        onClearResume={lib.clearResumePosition}
        onClearActionMessage={() => {
          setActionMessageOverride(null);
          lib.clearActionMessage();
        }}
        onShowActionMessage={(msg) => setActionMessageOverride(msg)}
        onSingleClickActionChange={lib.setSingleClickAction}
        onDoubleClickActionChange={lib.setDoubleClickAction}
        onAlwaysBackgroundPlayChange={lib.setAlwaysBackgroundPlay}
        onAutoPipChange={lib.setAutoPipOnHome}
        onEnableSwipeGesturesChange={lib.setEnableSwipeGestures}
        onGestureSeekStepChange={lib.setGestureSeekStepSeconds}
        onDefaultSoftwareDecoderChange={lib.setDefaultSoftwareDecoder}
        onShowHiddenFilesChange={lib.setShowHiddenFiles}
        onMainDisplayModeChange={lib.setMainDisplayMode}
        onMainViewLayoutChange={lib.setMainViewLayout}
        onOneFingerGestureChange={lib.setOneFingerGesture}
        onTwoFingerGestureChange={lib.setTwoFingerGesture}
        onThreeFingerGestureChange={lib.setThreeFingerGesture}
        onTogglePlayerButtonVisibility={lib.setPlayerButtonVisible}
        onShowAllButtons={lib.showAllPlayerButtons}
        onProgressBarStyleChange={lib.setProgressBarStyle}
      />

      {/* Background Play Mini Bar */}
      {bgSession && !playerSession && (
        <div className="fixed bottom-5 left-4 z-30 flex max-w-xs items-center gap-2.5 rounded-2xl border border-[var(--outline)]/50 bg-[var(--bg-surface)] px-3 py-2 shadow-xl md:left-6">
          <button
            type="button"
            onClick={handleExpandBgPlayer}
            className="flex min-w-0 flex-1 items-center gap-2 text-left"
          >
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[var(--primary)]/15 text-[var(--primary)]">
              <Headphones className="h-4 w-4" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-xs font-semibold">{bgSession.title}</div>
              <div className="text-[10px] text-[var(--text-muted)]">Background audio</div>
            </div>
          </button>
          <button
            type="button"
            onClick={() => {
              const audio = bgAudioRef.current;
              if (!audio) return;
              if (audio.paused) void audio.play().catch(() => {});
              else audio.pause();
            }}
            aria-label={bgSession.isPlaying ? 'Pause background audio' : 'Play background audio'}
            className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--primary)] text-[var(--on-primary)]"
          >
            {bgSession.isPlaying ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
          </button>
          <button
            type="button"
            onClick={handleStopBgPlayer}
            aria-label="Stop background audio"
            className="flex h-7 w-7 items-center justify-center rounded-full text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)]"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Fullscreen Video Player */}
      {playerSession && (
        <PlayerScreen
          playlist={playerSession.playlist}
          initialIndex={playerSession.startIndex}
          initialPositionMs={playerSession.initialPositionMs}
          preferences={lib.preferences}
          historyByUri={lib.historyByUri}
          onBack={handlePlayerBack}
          onMarkPlayed={lib.markVideoPlayed}
          onSavePlaybackState={lib.savePlaybackState}
          onSetDefaultSoftwareDecoder={lib.setDefaultSoftwareDecoder}
          onSetAlwaysBackgroundPlay={lib.setAlwaysBackgroundPlay}
        />
      )}
    </>
  );
}
