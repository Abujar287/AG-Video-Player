import {
  DoubleClickAction,
  PlayerControlButton,
  SingleClickAction,
  type UserPreferences,
} from '../types/media';
import { DEFAULT_GITHUB_REPO_SLUG } from '../services/githubUpdater';

const STORAGE_KEY = 'ag_player_prefs_v1';

export function getDefaultPreferences(): UserPreferences {
  return {
    isDarkMode: true,
    isGridView: false,
    showHiddenFiles: false,
    mainDisplayMode: 'folders',
    mainViewLayout: 'list',
    progressBarStyle: 'material',
    oneFingerGesture: 'Brightness / Volume Swipe',
    twoFingerGesture: 'Zoom / Scale',
    threeFingerGesture: 'Play / Pause',
    autoPipOnHome: false,
    defaultSoftwareDecoder: false,
    githubRepoSlug: DEFAULT_GITHUB_REPO_SLUG,
    favoriteUris: [],
    playlists: {},
    playedVideoUris: [],
    singleClickAction: SingleClickAction.TOGGLE_CONTROLS,
    doubleClickAction: DoubleClickAction.SEEK_10S,
    alwaysBackgroundPlay: false,
    enableSwipeGestures: true,
    gestureSeekStepSeconds: 0,
    hiddenButtons: [],
  };
}

export function loadPreferences(): UserPreferences {
  const defaults = getDefaultPreferences();
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return defaults;
    const parsed = JSON.parse(raw) as Partial<UserPreferences>;

    const singleClick = Object.values(SingleClickAction).includes(
      parsed.singleClickAction as SingleClickAction
    )
      ? (parsed.singleClickAction as SingleClickAction)
      : defaults.singleClickAction;

    const doubleClick = Object.values(DoubleClickAction).includes(
      parsed.doubleClickAction as DoubleClickAction
    )
      ? (parsed.doubleClickAction as DoubleClickAction)
      : defaults.doubleClickAction;

    const hiddenButtons = Array.isArray(parsed.hiddenButtons)
      ? parsed.hiddenButtons.filter((b): b is PlayerControlButton =>
          Object.values(PlayerControlButton).includes(b as PlayerControlButton)
        )
      : defaults.hiddenButtons;

    const isGridView = typeof parsed.isGridView === 'boolean' ? parsed.isGridView : defaults.isGridView;
    const mainViewLayout =
      parsed.mainViewLayout === 'grid' || parsed.mainViewLayout === 'list'
        ? parsed.mainViewLayout
        : isGridView
          ? 'grid'
          : 'list';

    return {
      ...defaults,
      ...parsed,
      isGridView,
      mainViewLayout,
      mainDisplayMode: parsed.mainDisplayMode === 'files' ? 'files' : 'folders',
      progressBarStyle: parsed.progressBarStyle === 'flat' ? 'flat' : 'material',
      singleClickAction: singleClick,
      doubleClickAction: doubleClick,
      hiddenButtons,
      favoriteUris: Array.isArray(parsed.favoriteUris) ? parsed.favoriteUris : [],
      playedVideoUris: Array.isArray(parsed.playedVideoUris) ? parsed.playedVideoUris : [],
      playlists: parsed.playlists && typeof parsed.playlists === 'object' ? parsed.playlists : {},
      githubRepoSlug: parsed.githubRepoSlug?.trim() || DEFAULT_GITHUB_REPO_SLUG,
    };
  } catch {
    return defaults;
  }
}

export function savePreferences(prefs: UserPreferences): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
  } catch {
    // Ignore storage quota errors
  }
}
