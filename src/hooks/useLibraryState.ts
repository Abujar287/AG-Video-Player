import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  type BrowserDestination,
  type LibrarySnapshot,
  type PlaybackHistoryEntry,
  type PlaylistEntry,
  type PlayerControlButton,
  type ProgressBarStyle,
  type SingleClickAction,
  type DoubleClickAction,
  SortDirection,
  SortField,
  type UpdateUiState,
  type UserPreferences,
  type VideoFolder,
  type VideoItem,
} from '../types/media';
import { loadPreferences, savePreferences } from '../repositories/preferencesRepository';
import {
  computeSavedPlaybackPosition,
  getLatestHistoryEntry,
  loadPlaybackHistory,
  savePlaybackHistoryMap,
} from '../repositories/playbackRepository';
import {
  deleteVideoItems,
  importLocalVideoFiles,
  loadLibrarySnapshot,
  moveVideoItems,
  renameVideoItem,
} from '../repositories/videoLibraryRepository';
import { checkForUpdate, downloadApkInBrowser, getCurrentAppVersionLabel } from '../services/githubUpdater';

function filterAndSortVideos(
  raw: VideoItem[],
  query: string,
  field: SortField,
  dir: SortDirection,
  prefs: UserPreferences
): VideoItem[] {
  const q = query.trim().toLowerCase();
  const matched = !q
    ? raw
    : raw.filter(
        (v) => v.title.toLowerCase().includes(q) || v.displayName.toLowerCase().includes(q)
      );

  const playedSet = new Set(prefs.playedVideoUris);
  const sorted = [...matched].sort((a, b) => {
    switch (field) {
      case SortField.NAME:
        return a.title.toLowerCase().localeCompare(b.title.toLowerCase());
      case SortField.SIZE:
        return a.sizeBytes - b.sizeBytes;
      case SortField.DATE:
        return a.dateModifiedSec - b.dateModifiedSec;
      case SortField.TYPE: {
        const extA = a.displayName.includes('.') ? a.displayName.split('.').pop()!.toLowerCase() : '';
        const extB = b.displayName.includes('.') ? b.displayName.split('.').pop()!.toLowerCase() : '';
        return extA.localeCompare(extB);
      }
      case SortField.PLAYS:
        return (playedSet.has(a.uriString) ? 1 : 0) - (playedSet.has(b.uriString) ? 1 : 0);
    }
  });

  return dir === SortDirection.DESC ? sorted.reverse() : sorted;
}

export function useLibraryState() {
  const [isLoadingLibrary, setIsLoadingLibrary] = useState(true);
  const [snapshot, setSnapshot] = useState<LibrarySnapshot>({
    folders: [],
    videosByFolder: {},
    allVideos: [],
  });
  const [destination, setDestination] = useState<BrowserDestination>({ type: 'folders' });
  const [searchQuery, setSearchQuery] = useState('');
  const [sortField, setSortField] = useState<SortField>(SortField.DATE);
  const [sortDirection, setSortDirection] = useState<SortDirection>(SortDirection.DESC);
  const [selectedVideoUris, setSelectedVideoUris] = useState<Set<string>>(() => new Set());
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [preferences, setPreferences] = useState<UserPreferences>(() => loadPreferences());
  const [historyByUri, setHistoryByUri] = useState<Record<string, PlaybackHistoryEntry>>(() =>
    loadPlaybackHistory()
  );
  const [updateState, setUpdateState] = useState<UpdateUiState>({ status: 'idle' });
  const [showUpdateDialog, setShowUpdateDialog] = useState(false);
  const downloadAbortRef = useRef<AbortController | null>(null);
  const lastReleaseRef = useRef<import('../types/media').GitHubReleaseInfo | null>(null);

  // Sync dark mode class on <html>
  useEffect(() => {
    const root = document.documentElement;
    if (preferences.isDarkMode) {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [preferences.isDarkMode]);

  const updatePreferences = useCallback((updater: (prev: UserPreferences) => UserPreferences) => {
    setPreferences((prev) => {
      const next = updater(prev);
      savePreferences(next);
      return next;
    });
  }, []);

  const refreshLibrary = useCallback(async () => {
    setIsLoadingLibrary(true);
    try {
      const nextSnap = await loadLibrarySnapshot(preferences.showHiddenFiles);
      setSnapshot(nextSnap);
    } finally {
      setIsLoadingLibrary(false);
    }
  }, [preferences.showHiddenFiles]);

  useEffect(() => {
    void refreshLibrary();
  }, [refreshLibrary]);

  const lastHistoryEntry = useMemo(() => {
    const latest = getLatestHistoryEntry(historyByUri);
    if (!latest) return null;
    const available = new Set(snapshot.allVideos.map((v) => v.uriString));
    if (available.has(latest.videoUri) || latest.positionMs > 0) {
      return latest;
    }
    return null;
  }, [historyByUri, snapshot.allVideos]);

  const filteredFolders = useMemo((): VideoFolder[] => {
    const q = searchQuery.trim().toLowerCase();
    const matched = !q
      ? snapshot.folders
      : snapshot.folders.filter(
          (folder) =>
            folder.name.toLowerCase().includes(q) ||
            (snapshot.videosByFolder[folder.bucketId] ?? []).some(
              (v) => v.title.toLowerCase().includes(q) || v.displayName.toLowerCase().includes(q)
            )
        );

    const playedSet = new Set(preferences.playedVideoUris);
    const unplayedCounts: Record<string, number> = {};
    for (const [bucketId, list] of Object.entries(snapshot.videosByFolder)) {
      unplayedCounts[bucketId] = list.filter((v) => !playedSet.has(v.uriString)).length;
    }

    const sorted = [...matched].sort((a, b) => {
      switch (sortField) {
        case SortField.NAME:
          return a.name.toLowerCase().localeCompare(b.name.toLowerCase());
        case SortField.SIZE:
          return a.totalSizeBytes - b.totalSizeBytes;
        case SortField.DATE:
          return a.latestModifiedSec - b.latestModifiedSec;
        case SortField.TYPE:
          return a.pathHint.toLowerCase().localeCompare(b.pathHint.toLowerCase());
        case SortField.PLAYS:
          return (unplayedCounts[a.bucketId] ?? 0) - (unplayedCounts[b.bucketId] ?? 0);
      }
    });

    return sortDirection === SortDirection.DESC ? sorted.reverse() : sorted;
  }, [snapshot, searchQuery, sortField, sortDirection, preferences.playedVideoUris]);

  const currentVideos = useMemo((): VideoItem[] => {
    const byUri = new Map(snapshot.allVideos.map((v) => [v.uriString, v]));
    let raw: VideoItem[] = [];
    if (destination.type === 'folder') {
      raw = snapshot.videosByFolder[destination.bucketId] ?? [];
    } else if (destination.type === 'favorites') {
      const favSet = new Set(preferences.favoriteUris);
      raw = snapshot.allVideos.filter((v) => favSet.has(v.uriString));
    } else if (destination.type === 'playlist') {
      const uris = preferences.playlists[destination.name] ?? [];
      raw = uris.map((u) => byUri.get(u)).filter((v): v is VideoItem => Boolean(v));
    }
    return filterAndSortVideos(raw, searchQuery, sortField, sortDirection, preferences);
  }, [snapshot, destination, searchQuery, sortField, sortDirection, preferences]);

  const sortedAllVideos = useMemo(
    () => filterAndSortVideos(snapshot.allVideos, searchQuery, sortField, sortDirection, preferences),
    [snapshot.allVideos, searchQuery, sortField, sortDirection, preferences]
  );

  // Navigation
  const openFolder = useCallback((bucketId: string) => {
    setDestination({ type: 'folder', bucketId });
    setSearchQuery('');
    setSelectedVideoUris(new Set());
  }, []);

  const openFavorites = useCallback(() => {
    setDestination({ type: 'favorites' });
    setSearchQuery('');
    setSelectedVideoUris(new Set());
  }, []);

  const openPlaylist = useCallback((name: string) => {
    setDestination({ type: 'playlist', name });
    setSearchQuery('');
    setSelectedVideoUris(new Set());
  }, []);

  const navigateBackToFolders = useCallback(() => {
    setDestination({ type: 'folders' });
    setSearchQuery('');
    setSelectedVideoUris(new Set());
  }, []);

  const setSortOption = useCallback((field: SortField, dir: SortDirection) => {
    setSortField(field);
    setSortDirection(dir);
  }, []);

  // Selection
  const startSelection = useCallback((uri: string) => {
    setSelectedVideoUris((prev) => new Set(prev).add(uri));
  }, []);

  const toggleSelection = useCallback((uri: string) => {
    setSelectedVideoUris((prev) => {
      const next = new Set(prev);
      if (next.has(uri)) next.delete(uri);
      else next.add(uri);
      return next;
    });
  }, []);

  const selectAllVisible = useCallback(() => {
    const list =
      destination.type === 'folders' &&
      (preferences.mainDisplayMode === 'files' || searchQuery.trim().length > 0)
        ? sortedAllVideos
        : currentVideos;
    setSelectedVideoUris(new Set(list.map((v) => v.uriString)));
  }, [destination.type, preferences.mainDisplayMode, searchQuery, sortedAllVideos, currentVideos]);

  const clearSelection = useCallback(() => {
    setSelectedVideoUris(new Set());
  }, []);

  // Collections
  const toggleFavorite = useCallback(
    (video: VideoItem) => {
      let added = false;
      updatePreferences((prev) => {
        const exists = prev.favoriteUris.includes(video.uriString);
        added = !exists;
        return {
          ...prev,
          favoriteUris: exists
            ? prev.favoriteUris.filter((u) => u !== video.uriString)
            : [...prev.favoriteUris, video.uriString],
        };
      });
      setActionMessage(
        added ? `Added "${video.title}" to Favourites` : `Removed "${video.title}" from Favourites`
      );
    },
    [updatePreferences]
  );

  const addToPlaylist = useCallback(
    (name: string, video: VideoItem) => {
      const clean = name.trim();
      if (!clean) return;
      updatePreferences((prev) => {
        const list = prev.playlists[clean] ?? [];
        if (list.includes(video.uriString)) return prev;
        return {
          ...prev,
          playlists: {
            ...prev.playlists,
            [clean]: [...list, video.uriString],
          },
        };
      });
      setActionMessage(`Added to playlist "${clean}"`);
    },
    [updatePreferences]
  );

  const removeFromPlaylist = useCallback(
    (name: string, video: VideoItem) => {
      updatePreferences((prev) => {
        const nextList = (prev.playlists[name] ?? []).filter((u) => u !== video.uriString);
        const nextPlaylists = { ...prev.playlists };
        if (nextList.length === 0) delete nextPlaylists[name];
        else nextPlaylists[name] = nextList;
        return { ...prev, playlists: nextPlaylists };
      });
      setActionMessage(`Removed from playlist "${name}"`);
    },
    [updatePreferences]
  );

  const deletePlaylist = useCallback(
    (name: string) => {
      updatePreferences((prev) => {
        const nextPlaylists = { ...prev.playlists };
        delete nextPlaylists[name];
        return { ...prev, playlists: nextPlaylists };
      });
      if (destination.type === 'playlist' && destination.name === name) {
        setDestination({ type: 'folders' });
      }
      setActionMessage(`Deleted playlist "${name}"`);
    },
    [destination, updatePreferences]
  );

  // Mutations
  const renameVideo = useCallback(
    async (video: VideoItem, newTitle: string) => {
      const res = await renameVideoItem(video, newTitle);
      setActionMessage(res.message);
      if (res.ok) await refreshLibrary();
    },
    [refreshLibrary]
  );

  const moveVideos = useCallback(
    async (videos: VideoItem[], targetPath: string) => {
      const res = await moveVideoItems(videos, targetPath);
      setActionMessage(res.message);
      if (res.ok) {
        setSelectedVideoUris(new Set());
        await refreshLibrary();
      }
    },
    [refreshLibrary]
  );

  const deleteVideos = useCallback(
    async (videos: VideoItem[]) => {
      setSelectedVideoUris(new Set());
      const res = await deleteVideoItems(videos);
      if (res.ok) {
        const removedSet = new Set(videos.map((v) => v.uriString));
        setHistoryByUri((prev) => {
          const next = { ...prev };
          for (const u of removedSet) delete next[u];
          savePlaybackHistoryMap(next);
          return next;
        });
        updatePreferences((prev) => {
          const nextPlaylists: Record<string, string[]> = {};
          for (const [k, list] of Object.entries(prev.playlists)) {
            const filtered = list.filter((u) => !removedSet.has(u));
            if (filtered.length > 0) nextPlaylists[k] = filtered;
          }
          return {
            ...prev,
            favoriteUris: prev.favoriteUris.filter((u) => !removedSet.has(u)),
            playlists: nextPlaylists,
          };
        });
        setSelectedVideoUris(new Set());
        await refreshLibrary();
      }
      setActionMessage(res.message);
    },
    [refreshLibrary, updatePreferences]
  );

  const importVideos = useCallback(
    async (files: FileList | File[], targetFolder?: string): Promise<VideoItem[]> => {
      const fileArr = Array.from(files);
      const folder =
        targetFolder ??
        (destination.type === 'folder'
          ? snapshot.folders.find((f) => f.bucketId === destination.bucketId)?.pathHint ?? 'Local Videos'
          : 'Local Videos');
      const added = await importLocalVideoFiles(fileArr, folder);
      if (added.length > 0) {
        await refreshLibrary();
        setActionMessage(`Added ${added.length} video(s) to ${folder}`);
      } else {
        setActionMessage('No supported video files selected.');
      }
      return added;
    },
    [destination, snapshot.folders, refreshLibrary]
  );

  // Playback history
  const savePlaybackState = useCallback(
    (
      videoUri: string,
      title: string,
      positionMs: number,
      durationMs: number,
      playbackSpeed: number,
      useSoftwareDecoder: boolean
    ) => {
      if (!videoUri || durationMs <= 0) return;
      const savedPos = computeSavedPlaybackPosition(positionMs, durationMs);
      setHistoryByUri((prev) => {
        const next: Record<string, PlaybackHistoryEntry> = {
          ...prev,
          [videoUri]: {
            videoUri,
            title,
            positionMs: savedPos,
            durationMs,
            lastPlayedAt: Date.now(),
            playbackSpeed,
            useSoftwareDecoder,
          },
        };
        savePlaybackHistoryMap(next);
        return next;
      });
    },
    []
  );

  const clearResumePosition = useCallback((videoUri: string) => {
    setHistoryByUri((prev) => {
      const next = { ...prev };
      delete next[videoUri];
      savePlaybackHistoryMap(next);
      return next;
    });
    setActionMessage('Resume position cleared');
  }, []);

  const markVideoPlayed = useCallback(
    (uri: string) => {
      if (!uri) return;
      updatePreferences((prev) => {
        if (prev.playedVideoUris.includes(uri)) return prev;
        return { ...prev, playedVideoUris: [...prev.playedVideoUris, uri] };
      });
    },
    [updatePreferences]
  );

  const buildPlaylistFor = useCallback(
    (video: VideoItem, list: VideoItem[]): { entries: PlaylistEntry[]; startIndex: number } => {
      const entries = list.map((v) => ({ uriString: v.uriString, title: v.title }));
      const idx = Math.max(
        0,
        list.findIndex((v) => v.uriString === video.uriString)
      );
      return { entries, startIndex: idx };
    },
    []
  );

  const getLastPlayedVideoAndPlaylist = useCallback((): {
    video: VideoItem;
    list: VideoItem[];
  } | null => {
    if (snapshot.allVideos.length === 0) return null;
    const target = lastHistoryEntry
      ? snapshot.allVideos.find((v) => v.uriString === lastHistoryEntry.videoUri) ??
        snapshot.allVideos[0]
      : snapshot.allVideos[0];
    if (!target) return null;
    const folderList = snapshot.videosByFolder[target.bucketId];
    return {
      video: target,
      list: folderList && folderList.length > 0 ? folderList : [target],
    };
  }, [snapshot, lastHistoryEntry]);

  // Settings setters
  const toggleDarkMode = useCallback(
    () => updatePreferences((p) => ({ ...p, isDarkMode: !p.isDarkMode })),
    [updatePreferences]
  );
  const toggleGridView = useCallback(
    () =>
      updatePreferences((p) => {
        const next = !p.isGridView;
        return { ...p, isGridView: next, mainViewLayout: next ? 'grid' : 'list' };
      }),
    [updatePreferences]
  );
  const setShowHiddenFiles = useCallback(
    (v: boolean) => updatePreferences((p) => ({ ...p, showHiddenFiles: v })),
    [updatePreferences]
  );
  const setMainDisplayMode = useCallback(
    (m: 'folders' | 'files') => updatePreferences((p) => ({ ...p, mainDisplayMode: m })),
    [updatePreferences]
  );
  const setMainViewLayout = useCallback(
    (l: 'list' | 'grid') =>
      updatePreferences((p) => ({ ...p, mainViewLayout: l, isGridView: l === 'grid' })),
    [updatePreferences]
  );
  const setProgressBarStyle = useCallback(
    (style: ProgressBarStyle) => updatePreferences((p) => ({ ...p, progressBarStyle: style })),
    [updatePreferences]
  );
  const setOneFingerGesture = useCallback(
    (g: string) => updatePreferences((p) => ({ ...p, oneFingerGesture: g })),
    [updatePreferences]
  );
  const setTwoFingerGesture = useCallback(
    (g: string) => updatePreferences((p) => ({ ...p, twoFingerGesture: g })),
    [updatePreferences]
  );
  const setThreeFingerGesture = useCallback(
    (g: string) => updatePreferences((p) => ({ ...p, threeFingerGesture: g })),
    [updatePreferences]
  );
  const setAutoPipOnHome = useCallback(
    (v: boolean) => updatePreferences((p) => ({ ...p, autoPipOnHome: v })),
    [updatePreferences]
  );
  const setDefaultSoftwareDecoder = useCallback(
    (v: boolean) => updatePreferences((p) => ({ ...p, defaultSoftwareDecoder: v })),
    [updatePreferences]
  );
  const setSingleClickAction = useCallback(
    (a: SingleClickAction) => updatePreferences((p) => ({ ...p, singleClickAction: a })),
    [updatePreferences]
  );
  const setDoubleClickAction = useCallback(
    (a: DoubleClickAction) => updatePreferences((p) => ({ ...p, doubleClickAction: a })),
    [updatePreferences]
  );
  const setAlwaysBackgroundPlay = useCallback(
    (v: boolean) => updatePreferences((p) => ({ ...p, alwaysBackgroundPlay: v })),
    [updatePreferences]
  );
  const setEnableSwipeGestures = useCallback(
    (v: boolean) => updatePreferences((p) => ({ ...p, enableSwipeGestures: v })),
    [updatePreferences]
  );
  const setGestureSeekStepSeconds = useCallback(
    (s: number) => updatePreferences((p) => ({ ...p, gestureSeekStepSeconds: s })),
    [updatePreferences]
  );
  const setPlayerButtonVisible = useCallback(
    (b: PlayerControlButton, visible: boolean) =>
      updatePreferences((p) => {
        const set = new Set(p.hiddenButtons);
        if (visible) set.delete(b);
        else set.add(b);
        return { ...p, hiddenButtons: Array.from(set) };
      }),
    [updatePreferences]
  );
  const showAllPlayerButtons = useCallback(
    () => updatePreferences((p) => ({ ...p, hiddenButtons: [] })),
    [updatePreferences]
  );

  // GitHub Updates
  const openUpdateCheck = useCallback(async () => {
    setShowUpdateDialog(true);
    setUpdateState((prev) => (prev.status === 'downloading' ? prev : { status: 'checking' }));
    if (updateState.status === 'downloading') return;
    const next = await checkForUpdate(preferences.githubRepoSlug);
    if (next.status === 'available') {
      lastReleaseRef.current = next.release;
    }
    setUpdateState(next);
  }, [preferences.githubRepoSlug, updateState.status]);

  const startUpdateDownload = useCallback(async (release: import('../types/media').GitHubReleaseInfo) => {
    lastReleaseRef.current = release;
    downloadAbortRef.current?.abort();
    const controller = new AbortController();
    downloadAbortRef.current = controller;
    setUpdateState({
      status: 'downloading',
      percent: 0,
      bytesDownloaded: 0,
      bytesTotal: release.apkSizeBytes,
    });

    const outcome = await downloadApkInBrowser(
      release,
      (bytesDownloaded, bytesTotal, percent) => {
        setUpdateState({ status: 'downloading', percent, bytesDownloaded, bytesTotal });
      },
      controller.signal
    );

    if (outcome === 'aborted') {
      return;
    }

    const fileName = release.apkFileName || `AG-Video-Player-v${release.versionName}.apk`;
    if (outcome === 'fallback_link') {
      const a = document.createElement('a');
      a.href = release.apkDownloadUrl;
      a.download = fileName;
      a.rel = 'noopener noreferrer';
      document.body.appendChild(a);
      a.click();
      a.remove();
      setActionMessage(`Download started in browser: ${fileName}`);
    } else {
      setActionMessage(`Downloaded ${fileName} to Downloads`);
    }
    setShowUpdateDialog(false);
    setUpdateState({ status: 'available', release });
  }, []);

  const cancelUpdateDownload = useCallback(() => {
    downloadAbortRef.current?.abort();
    downloadAbortRef.current = null;
    setShowUpdateDialog(false);
    if (lastReleaseRef.current) {
      setUpdateState({ status: 'available', release: lastReleaseRef.current });
    } else {
      setUpdateState({ status: 'idle' });
    }
  }, []);

  const dismissUpdateDialog = useCallback(() => {
    setShowUpdateDialog(false);
  }, []);

  return {
    isLoadingLibrary,
    snapshot,
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
    currentAppVersionLabel: getCurrentAppVersionLabel(),
    refreshLibrary,
    setSearchQuery,
    openFolder,
    openFavorites,
    openPlaylist,
    navigateBackToFolders,
    setSortOption,
    startSelection,
    toggleSelection,
    selectAllVisible,
    clearSelection,
    toggleFavorite,
    addToPlaylist,
    removeFromPlaylist,
    deletePlaylist,
    renameVideo,
    moveVideos,
    deleteVideos,
    importVideos,
    savePlaybackState,
    clearResumePosition,
    clearActionMessage: () => setActionMessage(null),
    markVideoPlayed,
    buildPlaylistFor,
    getLastPlayedVideoAndPlaylist,
    toggleDarkMode,
    toggleGridView,
    setShowHiddenFiles,
    setMainDisplayMode,
    setMainViewLayout,
    setProgressBarStyle,
    setOneFingerGesture,
    setTwoFingerGesture,
    setThreeFingerGesture,
    setAutoPipOnHome,
    setDefaultSoftwareDecoder,
    setSingleClickAction,
    setDoubleClickAction,
    setAlwaysBackgroundPlay,
    setEnableSwipeGestures,
    setGestureSeekStepSeconds,
    setPlayerButtonVisible,
    showAllPlayerButtons,
    openUpdateCheck,
    startUpdateDownload,
    cancelUpdateDownload,
    dismissUpdateDialog,
  };
}
