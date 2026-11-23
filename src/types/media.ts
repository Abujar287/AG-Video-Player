export interface VideoFolder {
  bucketId: string;
  name: string;
  pathHint: string;
  videoCount: number;
  totalSizeBytes: number;
  latestModifiedSec: number;
  previewVideoId: number;
  previewUri: string;
}

export interface VideoItem {
  id: number;
  uriString: string;
  title: string;
  displayName: string;
  bucketId: string;
  folderName: string;
  relativePath: string;
  filePath?: string | null;
  durationMs: number;
  sizeBytes: number;
  width: number;
  height: number;
  dateModifiedSec: number;
  mimeType: string;
  isBundled?: boolean;
}

export interface PlaybackHistoryEntry {
  videoUri: string;
  title: string;
  positionMs: number;
  durationMs: number;
  lastPlayedAt: number;
  playbackSpeed: number;
  useSoftwareDecoder: boolean;
}

export enum SortField {
  NAME = 'NAME',
  SIZE = 'SIZE',
  DATE = 'DATE',
  TYPE = 'TYPE',
  PLAYS = 'PLAYS',
}

export const SORT_FIELD_LABELS: Record<SortField, string> = {
  [SortField.NAME]: 'Name',
  [SortField.SIZE]: 'Size',
  [SortField.DATE]: 'Date',
  [SortField.TYPE]: 'Type',
  [SortField.PLAYS]: 'Plays',
};

export enum SortDirection {
  ASC = 'ASC',
  DESC = 'DESC',
}

export type BrowserDestination =
  | { type: 'folders' }
  | { type: 'folder'; bucketId: string }
  | { type: 'favorites' }
  | { type: 'playlist'; name: string };

export enum SingleClickAction {
  TOGGLE_CONTROLS = 'TOGGLE_CONTROLS',
  PLAY_PAUSE = 'PLAY_PAUSE',
  TOGGLE_MUTE = 'TOGGLE_MUTE',
  NONE = 'NONE',
}

export const SINGLE_CLICK_LABELS: Record<SingleClickAction, string> = {
  [SingleClickAction.TOGGLE_CONTROLS]: 'Show / Hide Controls',
  [SingleClickAction.PLAY_PAUSE]: 'Play / Pause',
  [SingleClickAction.TOGGLE_MUTE]: 'Mute / Unmute',
  [SingleClickAction.NONE]: 'Do Nothing',
};

export enum DoubleClickAction {
  PLAY_PAUSE = 'PLAY_PAUSE',
  SEEK_10S = 'SEEK_10S',
  SEEK_20S = 'SEEK_20S',
  NONE = 'NONE',
}

export const DOUBLE_CLICK_LABELS: Record<DoubleClickAction, string> = {
  [DoubleClickAction.PLAY_PAUSE]: 'Play / Pause',
  [DoubleClickAction.SEEK_10S]: 'Seek ±10s',
  [DoubleClickAction.SEEK_20S]: 'Seek ±20s',
  [DoubleClickAction.NONE]: 'Do Nothing',
};

export enum PlayerControlButton {
  FIT = 'FIT',
  ROTATE = 'ROTATE',
  SPEED = 'SPEED',
  LOOP = 'LOOP',
  MUTE = 'MUTE',
  ZOOM = 'ZOOM',
  SLEEP = 'SLEEP',
  BG_PLAY = 'BG_PLAY',
  TRACKS = 'TRACKS',
  DECODER = 'DECODER',
  LOCK = 'LOCK',
  PREV_NEXT = 'PREV_NEXT',
  PIP = 'PIP',
}

export const PLAYER_BUTTON_LABELS: Record<PlayerControlButton, string> = {
  [PlayerControlButton.FIT]: 'Fit / Full',
  [PlayerControlButton.ROTATE]: 'Rotate',
  [PlayerControlButton.SPEED]: 'Speed',
  [PlayerControlButton.LOOP]: 'Loop',
  [PlayerControlButton.MUTE]: 'Mute',
  [PlayerControlButton.ZOOM]: 'Quick Zoom',
  [PlayerControlButton.SLEEP]: 'Sleep Timer',
  [PlayerControlButton.BG_PLAY]: 'BG Play',
  [PlayerControlButton.TRACKS]: 'Tracks',
  [PlayerControlButton.DECODER]: 'HW / SW',
  [PlayerControlButton.LOCK]: 'Lock',
  [PlayerControlButton.PREV_NEXT]: 'Prev / Next',
  [PlayerControlButton.PIP]: 'PiP',
};

export enum PlayerAspectMode {
  FIT = 'FIT',
  FILL = 'FILL',
  ZOOM = 'ZOOM',
}

export const PLAYER_ASPECT_LABELS: Record<PlayerAspectMode, string> = {
  [PlayerAspectMode.FIT]: 'Fit',
  [PlayerAspectMode.FILL]: 'Stretch',
  [PlayerAspectMode.ZOOM]: 'Crop',
};

export enum RepeatMode {
  OFF = 0,
  ONE = 1,
  ALL = 2,
}

export interface PlaylistEntry {
  uriString: string;
  title: string;
}

export interface MediaTrackOption {
  id: string;
  label: string;
  language?: string | null;
  isSelected: boolean;
  srcUrl?: string;
}

export type ProgressBarStyle = 'material' | 'flat';

export interface UserPreferences {
  isDarkMode: boolean;
  isGridView: boolean;
  showHiddenFiles: boolean;
  mainDisplayMode: 'folders' | 'files';
  mainViewLayout: 'list' | 'grid';
  progressBarStyle: ProgressBarStyle;
  oneFingerGesture: string;
  twoFingerGesture: string;
  threeFingerGesture: string;
  autoPipOnHome: boolean;
  defaultSoftwareDecoder: boolean;
  githubRepoSlug: string;
  favoriteUris: string[];
  playlists: Record<string, string[]>;
  playedVideoUris: string[];
  singleClickAction: SingleClickAction;
  doubleClickAction: DoubleClickAction;
  alwaysBackgroundPlay: boolean;
  enableSwipeGestures: boolean;
  gestureSeekStepSeconds: number;
  hiddenButtons: PlayerControlButton[];
}

export interface GitHubReleaseInfo {
  versionName: string;
  versionCode: number | null;
  releaseTitle: string;
  releaseNotes: string;
  apkDownloadUrl: string;
  apkFileName: string;
  apkSizeBytes: number;
  htmlUrl: string;
}

export type UpdateUiState =
  | { status: 'idle' }
  | { status: 'checking' }
  | { status: 'available'; release: GitHubReleaseInfo }
  | { status: 'downloading'; percent: number; bytesDownloaded: number; bytesTotal: number }
  | { status: 'up_to_date'; versionName: string }
  | { status: 'error'; message: string };

export interface LibrarySnapshot {
  folders: VideoFolder[];
  videosByFolder: Record<string, VideoItem[]>;
  allVideos: VideoItem[];
}
