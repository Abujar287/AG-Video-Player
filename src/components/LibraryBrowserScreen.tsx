import { useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  ArrowUpDown,
  CheckCircle2,
  Circle,
  Edit3,
  Folder,
  FolderInput,
  FolderOpen,
  Heart,
  Info,
  LayoutGrid,
  List,
  ListPlus,
  ListVideo,
  Moon,
  MoreVertical,
  Play,
  PlusSquare,
  Search,
  Share2,
  Sun,
  Trash2,
  X,
} from 'lucide-react';
import {
  type BrowserDestination,
  DoubleClickAction,
  type PlaybackHistoryEntry,
  type PlayerControlButton,
  type ProgressBarStyle,
  SingleClickAction,
  type SortDirection,
  type SortField,
  type UpdateUiState,
  type UserPreferences,
  type VideoFolder,
  type VideoItem,
} from '../types/media';
import { formatDuration, formatFileSize, formatResolution } from '../utils/format';
import { VideoThumbnail } from './VideoThumbnail';
import { ControlSettingsDialog } from './ControlSettingsDialog';
import { UpdateDialog } from './UpdateDialog';
import {
  AddToPlaylistDialog,
  DeleteConfirmDialog,
  MoveVideosDialog,
  RenameVideoDialog,
  SortOptionsDialog,
  VideoPropertiesDialog,
} from './LibraryDialogs';

const CloseIcon = X;
const MoveIcon = FolderInput;
const SelectAllIcon = PlusSquare;

interface LibraryBrowserScreenProps {
  isLoadingLibrary: boolean;
  folders: VideoFolder[];
  allVideos: VideoItem[];
  videosByFolder: Record<string, VideoItem[]>;
  filteredFolders: VideoFolder[];
  currentVideos: VideoItem[];
  sortedAllVideos: VideoItem[];
  destination: BrowserDestination;
  searchQuery: string;
  sortField: SortField;
  sortDirection: SortDirection;
  selectedVideoUris: Set<string>;
  historyByUri: Record<string, PlaybackHistoryEntry>;
  lastHistoryEntry: PlaybackHistoryEntry | null;
  preferences: UserPreferences;
  updateState: UpdateUiState;
  showUpdateDialog: boolean;
  actionMessage: string | null;
  currentAppVersionLabel: string;
  onRefresh: () => void;
  onOpenDocumentPicker: () => void;
  onDropFiles: (files: FileList) => void;
  onToggleTheme: () => void;
  onToggleGridView: () => void;
  onApplySort: (field: SortField, dir: SortDirection) => void;
  onOpenUpdateCheck: () => void;
  onStartDownload: (release: import('../types/media').GitHubReleaseInfo) => void;
  onCancelDownload: () => void;
  onDismissUpdateDialog: () => void;
  onSearchQueryChange: (q: string) => void;
  onFolderClick: (bucketId: string) => void;
  onOpenFavorites: () => void;
  onOpenPlaylist: (name: string) => void;
  onDeletePlaylist: (name: string) => void;
  onBackToFolders: () => void;
  onVideoClick: (video: VideoItem, list: VideoItem[]) => void;
  onResumeLastPlayed: () => void;
  onStartSelection: (uri: string) => void;
  onToggleSelection: (uri: string) => void;
  onSelectAll: () => void;
  onClearSelection: () => void;
  onToggleFavorite: (video: VideoItem) => void;
  onAddToPlaylist: (name: string, video: VideoItem) => void;
  onRemoveFromPlaylist: (name: string, video: VideoItem) => void;
  onRenameVideo: (video: VideoItem, newTitle: string) => void;
  onMoveVideos: (videos: VideoItem[], targetPath: string) => void;
  onDeleteVideos: (videos: VideoItem[]) => void;
  onClearResume: (uri: string) => void;
  onClearActionMessage: () => void;
  onShowActionMessage: (msg: string) => void;
  onSingleClickActionChange: (a: SingleClickAction) => void;
  onDoubleClickActionChange: (a: DoubleClickAction) => void;
  onAlwaysBackgroundPlayChange: (v: boolean) => void;
  onAutoPipChange: (v: boolean) => void;
  onEnableSwipeGesturesChange: (v: boolean) => void;
  onGestureSeekStepChange: (s: number) => void;
  onDefaultSoftwareDecoderChange: (v: boolean) => void;
  onShowHiddenFilesChange: (v: boolean) => void;
  onMainDisplayModeChange: (m: 'folders' | 'files') => void;
  onMainViewLayoutChange: (l: 'list' | 'grid') => void;
  onOneFingerGestureChange: (g: string) => void;
  onTwoFingerGestureChange: (g: string) => void;
  onThreeFingerGestureChange: (g: string) => void;
  onTogglePlayerButtonVisibility: (b: PlayerControlButton, v: boolean) => void;
  onShowAllButtons: () => void;
  onProgressBarStyleChange: (style: ProgressBarStyle) => void;
}

export function LibraryBrowserScreen(props: LibraryBrowserScreenProps) {
  const {
    isLoadingLibrary,
    folders,
    allVideos,
    videosByFolder,
    filteredFolders,
    currentVideos,
    sortedAllVideos,
    destination,
    searchQuery,
    sortField,
    sortDirection,
    selectedVideoUris,
    historyByUri,
    lastHistoryEntry,
    preferences,
    updateState,
    showUpdateDialog,
    actionMessage,
    currentAppVersionLabel,
  } = props;

  const [showSortDialog, setShowSortDialog] = useState(false);
  const [showControlSettings, setShowControlSettings] = useState(false);
  const [detailVideo, setDetailVideo] = useState<VideoItem | null>(null);
  const [renameTargetVideo, setRenameTargetVideo] = useState<VideoItem | null>(null);
  const [playlistTargetVideo, setPlaylistTargetVideo] = useState<VideoItem | null>(null);
  const [moveTargetVideos, setMoveTargetVideos] = useState<VideoItem[] | null>(null);
  const [deleteTargetVideos, setDeleteTargetVideos] = useState<VideoItem[] | null>(null);
  const [isDraggingFiles, setIsDraggingFiles] = useState(false);

  const isSelectionMode = selectedVideoUris.size > 0;
  const isInVideoList = destination.type !== 'folders';
  const isGridLayoutActive =
    preferences.mainViewLayout === 'list'
      ? false
      : preferences.mainViewLayout === 'grid'
        ? true
        : preferences.isGridView;
  const hasAvailableUpdate = updateState.status === 'available';

  useEffect(() => {
    if (!actionMessage) return;
    const t = window.setTimeout(() => {
      props.onClearActionMessage();
    }, 2800);
    return () => window.clearTimeout(t);
  }, [actionMessage, props]);

  const isSearching = searchQuery.trim().length > 0;

  const displayedVideos =
    destination.type === 'folders' && (preferences.mainDisplayMode === 'files' || isSearching)
      ? sortedAllVideos
      : currentVideos;

  const selectedFolder =
    destination.type === 'folder'
      ? folders.find((f) => f.bucketId === destination.bucketId) ?? null
      : null;

  const activePlaylistName = destination.type === 'playlist' ? destination.name : null;

  const titleText =
    destination.type === 'folders'
      ? 'AG Video Player'
      : destination.type === 'folder'
        ? selectedFolder?.name ?? 'Folder'
        : destination.type === 'favorites'
          ? 'Favourites'
          : destination.name;

  const subtitleText = selectedFolder
    ? `${selectedFolder.videoCount} ${selectedFolder.videoCount === 1 ? 'video' : 'videos'} · ${formatFileSize(selectedFolder.totalSizeBytes)}`
    : destination.type === 'favorites' || destination.type === 'playlist'
      ? `${displayedVideos.length} ${displayedVideos.length === 1 ? 'video' : 'videos'}`
      : isSearching
        ? `${displayedVideos.length} matching ${displayedVideos.length === 1 ? 'video' : 'videos'}`
        : folders.length > 0
          ? `${folders.length} folders · ${allVideos.length} videos`
          : '';

  const availableUriSet = new Set(allVideos.map((v) => v.uriString));
  const favoriteVideosCount = preferences.favoriteUris.filter((u) => availableUriSet.has(u)).length;
  const playedSet = new Set(preferences.playedVideoUris);

  const handleShareVideo = async (video: VideoItem) => {
    if (navigator.share) {
      try {
        await navigator.share({
          title: video.title,
          text: `${video.displayName} (${formatFileSize(video.sizeBytes)})`,
        });
        return;
      } catch {
        // User cancelled or unsupported
      }
    }
    try {
      await navigator.clipboard.writeText(video.displayName);
      props.onShowActionMessage(`Copied "${video.displayName}" to clipboard`);
    } catch {
      props.onShowActionMessage(`Ready to share "${video.title}"`);
    }
  };

  return (
    <div
      className="relative flex min-h-screen flex-col bg-[var(--bg-canvas)] text-[var(--text-primary)]"
      onDragOver={(e) => {
        e.preventDefault();
        setIsDraggingFiles(true);
      }}
      onDragLeave={(e) => {
        if (e.currentTarget === e.target) setIsDraggingFiles(false);
      }}
      onDrop={(e) => {
        e.preventDefault();
        setIsDraggingFiles(false);
        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
          props.onDropFiles(e.dataTransfer.files);
        }
      }}
    >
      {/* Top App Bar */}
      {isSelectionMode ? (
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between gap-3 bg-[var(--primary-container)] px-3 text-[var(--on-primary-container)] shadow-sm md:px-6">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={props.onClearSelection}
              aria-label="Cancel selection"
              className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-black/10"
            >
              <CloseIcon className="h-5 w-5" />
            </button>
            <span className="text-base font-semibold tabular-nums">
              {selectedVideoUris.size} selected
            </span>
          </div>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={props.onSelectAll}
              aria-label="Select all"
              className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-black/10"
            >
              <SelectAllIcon className="h-5 w-5" />
            </button>
            <button
              type="button"
              onClick={() => {
                const selected = displayedVideos.filter((v) => selectedVideoUris.has(v.uriString));
                if (selected.length > 0) setMoveTargetVideos(selected);
              }}
              aria-label="Move selected"
              className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-black/10"
            >
              <MoveIcon className="h-5 w-5" />
            </button>
            <button
              type="button"
              onClick={() => {
                const selected = displayedVideos.filter((v) => selectedVideoUris.has(v.uriString));
                if (selected.length > 0) setDeleteTargetVideos(selected);
              }}
              aria-label="Delete selected"
              className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-black/10"
            >
              <Trash2 className="h-5 w-5" />
            </button>
          </div>
        </header>
      ) : (
        <header className="sticky top-0 z-30 border-b border-[var(--outline)]/25 bg-[var(--bg-surface)] px-3 py-2.5 md:px-6">
          <div className="mx-auto flex max-w-6xl items-center justify-between gap-3">
            <div className="flex min-w-0 flex-1 items-center gap-2.5">
              {isInVideoList ? (
                <button
                  type="button"
                  onClick={props.onBackToFolders}
                  aria-label="Back"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-surface-variant)]"
                >
                  <ArrowLeft className="h-5 w-5" />
                </button>
              ) : (
                <img
                  src="/icon.png"
                  alt="AG Video Player"
                  className="h-[34px] w-[34px] shrink-0 rounded-lg object-cover"
                />
              )}
              <div className="min-w-0 flex-1">
                <h1 className="truncate text-[15.5px] font-bold leading-tight">{titleText}</h1>
                {subtitleText && (
                  <p className="font-mono-label truncate text-[10px] text-[var(--text-muted)]">
                    {subtitleText}
                  </p>
                )}
              </div>
            </div>

            <div className="flex shrink-0 items-center gap-1">
              <button
                type="button"
                onClick={props.onToggleTheme}
                aria-label="Toggle Theme"
                title="Toggle Theme"
                className="flex h-9 w-9 items-center justify-center rounded-full text-[var(--primary)] transition-colors hover:bg-[var(--bg-surface-variant)]"
              >
                {preferences.isDarkMode ? (
                  <Sun className="h-5 w-5" />
                ) : (
                  <Moon className="h-5 w-5" />
                )}
              </button>
              <button
                type="button"
                onClick={props.onToggleGridView}
                aria-label="Toggle Grid"
                title={isGridLayoutActive ? 'Switch to List View' : 'Switch to Grid View'}
                className="flex h-9 w-9 items-center justify-center rounded-full text-[var(--primary)] transition-colors hover:bg-[var(--bg-surface-variant)]"
              >
                {isGridLayoutActive ? (
                  <List className="h-5 w-5" />
                ) : (
                  <LayoutGrid className="h-5 w-5" />
                )}
              </button>
              <button
                type="button"
                onClick={() => setShowSortDialog(true)}
                aria-label="Sort"
                title="Sort"
                className="flex h-9 w-9 items-center justify-center rounded-full text-[var(--primary)] transition-colors hover:bg-[var(--bg-surface-variant)]"
              >
                <ArrowUpDown className="h-5 w-5" />
              </button>
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setShowControlSettings(true)}
                  aria-label="Settings"
                  title="Settings"
                  className="flex h-9 w-9 items-center justify-center rounded-full text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-surface-variant)]"
                >
                  <MoreVertical className="h-5 w-5" />
                </button>
                {hasAvailableUpdate && (
                  <span className="pointer-events-none absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-[var(--secondary)]" />
                )}
              </div>
            </div>
          </div>
        </header>
      )}

      {/* Main Content */}
      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col px-3 pt-2.5 pb-24 md:px-6">
        {/* Search Bar */}
        <div className="relative mb-2.5">
          <Search className="pointer-events-none absolute top-1/2 left-3.5 h-4 w-4 -translate-y-1/2 text-[var(--text-muted)]" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => props.onSearchQueryChange(e.target.value)}
            placeholder="Search video files…"
            className="w-full rounded-[14px] border border-[var(--outline)]/55 bg-[var(--bg-surface)] py-2.5 pr-10 pl-10 text-sm text-[var(--text-primary)] placeholder-[var(--text-muted)] outline-none transition-colors focus:border-[var(--primary)]"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => props.onSearchQueryChange('')}
              aria-label="Clear search"
              className="absolute top-1/2 right-2.5 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)]"
            >
              <CloseIcon className="h-4 w-4" />
            </button>
          )}
        </div>

        {/* Folders View */}
        {destination.type === 'folders' && preferences.mainDisplayMode === 'folders' && !isSearching ? (
          <>
            {!searchQuery.trim() &&
              (favoriteVideosCount > 0 || Object.keys(preferences.playlists).length > 0) && (
                <div className="mb-2.5 flex items-center gap-2 overflow-x-auto pb-1">
                  {favoriteVideosCount > 0 && (
                    <button
                      type="button"
                      onClick={props.onOpenFavorites}
                      className="flex shrink-0 items-center gap-1.5 rounded-[10px] bg-[var(--secondary-container)]/80 px-3 py-2 text-xs font-semibold text-[var(--on-secondary-container)] transition-opacity hover:opacity-90"
                    >
                      <Heart className="h-4 w-4 fill-current text-[var(--secondary)]" />
                      <span>Favourites ({favoriteVideosCount})</span>
                    </button>
                  )}
                  {Object.keys(preferences.playlists)
                    .sort()
                    .map((name) => {
                      const count = preferences.playlists[name]?.length ?? 0;
                      return (
                        <div
                          key={name}
                          className="flex shrink-0 items-center gap-1 rounded-[10px] bg-[var(--bg-surface-variant)] py-1.5 pr-1.5 pl-3 text-xs font-medium"
                        >
                          <button
                            type="button"
                            onClick={() => props.onOpenPlaylist(name)}
                            className="flex items-center gap-1.5 hover:text-[var(--primary)]"
                          >
                            <ListVideo className="h-4 w-4 text-[var(--primary)]" />
                            <span>
                              {name} ({count})
                            </span>
                          </button>
                          <button
                            type="button"
                            onClick={() => props.onDeletePlaylist(name)}
                            aria-label={`Delete playlist ${name}`}
                            className="ml-0.5 flex h-5 w-5 items-center justify-center rounded-full text-[var(--text-muted)] hover:bg-black/10"
                          >
                            <CloseIcon className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      );
                    })}
                </div>
              )}

            {folders.length === 0 && !isLoadingLibrary ? (
              <EmptyLibraryState onOpenDocumentPicker={props.onOpenDocumentPicker} />
            ) : isGridLayoutActive ? (
              <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
                {filteredFolders.map((folder) => {
                  const unplayed = (videosByFolder[folder.bucketId] ?? []).filter(
                    (v) => !playedSet.has(v.uriString)
                  ).length;
                  return (
                    <FolderGridCard
                      key={folder.bucketId}
                      folder={folder}
                      newCount={unplayed}
                      onClick={() => props.onFolderClick(folder.bucketId)}
                    />
                  );
                })}
              </div>
            ) : (
              <div className="flex flex-col gap-2">
                {filteredFolders.map((folder) => {
                  const unplayed = (videosByFolder[folder.bucketId] ?? []).filter(
                    (v) => !playedSet.has(v.uriString)
                  ).length;
                  return (
                    <FolderListCard
                      key={folder.bucketId}
                      folder={folder}
                      newCount={unplayed}
                      onClick={() => props.onFolderClick(folder.bucketId)}
                    />
                  );
                })}
              </div>
            )}
          </>
        ) : displayedVideos.length === 0 ? (
          <EmptyVideosState />
        ) : isGridLayoutActive ? (
          <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 md:grid-cols-4">
            {displayedVideos.map((video) => (
              <VideoCard
                key={video.uriString}
                video={video}
                isGrid
                isSelected={selectedVideoUris.has(video.uriString)}
                isSelectionMode={isSelectionMode}
                isFavorite={preferences.favoriteUris.includes(video.uriString)}
                isNew={!playedSet.has(video.uriString)}
                history={historyByUri[video.uriString]}
                activePlaylistName={activePlaylistName}
                onClick={(shiftKey) => {
                  if (isSelectionMode || shiftKey) {
                    if (isSelectionMode) props.onToggleSelection(video.uriString);
                    else props.onStartSelection(video.uriString);
                  } else {
                    props.onVideoClick(video, displayedVideos);
                  }
                }}
                onLongClick={() => {
                  if (isSelectionMode) props.onToggleSelection(video.uriString);
                  else props.onStartSelection(video.uriString);
                }}
                onToggleFavorite={() => props.onToggleFavorite(video)}
                onAddToPlaylist={() => setPlaylistTargetVideo(video)}
                onRemoveFromPlaylist={(name) => props.onRemoveFromPlaylist(name, video)}
                onShare={() => void handleShareVideo(video)}
                onRename={() => setRenameTargetVideo(video)}
                onMove={() => setMoveTargetVideos([video])}
                onProperties={() => setDetailVideo(video)}
                onDelete={() => setDeleteTargetVideos([video])}
              />
            ))}
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {displayedVideos.map((video) => (
              <VideoCard
                key={video.uriString}
                video={video}
                isGrid={false}
                isSelected={selectedVideoUris.has(video.uriString)}
                isSelectionMode={isSelectionMode}
                isFavorite={preferences.favoriteUris.includes(video.uriString)}
                isNew={!playedSet.has(video.uriString)}
                history={historyByUri[video.uriString]}
                activePlaylistName={activePlaylistName}
                onClick={(shiftKey) => {
                  if (isSelectionMode || shiftKey) {
                    if (isSelectionMode) props.onToggleSelection(video.uriString);
                    else props.onStartSelection(video.uriString);
                  } else {
                    props.onVideoClick(video, displayedVideos);
                  }
                }}
                onLongClick={() => {
                  if (isSelectionMode) props.onToggleSelection(video.uriString);
                  else props.onStartSelection(video.uriString);
                }}
                onToggleFavorite={() => props.onToggleFavorite(video)}
                onAddToPlaylist={() => setPlaylistTargetVideo(video)}
                onRemoveFromPlaylist={(name) => props.onRemoveFromPlaylist(name, video)}
                onShare={() => void handleShareVideo(video)}
                onRename={() => setRenameTargetVideo(video)}
                onMove={() => setMoveTargetVideos([video])}
                onProperties={() => setDetailVideo(video)}
                onDelete={() => setDeleteTargetVideos([video])}
              />
            ))}
          </div>
        )}
      </main>

      {/* Resume Last Played FAB */}
      {!isSelectionMode && (lastHistoryEntry !== null || allVideos.length > 0) && (
        <button
          type="button"
          onClick={props.onResumeLastPlayed}
          aria-label="Resume Last Video"
          title="Resume Last Video"
          className="fixed right-5 bottom-5 z-30 flex h-14 w-14 items-center justify-center rounded-full bg-[var(--primary)] text-[var(--on-primary)] shadow-lg transition-transform hover:scale-105 active:scale-95 md:right-7 md:bottom-7"
        >
          <Play className="h-7 w-7 translate-x-0.5 fill-current" />
        </button>
      )}

      {/* Drag & Drop File Overlay */}
      {isDraggingFiles && (
        <div className="pointer-events-none fixed inset-0 z-40 flex items-center justify-center bg-black/55 backdrop-blur-xs">
          <div className="rounded-3xl border-2 border-dashed border-[var(--primary)] bg-[var(--bg-surface)] px-8 py-6 text-center shadow-2xl">
            <FolderOpen className="mx-auto mb-2 h-10 w-10 text-[var(--primary)]" />
            <p className="text-sm font-semibold">Drop video files to add to library</p>
          </div>
        </div>
      )}

      {/* Snackbar Toast */}
      {actionMessage && (
        <div className="fixed bottom-6 left-1/2 z-40 -translate-x-1/2 rounded-xl bg-[#111827] px-4 py-2.5 text-xs font-medium text-white shadow-xl">
          {actionMessage}
        </div>
      )}

      {/* Dialogs */}
      {showSortDialog && (
        <SortOptionsDialog
          currentField={sortField}
          currentDirection={sortDirection}
          onDismiss={() => setShowSortDialog(false)}
          onApply={props.onApplySort}
        />
      )}

      {showControlSettings && (
        <ControlSettingsDialog
          preferences={preferences}
          currentVersionLabel={currentAppVersionLabel}
          hasAvailableUpdate={hasAvailableUpdate}
          onDismiss={() => setShowControlSettings(false)}
          onToggleTheme={props.onToggleTheme}
          onOpenUpdateCheck={props.onOpenUpdateCheck}
          onProgressBarStyleChange={props.onProgressBarStyleChange}
          onSingleClickActionChange={props.onSingleClickActionChange}
          onDoubleClickActionChange={props.onDoubleClickActionChange}
          onAlwaysBackgroundPlayChange={props.onAlwaysBackgroundPlayChange}
          onAutoPipChange={props.onAutoPipChange}
          onEnableSwipeGesturesChange={props.onEnableSwipeGesturesChange}
          onGestureSeekStepChange={props.onGestureSeekStepChange}
          onDefaultSoftwareDecoderChange={props.onDefaultSoftwareDecoderChange}
          onShowHiddenFilesChange={props.onShowHiddenFilesChange}
          onMainDisplayModeChange={props.onMainDisplayModeChange}
          onMainViewLayoutChange={props.onMainViewLayoutChange}
          onOneFingerGestureChange={props.onOneFingerGestureChange}
          onTwoFingerGestureChange={props.onTwoFingerGestureChange}
          onThreeFingerGestureChange={props.onThreeFingerGestureChange}
          onTogglePlayerButtonVisibility={props.onTogglePlayerButtonVisibility}
          onShowAllButtons={props.onShowAllButtons}
        />
      )}

      {showUpdateDialog && (
        <UpdateDialog
          currentVersionLabel={currentAppVersionLabel}
          state={updateState}
          onDismiss={props.onDismissUpdateDialog}
          onRetryCheck={props.onOpenUpdateCheck}
          onStartDownload={props.onStartDownload}
          onCancelDownload={props.onCancelDownload}
        />
      )}

      {detailVideo && (
        <VideoPropertiesDialog
          video={detailVideo}
          history={historyByUri[detailVideo.uriString]}
          onClearResume={props.onClearResume}
          onDismiss={() => setDetailVideo(null)}
        />
      )}

      {renameTargetVideo && (
        <RenameVideoDialog
          video={renameTargetVideo}
          onRename={props.onRenameVideo}
          onDismiss={() => setRenameTargetVideo(null)}
        />
      )}

      {playlistTargetVideo && (
        <AddToPlaylistDialog
          video={playlistTargetVideo}
          existingPlaylists={Object.keys(preferences.playlists).sort()}
          onAdd={props.onAddToPlaylist}
          onDismiss={() => setPlaylistTargetVideo(null)}
        />
      )}

      {moveTargetVideos && (
        <MoveVideosDialog
          videos={moveTargetVideos}
          folders={folders}
          onMove={props.onMoveVideos}
          onDismiss={() => setMoveTargetVideos(null)}
        />
      )}

      {deleteTargetVideos && (
        <DeleteConfirmDialog
          videos={deleteTargetVideos}
          onConfirm={(targets) => {
            props.onDeleteVideos(targets);
            props.onClearSelection();
            setDeleteTargetVideos(null);
          }}
          onDismiss={() => setDeleteTargetVideos(null)}
        />
      )}
    </div>
  );
}

function FolderGridCard({
  folder,
  newCount,
  onClick,
}: {
  folder: VideoFolder;
  newCount: number;
  onClick: () => void;
}) {
  const unit = folder.videoCount === 1 ? 'video' : 'videos';
  return (
    <button
      type="button"
      onClick={onClick}
      className="relative flex w-full flex-col items-center rounded-[14px] border border-[var(--outline)]/35 bg-[var(--bg-surface)] p-3.5 text-center transition-colors hover:border-[var(--primary)]/50"
    >
      {newCount > 0 && (
        <span className="absolute top-3 right-3 rounded-full bg-[var(--badge-new-red)] px-1.5 py-0.5 text-[10px] font-bold text-white tabular-nums">
          {newCount}
        </span>
      )}
      <div className="flex h-12 w-12 items-center justify-center rounded-[14px] bg-[var(--primary-container)] text-[var(--primary)]">
        <Folder className="h-7 w-7 fill-current" />
      </div>
      <span className="mt-2 w-full truncate text-sm font-semibold">{folder.name}</span>
      <span className="font-mono-label mt-0.5 w-full truncate text-[10.5px] text-[var(--text-muted)]">
        {folder.videoCount} {unit} · {formatFileSize(folder.totalSizeBytes)}
      </span>
    </button>
  );
}

function FolderListCard({
  folder,
  newCount,
  onClick,
}: {
  folder: VideoFolder;
  newCount: number;
  onClick: () => void;
}) {
  const unit = folder.videoCount === 1 ? 'video' : 'videos';
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center gap-3 rounded-xl border border-[var(--outline)]/35 bg-[var(--bg-surface)] px-3 py-2.5 text-left transition-colors hover:border-[var(--primary)]/50"
    >
      <div className="flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-[10px] bg-[var(--primary-container)] text-[var(--primary)]">
        <Folder className="h-6 w-6 fill-current" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-semibold">{folder.name}</div>
        <div className="font-mono-label flex items-center gap-1 truncate text-[10.5px]">
          <span className="text-[var(--primary)]">
            {folder.videoCount} {unit}
          </span>
          <span className="truncate text-[var(--text-muted)]">
            · {formatFileSize(folder.totalSizeBytes)} · {folder.pathHint}
          </span>
        </div>
      </div>
      {newCount > 0 && (
        <span className="shrink-0 rounded-full bg-[var(--badge-new-red)] px-2 py-0.5 text-[10px] font-bold text-white tabular-nums">
          {newCount}
        </span>
      )}
    </button>
  );
}

function VideoCard({
  video,
  isGrid,
  isSelected,
  isSelectionMode,
  isFavorite,
  isNew,
  history,
  activePlaylistName,
  onClick,
  onLongClick,
  onToggleFavorite,
  onAddToPlaylist,
  onRemoveFromPlaylist,
  onShare,
  onRename,
  onMove,
  onProperties,
  onDelete,
}: {
  video: VideoItem;
  isGrid: boolean;
  isSelected: boolean;
  isSelectionMode: boolean;
  isFavorite: boolean;
  isNew: boolean;
  history?: PlaybackHistoryEntry;
  activePlaylistName: string | null;
  onClick: (shiftKey: boolean) => void;
  onLongClick: () => void;
  onToggleFavorite: () => void;
  onAddToPlaylist: () => void;
  onRemoveFromPlaylist: (name: string) => void;
  onShare: () => void;
  onRename: () => void;
  onMove: () => void;
  onProperties: () => void;
  onDelete: () => void;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const longPressTimerRef = useRef<number | null>(null);
  const longPressedRef = useRef(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const handleOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, [menuOpen]);

  const progressFraction =
    history && history.durationMs > 0 && history.positionMs > 0
      ? Math.max(0, Math.min(1, history.positionMs / history.durationMs))
      : 0;

  const startPress = () => {
    longPressedRef.current = false;
    longPressTimerRef.current = window.setTimeout(() => {
      longPressedRef.current = true;
      onLongClick();
    }, 450);
  };

  const cancelPress = () => {
    if (longPressTimerRef.current) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  };

  const handleCardClick = (e: React.MouseEvent) => {
    if (longPressedRef.current) {
      longPressedRef.current = false;
      return;
    }
    onClick(e.shiftKey);
  };

  if (isGrid) {
    return (
      <div
        onPointerDown={startPress}
        onPointerUp={cancelPress}
        onPointerLeave={cancelPress}
        onPointerCancel={cancelPress}
        onClick={handleCardClick}
        className={`relative flex cursor-pointer flex-col overflow-hidden rounded-[14px] transition-colors ${
          isSelected
            ? 'border-2 border-[var(--primary)] bg-[var(--primary-container)]/35'
            : 'border border-[var(--outline)]/35 bg-[var(--bg-surface)] hover:border-[var(--primary)]/50'
        }`}
      >
        <div className="relative aspect-video w-full overflow-hidden">
          <VideoThumbnail uriString={video.uriString} alt={video.title} className="h-full w-full" />
          {isNew && (
            <span className="absolute top-1.5 left-1.5 rounded bg-[var(--badge-new-red)] px-1.5 py-0.5 text-[9px] font-bold text-white">
              NEW
            </span>
          )}
          {isSelectionMode && (
            <div className="absolute top-1.5 right-1.5">
              {isSelected ? (
                <CheckCircle2 className="h-5 w-5 text-[var(--primary)]" />
              ) : (
                <Circle className="h-5 w-5 text-white drop-shadow" />
              )}
            </div>
          )}
          <span className="font-mono-label absolute right-1.5 bottom-1.5 rounded bg-black/75 px-1.5 py-0.5 text-[10px] text-white">
            {formatDuration(video.durationMs)}
          </span>
          {progressFraction > 0 && (
            <div className="absolute right-0 bottom-0 left-0 h-1 bg-white/25">
              <div
                className="h-full bg-[var(--primary)]"
                style={{ width: `${progressFraction * 100}%` }}
              />
            </div>
          )}
        </div>

        <div className="flex items-start gap-1 p-2.5">
          <div className="min-w-0 flex-1">
            <div className="line-clamp-2 text-xs font-semibold leading-snug">{video.title}</div>
            <div className="font-mono-label mt-1 truncate text-[10px] text-[var(--text-muted)]">
              {formatFileSize(video.sizeBytes)} · {formatResolution(video.width, video.height)}
            </div>
          </div>
          {!isSelectionMode && (
            <div
              ref={menuRef}
              className="relative"
              onClick={(e) => e.stopPropagation()}
              onPointerDown={(e) => e.stopPropagation()}
            >
              <button
                type="button"
                onClick={() => setMenuOpen((o) => !o)}
                aria-label="More options"
                className="flex h-7 w-7 items-center justify-center rounded-full text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)]"
              >
                <MoreVertical className="h-4 w-4" />
              </button>
              {menuOpen && (
                <VideoContextMenu
                  isFavorite={isFavorite}
                  activePlaylistName={activePlaylistName}
                  onClose={() => setMenuOpen(false)}
                  onToggleFavorite={onToggleFavorite}
                  onAddToPlaylist={onAddToPlaylist}
                  onRemoveFromPlaylist={onRemoveFromPlaylist}
                  onShare={onShare}
                  onRename={onRename}
                  onMove={onMove}
                  onProperties={onProperties}
                  onDelete={onDelete}
                />
              )}
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div
      onPointerDown={startPress}
      onPointerUp={cancelPress}
      onPointerLeave={cancelPress}
      onPointerCancel={cancelPress}
      onClick={handleCardClick}
      className={`relative flex cursor-pointer items-center gap-2.5 rounded-xl p-2 transition-colors ${
        isSelected
          ? 'border-2 border-[var(--primary)] bg-[var(--primary-container)]/35'
          : 'border border-[var(--outline)]/35 bg-[var(--bg-surface)] hover:border-[var(--primary)]/50'
      }`}
    >
      <div className="relative h-16 w-[108px] shrink-0 overflow-hidden rounded-lg">
        <VideoThumbnail uriString={video.uriString} alt={video.title} className="h-full w-full" />
        {isNew && (
          <span className="absolute top-1 left-1 rounded bg-[var(--badge-new-red)] px-1 py-0.5 text-[8.5px] font-bold text-white">
            NEW
          </span>
        )}
        <span className="font-mono-label absolute right-1 bottom-1 rounded bg-black/75 px-1 py-0.5 text-[9.5px] text-white">
          {formatDuration(video.durationMs)}
        </span>
        {progressFraction > 0 && (
          <div className="absolute right-0 bottom-0 left-0 h-[2.5px] bg-white/25">
            <div
              className="h-full bg-[var(--primary)]"
              style={{ width: `${progressFraction * 100}%` }}
            />
          </div>
        )}
      </div>

      <div className="min-w-0 flex-1">
        <div className="line-clamp-2 text-sm font-semibold leading-snug">{video.title}</div>
        <div className="font-mono-label mt-0.5 truncate text-[10.5px] text-[var(--text-muted)]">
          {formatFileSize(video.sizeBytes)} · {formatResolution(video.width, video.height)}
        </div>
      </div>

      {isSelectionMode ? (
        <div className="pr-1.5">
          {isSelected ? (
            <CheckCircle2 className="h-5 w-5 text-[var(--primary)]" />
          ) : (
            <Circle className="h-5 w-5 text-[var(--text-muted)]" />
          )}
        </div>
      ) : (
        <div
          ref={menuRef}
          className="relative"
          onClick={(e) => e.stopPropagation()}
          onPointerDown={(e) => e.stopPropagation()}
        >
          <button
            type="button"
            onClick={() => setMenuOpen((o) => !o)}
            aria-label="More options"
            className="flex h-9 w-9 items-center justify-center rounded-full text-[var(--text-muted)] hover:bg-[var(--bg-surface-variant)]"
          >
            <MoreVertical className="h-5 w-5" />
          </button>
          {menuOpen && (
            <VideoContextMenu
              isFavorite={isFavorite}
              activePlaylistName={activePlaylistName}
              onClose={() => setMenuOpen(false)}
              onToggleFavorite={onToggleFavorite}
              onAddToPlaylist={onAddToPlaylist}
              onRemoveFromPlaylist={onRemoveFromPlaylist}
              onShare={onShare}
              onRename={onRename}
              onMove={onMove}
              onProperties={onProperties}
              onDelete={onDelete}
            />
          )}
        </div>
      )}
    </div>
  );
}

function VideoContextMenu({
  isFavorite,
  activePlaylistName,
  onClose,
  onToggleFavorite,
  onAddToPlaylist,
  onRemoveFromPlaylist,
  onShare,
  onRename,
  onMove,
  onProperties,
  onDelete,
}: {
  isFavorite: boolean;
  activePlaylistName: string | null;
  onClose: () => void;
  onToggleFavorite: () => void;
  onAddToPlaylist: () => void;
  onRemoveFromPlaylist: (name: string) => void;
  onShare: () => void;
  onRename: () => void;
  onMove: () => void;
  onProperties: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="absolute right-0 bottom-full z-40 mb-1 w-52 overflow-hidden rounded-2xl border border-[var(--outline)]/50 bg-[var(--bg-surface)] py-1.5 shadow-2xl md:top-full md:bottom-auto md:mt-1 md:mb-0">
      <MenuRow
        icon={<Heart className={`h-4 w-4 ${isFavorite ? 'fill-current text-[var(--secondary)]' : ''}`} />}
        label={isFavorite ? 'Remove from Favourites' : 'Add to Favourites'}
        onClick={() => {
          onClose();
          onToggleFavorite();
        }}
      />
      <MenuRow
        icon={<ListPlus className="h-4 w-4" />}
        label="Add to Playlist"
        onClick={() => {
          onClose();
          onAddToPlaylist();
        }}
      />
      {activePlaylistName && (
        <MenuRow
          icon={<CloseIcon className="h-4 w-4" />}
          label={`Remove from "${activePlaylistName}"`}
          onClick={() => {
            onClose();
            onRemoveFromPlaylist(activePlaylistName);
          }}
        />
      )}
      <MenuRow
        icon={<Edit3 className="h-4 w-4" />}
        label="Rename"
        onClick={() => {
          onClose();
          onRename();
        }}
      />
      <MenuRow
        icon={<MoveIcon className="h-4 w-4" />}
        label="Move"
        onClick={() => {
          onClose();
          onMove();
        }}
      />
      <MenuRow
        icon={<Share2 className="h-4 w-4" />}
        label="Share"
        onClick={() => {
          onClose();
          onShare();
        }}
      />
      <MenuRow
        icon={<Info className="h-4 w-4" />}
        label="Properties"
        onClick={() => {
          onClose();
          onProperties();
        }}
      />
      <div className="my-1 h-px bg-[var(--outline)]/35" />
      <MenuRow
        icon={<Trash2 className="h-4 w-4 text-[var(--error)]" />}
        label="Delete"
        danger
        onClick={() => {
          onClose();
          onDelete();
        }}
      />
    </div>
  );
}

function MenuRow({
  icon,
  label,
  danger,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  danger?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex w-full items-center gap-2.5 px-3.5 py-2 text-left text-xs transition-colors hover:bg-[var(--bg-surface-variant)] ${
        danger ? 'text-[var(--error)]' : 'text-[var(--text-primary)]'
      }`}
    >
      {icon}
      <span className="truncate">{label}</span>
    </button>
  );
}

function EmptyLibraryState({ onOpenDocumentPicker }: { onOpenDocumentPicker: () => void }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center p-8 text-center">
      <FolderOpen className="h-14 w-14 text-[var(--primary)] opacity-70" />
      <h2 className="mt-3 text-sm font-semibold">No Local Videos Found</h2>
      <p className="mt-1 text-xs text-[var(--text-muted)]">
        Add videos to storage or pick a file directly.
      </p>
      <button
        type="button"
        onClick={onOpenDocumentPicker}
        className="mt-3 rounded-xl border border-[var(--outline)] px-4 py-2 text-xs font-semibold text-[var(--primary)] hover:bg-[var(--bg-surface-variant)]"
      >
        Open Video File
      </button>
    </div>
  );
}

function EmptyVideosState() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center p-8 text-center">
      <FolderOpen className="h-12 w-12 text-[var(--text-muted)] opacity-60" />
      <h2 className="mt-2 text-sm font-semibold">No videos found</h2>
    </div>
  );
}
