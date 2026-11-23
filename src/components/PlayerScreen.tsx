import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  Captions,
  Check,
  ChevronRight,
  Cpu,
  FileText,
  Gauge,
  Headphones,
  Lock,
  LockOpen,
  Moon,
  Pause,
  PictureInPicture2,
  Play,
  Repeat,
  Repeat1,
  RotateCw,
  SkipBack,
  SkipForward,
  Sun,
  Volume2,
  VolumeX,
  ZoomIn,
} from 'lucide-react';
import {
  DoubleClickAction,
  type MediaTrackOption,
  type PlaybackHistoryEntry,
  PLAYER_ASPECT_LABELS,
  PlayerAspectMode,
  PlayerControlButton,
  type PlaylistEntry,
  RepeatMode,
  SingleClickAction,
  type UserPreferences,
} from '../types/media';
import { resolvePlayableUrl } from '../repositories/videoLibraryRepository';
import { formatDuration, formatSignedDelta } from '../utils/format';
import { createSubtitleBlobUrl } from '../utils/subtitles';
import { FitScreenIcon } from './ControlSettingsDialog';

type DragMode = 'NONE' | 'SEEK' | 'BRIGHTNESS' | 'VOLUME';
type TopToolItem = 'ZOOM' | 'MUTE' | 'ROTATE' | 'SPEED' | 'LOOP' | 'SLEEP' | 'BG_PLAY' | 'TRACKS';

interface PlayerScreenProps {
  playlist: PlaylistEntry[];
  initialIndex: number;
  initialPositionMs?: number;
  preferences: UserPreferences;
  historyByUri: Record<string, PlaybackHistoryEntry>;
  onBack: (bgAudioInfo?: {
    uriString: string;
    title: string;
    currentTimeSec: number;
    playbackRate: number;
    volumePercent: number;
  }) => void;
  onMarkPlayed: (uriString: string) => void;
  onSavePlaybackState: (
    videoUri: string,
    title: string,
    positionMs: number,
    durationMs: number,
    playbackSpeed: number,
    useSoftwareDecoder: boolean
  ) => void;
  onSetDefaultSoftwareDecoder: (enabled: boolean) => void;
  onSetAlwaysBackgroundPlay: (enabled: boolean) => void;
}

const ZOOM_STEPS = [1, 1.25, 1.5, 2];

export function PlayerScreen({
  playlist,
  initialIndex,
  initialPositionMs,
  preferences,
  historyByUri,
  onBack,
  onMarkPlayed,
  onSavePlaybackState,
  onSetDefaultSoftwareDecoder,
  onSetAlwaysBackgroundPlay,
}: PlayerScreenProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const subtitleInputRef = useRef<HTMLInputElement>(null);
  const flatBarRef = useRef<HTMLDivElement>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const gainNodeRef = useRef<GainNode | null>(null);
  const sourceConnectedRef = useRef(false);

  const [currentIndex, setCurrentIndex] = useState(() =>
    Math.max(0, Math.min(initialIndex, Math.max(0, playlist.length - 1)))
  );
  const currentEntry = playlist[currentIndex] ?? playlist[0];

  const [isPlaying, setIsPlaying] = useState(true);
  const [currentPosMs, setCurrentPosMs] = useState(0);
  const [durationMs, setDurationMs] = useState(0);
  const [playbackSpeed, setPlaybackSpeed] = useState(1.0);
  const [useSoftwareDecoder, setUseSoftwareDecoder] = useState(preferences.defaultSoftwareDecoder);
  const [repeatMode, setRepeatMode] = useState<RepeatMode>(RepeatMode.OFF);
  const [sleepMinutesRemaining, setSleepMinutesRemaining] = useState<number | null>(null);
  const [isLocked, setIsLocked] = useState(false);
  const [isBackgroundPlayEnabled, setIsBackgroundPlayEnabled] = useState(
    preferences.alwaysBackgroundPlay
  );
  const [controlsVisible, setControlsVisible] = useState(true);
  const [aspectMode, setAspectMode] = useState<PlayerAspectMode>(PlayerAspectMode.FIT);
  const [isRotated, setIsRotated] = useState(false);
  const [brightnessPercent, setBrightnessPercent] = useState(100);
  const [volumePercent, setVolumePercent] = useState(100);
  const [lastNonZeroVolume, setLastNonZeroVolume] = useState(100);
  const [isMuted, setIsMuted] = useState(false);
  const [statusBanner, setStatusBanner] = useState<string | null>(null);

  const [showMoreTools, setShowMoreTools] = useState(false);
  const [showSpeedPicker, setShowSpeedPicker] = useState(false);
  const [showTrackDialog, setShowTrackDialog] = useState(false);
  const [showSleepDialog, setShowSleepDialog] = useState(false);
  const [isScrubbingBar, setIsScrubbingBar] = useState(false);
  const [scrubMs, setScrubMs] = useState(0);

  const [subtitleTracks, setSubtitleTracks] = useState<MediaTrackOption[]>([]);
  const [activeSubtitleId, setActiveSubtitleId] = useState<string | null>(null);
  const [pinchScale, setPinchScale] = useState(1);

  // Gesture HUD state
  const [dragMode, setDragMode] = useState<DragMode>('NONE');
  const [previewSeekMs, setPreviewSeekMs] = useState(0);
  const [dragSeekDeltaMs, setDragSeekDeltaMs] = useState(0);

  const gestureRef = useRef<{
    active: boolean;
    pointerId: number | null;
    startX: number;
    startY: number;
    mode: DragMode;
    startPosMs: number;
    previewSeekMs: number;
    startBrightness: number;
    startVolume: number;
    moved: boolean;
  }>({
    active: false,
    pointerId: null,
    startX: 0,
    startY: 0,
    mode: 'NONE',
    startPosMs: 0,
    previewSeekMs: 0,
    startBrightness: 100,
    startVolume: 100,
    moved: false,
  });

  const singleTapTimerRef = useRef<number | null>(null);
  const lastTapTimeRef = useRef(0);
  const pinchRef = useRef<{ initialDist: number | null; initialScale: number }>({
    initialDist: null,
    initialScale: 1,
  });

  const hiddenSet = useMemo(() => new Set(preferences.hiddenButtons), [preferences.hiddenButtons]);
  const isBtnVisible = useCallback(
    (btn: PlayerControlButton) => !hiddenSet.has(btn),
    [hiddenSet]
  );

  const allTopTools = useMemo<TopToolItem[]>(() => {
    const list: TopToolItem[] = [];
    if (isBtnVisible(PlayerControlButton.ZOOM)) list.push('ZOOM');
    if (isBtnVisible(PlayerControlButton.MUTE)) list.push('MUTE');
    if (isBtnVisible(PlayerControlButton.ROTATE)) list.push('ROTATE');
    if (isBtnVisible(PlayerControlButton.SPEED)) list.push('SPEED');
    if (isBtnVisible(PlayerControlButton.LOOP)) list.push('LOOP');
    if (isBtnVisible(PlayerControlButton.SLEEP)) list.push('SLEEP');
    if (isBtnVisible(PlayerControlButton.BG_PLAY)) list.push('BG_PLAY');
    if (isBtnVisible(PlayerControlButton.TRACKS)) list.push('TRACKS');
    return list;
  }, [isBtnVisible]);

  const displayedTools = allTopTools.slice(0, 4);
  const overflowTools = allTopTools.slice(4);

  const ensureAudioBoostGraph = useCallback(() => {
    const video = videoRef.current;
    if (!video || sourceConnectedRef.current) return;
    try {
      const AudioCtx =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      const src = ctx.createMediaElementSource(video);
      const gain = ctx.createGain();
      src.connect(gain);
      gain.connect(ctx.destination);
      audioCtxRef.current = ctx;
      gainNodeRef.current = gain;
      sourceConnectedRef.current = true;
    } catch {
      // Fallback to native HTMLVideoElement volume
    }
  }, []);

  const applyVolume = useCallback(
    (nextPct: number) => {
      const clamped = Math.max(0, Math.min(200, Math.round(nextPct)));
      if (clamped > 0) setLastNonZeroVolume(clamped);
      setVolumePercent(clamped);
      setIsMuted(clamped === 0);

      const video = videoRef.current;
      if (!video) return;

      if (clamped > 100) {
        ensureAudioBoostGraph();
      }
      if (audioCtxRef.current?.state === 'suspended') {
        void audioCtxRef.current.resume().catch(() => {});
      }

      if (gainNodeRef.current) {
        video.muted = clamped === 0;
        video.volume = 1;
        gainNodeRef.current.gain.value = clamped === 0 ? 0 : clamped / 100;
      } else {
        video.muted = clamped === 0;
        video.volume = Math.min(1, clamped / 100);
      }
    },
    [ensureAudioBoostGraph]
  );

  // Sync volume & playback rate when video element updates
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    video.playbackRate = playbackSpeed;
  }, [playbackSpeed, currentIndex]);

  // Load saved resume position and mark played on item change
  useEffect(() => {
    if (!currentEntry) return;
    onMarkPlayed(currentEntry.uriString);

    const saved = historyByUri[currentEntry.uriString];
    setUseSoftwareDecoder(saved?.useSoftwareDecoder ?? preferences.defaultSoftwareDecoder);
    const nextSpeed = saved?.playbackSpeed ?? 1.0;
    setPlaybackSpeed(nextSpeed);

    const video = videoRef.current;
    if (!video) return;

    const resumeTargetMs =
      initialPositionMs !== undefined && currentIndex === initialIndex
        ? initialPositionMs
        : saved &&
            saved.positionMs > 1500 &&
            saved.durationMs > 0 &&
            saved.positionMs < saved.durationMs - 1500
          ? saved.positionMs
          : 0;

    const handleLoadedMetadata = () => {
      const dur =
        Number.isFinite(video.duration) && video.duration > 0
          ? Math.round(video.duration * 1000)
          : 0;
      setDurationMs(dur);
      video.playbackRate = nextSpeed;
      if (resumeTargetMs > 0 && dur > 0 && resumeTargetMs < dur - 500) {
        video.currentTime = resumeTargetMs / 1000;
        setCurrentPosMs(resumeTargetMs);
        setStatusBanner(`Resumed from ${formatDuration(resumeTargetMs)}`);
      }
      void video.play().catch(() => {
        setIsPlaying(false);
      });
    };

    if (video.readyState >= 1) {
      handleLoadedMetadata();
    } else {
      video.addEventListener('loadedmetadata', handleLoadedMetadata, { once: true });
    }
    return () => {
      video.removeEventListener('loadedmetadata', handleLoadedMetadata);
    };
  }, [currentEntry?.uriString]); // eslint-disable-line react-hooks/exhaustive-deps

  // Persist snapshot helper
  const persistCurrentSnapshot = useCallback(() => {
    const video = videoRef.current;
    if (!video || !currentEntry) return;
    const dur =
      Number.isFinite(video.duration) && video.duration > 0
        ? Math.round(video.duration * 1000)
        : durationMs;
    const pos = Math.round(video.currentTime * 1000);
    if (dur > 0) {
      onSavePlaybackState(
        currentEntry.uriString,
        currentEntry.title,
        pos,
        dur,
        playbackSpeed,
        useSoftwareDecoder
      );
    }
  }, [currentEntry, durationMs, onSavePlaybackState, playbackSpeed, useSoftwareDecoder]);

  // Periodic save every 3s
  useEffect(() => {
    const interval = window.setInterval(() => {
      persistCurrentSnapshot();
    }, 3000);
    return () => {
      window.clearInterval(interval);
    };
  }, [persistCurrentSnapshot]);

  // Auto-hide controls after 3.5s
  useEffect(() => {
    if (!controlsVisible || (!isPlaying && !isLocked)) return;
    if (showSpeedPicker || showTrackDialog || showSleepDialog) return;
    const timer = window.setTimeout(() => {
      setControlsVisible(false);
    }, 3500);
    return () => window.clearTimeout(timer);
  }, [controlsVisible, isPlaying, isLocked, showSpeedPicker, showTrackDialog, showSleepDialog]);

  // Auto-clear status banner
  useEffect(() => {
    if (!statusBanner) return;
    const timer = window.setTimeout(() => setStatusBanner(null), 2200);
    return () => window.clearTimeout(timer);
  }, [statusBanner]);

  // Sleep timer countdown
  useEffect(() => {
    if (sleepMinutesRemaining === null) return;
    if (sleepMinutesRemaining <= 0) {
      videoRef.current?.pause();
      setIsPlaying(false);
      setSleepMinutesRemaining(null);
      setStatusBanner('Sleep timer paused playback');
      return;
    }
    const timer = window.setTimeout(() => {
      setSleepMinutesRemaining((prev) => (prev !== null ? Math.max(0, prev - 1) : null));
    }, 60_000);
    return () => window.clearTimeout(timer);
  }, [sleepMinutesRemaining]);

  // Auto PiP on tab visibility hidden if enabled
  useEffect(() => {
    if (!preferences.autoPipOnHome) return;
    const handleVisibility = () => {
      if (
        document.hidden &&
        videoRef.current &&
        !videoRef.current.paused &&
        !preferences.alwaysBackgroundPlay
      ) {
        if (document.pictureInPictureEnabled && !document.pictureInPictureElement) {
          void videoRef.current.requestPictureInPicture().catch(() => {});
        }
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, [preferences.autoPipOnHome, preferences.alwaysBackgroundPlay]);

  // Clean up subtitle blob URLs and audio context on unmount
  useEffect(() => {
    return () => {
      subtitleTracks.forEach((tr) => {
        if (tr.srcUrl) URL.revokeObjectURL(tr.srcUrl);
      });
      if (audioCtxRef.current) {
        void audioCtxRef.current.close().catch(() => {});
      }
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handlePlayPause = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    if (audioCtxRef.current?.state === 'suspended') {
      void audioCtxRef.current.resume().catch(() => {});
    }
    if (video.paused) {
      void video.play().catch(() => {});
    } else {
      video.pause();
    }
    setControlsVisible(true);
  }, []);

  const handleSeekTo = useCallback(
    (ms: number) => {
      const video = videoRef.current;
      if (!video) return;
      const dur =
        Number.isFinite(video.duration) && video.duration > 0 ? video.duration * 1000 : durationMs;
      const clamped = Math.max(0, Math.min(ms, dur || 0));
      video.currentTime = clamped / 1000;
      setCurrentPosMs(clamped);
    },
    [durationMs]
  );

  const handleSeekBy = useCallback(
    (deltaMs: number) => {
      const video = videoRef.current;
      if (!video) return;
      const dur =
        Number.isFinite(video.duration) && video.duration > 0 ? video.duration * 1000 : durationMs;
      const next = Math.max(0, Math.min(video.currentTime * 1000 + deltaMs, dur || 0));
      video.currentTime = next / 1000;
      setCurrentPosMs(next);
      setStatusBanner(formatSignedDelta(deltaMs));
    },
    [durationMs]
  );

  const handlePrevious = useCallback(() => {
    if (currentIndex <= 0) return;
    persistCurrentSnapshot();
    setCurrentIndex((i) => Math.max(0, i - 1));
  }, [currentIndex, persistCurrentSnapshot]);

  const handleNext = useCallback(() => {
    if (currentIndex >= playlist.length - 1) return;
    persistCurrentSnapshot();
    setCurrentIndex((i) => Math.min(playlist.length - 1, i + 1));
  }, [currentIndex, playlist.length, persistCurrentSnapshot]);

  const handleBackClick = useCallback(() => {
    persistCurrentSnapshot();
    const video = videoRef.current;
    if (isBackgroundPlayEnabled && video && !video.paused && currentEntry) {
      onBack({
        uriString: currentEntry.uriString,
        title: currentEntry.title,
        currentTimeSec: video.currentTime,
        playbackRate: playbackSpeed,
        volumePercent,
      });
    } else {
      video?.pause();
      onBack();
    }
  }, [
    persistCurrentSnapshot,
    isBackgroundPlayEnabled,
    currentEntry,
    playbackSpeed,
    volumePercent,
    onBack,
  ]);

  const handleToggleMute = useCallback(() => {
    if (isMuted || volumePercent === 0) {
      applyVolume(Math.max(50, lastNonZeroVolume));
      setStatusBanner('Audio Unmuted');
    } else {
      applyVolume(0);
      setStatusBanner('Audio Muted');
    }
  }, [isMuted, volumePercent, lastNonZeroVolume, applyVolume]);

  const handleCycleQuickZoom = useCallback(() => {
    const idx = ZOOM_STEPS.findIndex((s) => Math.abs(s - pinchScale) < 0.08);
    const next = ZOOM_STEPS[(idx + 1) % ZOOM_STEPS.length];
    setPinchScale(next);
    setStatusBanner(next === 1 ? 'Zoom Reset (1.0x)' : `Quick Zoom ${next}x`);
  }, [pinchScale]);

  const handleCycleAspectMode = useCallback(() => {
    const order = [PlayerAspectMode.FIT, PlayerAspectMode.FILL, PlayerAspectMode.ZOOM];
    const next = order[(order.indexOf(aspectMode) + 1) % order.length];
    setAspectMode(next);
    setPinchScale(1);
    setStatusBanner(`Screen: ${PLAYER_ASPECT_LABELS[next]}`);
  }, [aspectMode]);

  const handleEnterPip = useCallback(async () => {
    const video = videoRef.current;
    if (!video) return;
    try {
      if (document.pictureInPictureElement) {
        await document.exitPictureInPicture();
      } else if (document.pictureInPictureEnabled) {
        await video.requestPictureInPicture();
      } else {
        setStatusBanner('Picture-in-Picture not supported in this browser');
      }
    } catch {
      setStatusBanner('Could not enter Picture-in-Picture');
    }
  }, []);

  const handleToggleDecoder = useCallback(() => {
    const nextSw = !useSoftwareDecoder;
    setUseSoftwareDecoder(nextSw);
    onSetDefaultSoftwareDecoder(nextSw);
    setStatusBanner(nextSw ? 'Switched to SW Decoder' : 'Switched to HW Decoder');
  }, [useSoftwareDecoder, onSetDefaultSoftwareDecoder]);

  const handleToggleRotation = useCallback(async () => {
    const next = !isRotated;
    setIsRotated(next);
    if (next && containerRef.current && document.fullscreenElement === null) {
      try {
        await containerRef.current.requestFullscreen();
      } catch {
        // ignore if fullscreen blocked
      }
    } else if (!next && document.fullscreenElement) {
      try {
        await document.exitFullscreen();
      } catch {
        // ignore
      }
    }
    setStatusBanner(next ? 'Orientation: Landscape' : 'Orientation: Auto');
  }, [isRotated]);

  const handleCycleRepeat = useCallback(() => {
    const next =
      repeatMode === RepeatMode.OFF
        ? RepeatMode.ONE
        : repeatMode === RepeatMode.ONE
          ? RepeatMode.ALL
          : RepeatMode.OFF;
    setRepeatMode(next);
    setStatusBanner(
      next === RepeatMode.ONE
        ? 'Repeat: Current Video'
        : next === RepeatMode.ALL
          ? 'Repeat: Playlist'
          : 'Repeat: Off'
    );
  }, [repeatMode]);

  const handleToggleLock = useCallback(() => {
    setIsLocked((prev) => {
      const next = !prev;
      setControlsVisible(true);
      setStatusBanner(next ? 'Controls Locked' : 'Controls Unlocked');
      return next;
    });
  }, []);

  // Keyboard shortcuts
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (showTrackDialog || showSleepDialog) return;
      if (e.key === 'Escape') {
        handleBackClick();
      } else if (e.key === ' ' || e.key === 'k') {
        e.preventDefault();
        if (!isLocked) handlePlayPause();
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        if (!isLocked) handleSeekBy(-10_000);
      } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        if (!isLocked) handleSeekBy(10_000);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        if (!isLocked) {
          const next = Math.min(200, volumePercent + 10);
          applyVolume(next);
          setStatusBanner(next > 100 ? `BOOST ${next}%` : `Volume ${next}%`);
        }
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        if (!isLocked) {
          const next = Math.max(0, volumePercent - 10);
          applyVolume(next);
          setStatusBanner(`Volume ${next}%`);
        }
      } else if (e.key === 'm' || e.key === 'M') {
        if (!isLocked) handleToggleMute();
      }
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [
    showTrackDialog,
    showSleepDialog,
    isLocked,
    volumePercent,
    handleBackClick,
    handlePlayPause,
    handleSeekBy,
    applyVolume,
    handleToggleMute,
  ]);

  // Tap & Drag Gesture handlers on the video surface
  const triggerTap = useCallback(
    (clientX: number) => {
      const rect = containerRef.current?.getBoundingClientRect();
      const width = rect?.width || window.innerWidth;
      const leftHalf = clientX - (rect?.left ?? 0) < width / 2;
      const now = Date.now();

      if (now - lastTapTimeRef.current < 260) {
        if (singleTapTimerRef.current) {
          window.clearTimeout(singleTapTimerRef.current);
          singleTapTimerRef.current = null;
        }
        lastTapTimeRef.current = 0;
        if (!isLocked) {
          switch (preferences.doubleClickAction) {
            case DoubleClickAction.PLAY_PAUSE:
              handlePlayPause();
              break;
            case DoubleClickAction.SEEK_10S:
              handleSeekBy(leftHalf ? -10_000 : 10_000);
              break;
            case DoubleClickAction.SEEK_20S:
              handleSeekBy(leftHalf ? -20_000 : 20_000);
              break;
            case DoubleClickAction.NONE:
              break;
          }
        }
        return;
      }

      lastTapTimeRef.current = now;
      singleTapTimerRef.current = window.setTimeout(() => {
        singleTapTimerRef.current = null;
        setShowSpeedPicker(false);
        if (isLocked) {
          setControlsVisible((v) => !v);
        } else {
          switch (preferences.singleClickAction) {
            case SingleClickAction.TOGGLE_CONTROLS:
              setControlsVisible((v) => !v);
              break;
            case SingleClickAction.PLAY_PAUSE:
              handlePlayPause();
              break;
            case SingleClickAction.TOGGLE_MUTE:
              handleToggleMute();
              break;
            case SingleClickAction.NONE:
              break;
          }
        }
      }, 240);
    },
    [
      isLocked,
      preferences.singleClickAction,
      preferences.doubleClickAction,
      handlePlayPause,
      handleSeekBy,
      handleToggleMute,
    ]
  );

  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (e.button !== 0 && e.pointerType === 'mouse') return;
    if (audioCtxRef.current?.state === 'suspended') {
      void audioCtxRef.current.resume().catch(() => {});
    }
    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {
      // ignore if unsupported
    }
    gestureRef.current = {
      active: true,
      pointerId: e.pointerId,
      startX: e.clientX,
      startY: e.clientY,
      mode: 'NONE',
      startPosMs: currentPosMs,
      previewSeekMs: currentPosMs,
      startBrightness: brightnessPercent,
      startVolume: volumePercent,
      moved: false,
    };
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    const g = gestureRef.current;
    if (!g.active || g.pointerId !== e.pointerId) return;
    if (isLocked || !preferences.enableSwipeGestures || preferences.oneFingerGesture === 'None') {
      return;
    }

    const rect = containerRef.current?.getBoundingClientRect();
    const width = Math.max(300, rect?.width ?? window.innerWidth);
    const dragHeight = Math.max(180, (rect?.height ?? window.innerHeight) * 0.45);
    const dx = e.clientX - g.startX;
    const dyTotal = e.clientY - g.startY;

    if (Math.abs(dx) > 8 || Math.abs(dyTotal) > 8) {
      g.moved = true;
    }

    if (g.mode === 'NONE') {
      if (Math.abs(dx) > 16 && Math.abs(dx) > Math.abs(dyTotal) * 1.15) {
        g.mode = 'SEEK';
        setDragMode('SEEK');
      } else if (
        preferences.oneFingerGesture !== 'Seek & Controls' &&
        Math.abs(dyTotal) > 14 &&
        Math.abs(dyTotal) > Math.abs(dx) * 1.15
      ) {
        const isLeft = g.startX - (rect?.left ?? 0) < width / 2;
        g.mode = isLeft ? 'BRIGHTNESS' : 'VOLUME';
        setDragMode(g.mode);
      }
    }

    if (g.mode === 'SEEK') {
      const realDur = Math.max(1000, durationMs);
      let rawDelta: number;
      if (preferences.gestureSeekStepSeconds <= 0) {
        const spanMs = Math.max(15_000, Math.min(120_000, realDur));
        rawDelta = Math.round((dx / width) * spanMs);
      } else {
        const stepMs = preferences.gestureSeekStepSeconds * 1000;
        const steps = Math.trunc(dx / (width * 0.14));
        rawDelta = steps * stepMs;
      }
      const nextPreview = Math.max(0, Math.min(realDur, g.startPosMs + rawDelta));
      g.previewSeekMs = nextPreview;
      setDragSeekDeltaMs(rawDelta);
      setPreviewSeekMs(nextPreview);
    } else if (g.mode === 'BRIGHTNESS') {
      const deltaPct = Math.round((-dyTotal / dragHeight) * 100);
      const nextBrightness = Math.max(10, Math.min(100, g.startBrightness + deltaPct));
      setBrightnessPercent(nextBrightness);
    } else if (g.mode === 'VOLUME') {
      const deltaPct = Math.round((-dyTotal / dragHeight) * 200);
      const nextVol = Math.max(0, Math.min(200, g.startVolume + deltaPct));
      applyVolume(nextVol);
    }
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    const g = gestureRef.current;
    if (!g.active || g.pointerId !== e.pointerId) return;
    g.active = false;
    try {
      if (e.currentTarget.hasPointerCapture(e.pointerId)) {
        e.currentTarget.releasePointerCapture(e.pointerId);
      }
    } catch {
      // ignore
    }
    if (g.mode === 'SEEK') {
      handleSeekTo(g.previewSeekMs);
    } else if (!g.moved) {
      triggerTap(e.clientX);
    }
    g.mode = 'NONE';
    setDragMode('NONE');
  };

  // Multi-touch 2-finger pinch & 3-finger tap
  const handleTouchStart = (e: React.TouchEvent<HTMLDivElement>) => {
    if (e.touches.length === 3 && !isLocked) {
      if (preferences.threeFingerGesture === 'Play / Pause') handlePlayPause();
      else if (preferences.threeFingerGesture === 'Lock / Unlock') handleToggleLock();
      else if (preferences.threeFingerGesture === 'Mute Audio') handleToggleMute();
    } else if (
      e.touches.length === 2 &&
      !isLocked &&
      preferences.twoFingerGesture === 'Zoom / Scale'
    ) {
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      pinchRef.current = { initialDist: Math.hypot(dx, dy), initialScale: pinchScale };
    }
  };

  const handleTouchMove = (e: React.TouchEvent<HTMLDivElement>) => {
    if (
      e.touches.length === 2 &&
      pinchRef.current.initialDist &&
      !isLocked &&
      preferences.twoFingerGesture === 'Zoom / Scale'
    ) {
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      const dist = Math.hypot(dx, dy);
      const scale = Math.max(
        0.75,
        Math.min(2.5, pinchRef.current.initialScale * (dist / pinchRef.current.initialDist))
      );
      setPinchScale(scale);
    }
  };

  const handleImportSubtitleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const text = await file.text();
    const vttUrl = createSubtitleBlobUrl(text, file.name);
    const id = `sub_${Date.now()}`;
    const newTrack: MediaTrackOption = {
      id,
      label: file.name,
      language: 'en',
      isSelected: true,
      srcUrl: vttUrl,
    };
    setSubtitleTracks((prev) => [
      ...prev.map((t) => ({ ...t, isSelected: false })),
      newTrack,
    ]);
    setActiveSubtitleId(id);
    setStatusBanner('Subtitle attached');
  };

  const selectSubtitleTrack = (id: string | null) => {
    setActiveSubtitleId(id);
    setSubtitleTracks((prev) => prev.map((t) => ({ ...t, isSelected: t.id === id })));
    const video = videoRef.current;
    if (video) {
      for (let i = 0; i < video.textTracks.length; i++) {
        const track = video.textTracks[i];
        track.mode =
          id && track.label === subtitleTracks.find((s) => s.id === id)?.label
            ? 'showing'
            : 'disabled';
      }
    }
    setStatusBanner(
      id ? `Subtitle: ${subtitleTracks.find((s) => s.id === id)?.label ?? 'On'}` : 'Subtitles Off'
    );
  };

  const handleVideoEnded = () => {
    persistCurrentSnapshot();
    if (repeatMode === RepeatMode.ONE) {
      if (videoRef.current) {
        videoRef.current.currentTime = 0;
        void videoRef.current.play().catch(() => {});
      }
    } else if (currentIndex < playlist.length - 1) {
      setCurrentIndex((i) => i + 1);
    } else if (repeatMode === RepeatMode.ALL && playlist.length > 0) {
      setCurrentIndex(0);
    } else {
      setIsPlaying(false);
      setControlsVisible(true);
    }
  };

  const scrubFlatBar = (clientX: number) => {
    const bar = flatBarRef.current;
    if (!bar) return;
    const rect = bar.getBoundingClientRect();
    const frac = Math.max(0, Math.min(1, (clientX - rect.left) / Math.max(1, rect.width)));
    const targetMs = Math.round(frac * Math.max(1, durationMs));
    setScrubMs(targetMs);
    return targetMs;
  };

  const objectFitClass =
    aspectMode === PlayerAspectMode.FIT
      ? 'object-contain'
      : aspectMode === PlayerAspectMode.FILL
        ? 'object-fill'
        : 'object-cover';

  const activePos = isScrubbingBar ? scrubMs : currentPosMs;
  const dur = Math.max(1, durationMs);
  const progressPct = Math.min(100, Math.max(0, (activePos / dur) * 100));
  const brightnessDimOpacity = Math.max(0, Math.min(0.88, (100 - brightnessPercent) / 100));

  return (
    <div
      ref={containerRef}
      className="fixed inset-0 z-50 flex select-none items-center justify-center overflow-hidden bg-black text-white"
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
    >
      {/* Hidden Subtitle File Picker */}
      <input
        ref={subtitleInputRef}
        type="file"
        accept=".srt,.vtt,.ass,.ssa,text/vtt"
        className="hidden"
        onChange={handleImportSubtitleFile}
      />

      {/* Video Surface */}
      <video
        ref={videoRef}
        key={currentEntry?.uriString}
        src={currentEntry ? resolvePlayableUrl(currentEntry.uriString) : undefined}
        playsInline
        preload="auto"
        className={`h-full w-full transition-transform duration-150 ${objectFitClass}`}
        style={{
          filter: `brightness(${0.35 + (brightnessPercent / 100) * 0.65})`,
          transform: `${isRotated ? 'rotate(90deg) ' : ''}scale(${pinchScale})`,
        }}
        onTimeUpdate={(e) => {
          const v = e.currentTarget;
          if (!isScrubbingBar) {
            setCurrentPosMs(Math.round(v.currentTime * 1000));
          }
          if (Number.isFinite(v.duration) && v.duration > 0) {
            setDurationMs(Math.round(v.duration * 1000));
          }
        }}
        onPlay={() => setIsPlaying(true)}
        onPause={() => {
          setIsPlaying(false);
          persistCurrentSnapshot();
        }}
        onEnded={handleVideoEnded}
      >
        {subtitleTracks.map(
          (tr) =>
            tr.srcUrl && (
              <track
                key={tr.id}
                kind="subtitles"
                label={tr.label}
                src={tr.srcUrl}
                default={tr.id === activeSubtitleId}
              />
            )
        )}
      </video>

      {/* Hardware-safe Brightness Dimmer Scrim */}
      <div
        className="pointer-events-none absolute inset-0 bg-black transition-opacity duration-75"
        style={{ opacity: brightnessDimOpacity }}
      />

      {/* Gesture Capture Layer */}
      <div
        className="absolute inset-0 touch-none"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
      />

      {/* Controls Overlay */}
      <div
        className={`pointer-events-none absolute inset-0 flex flex-col justify-between transition-opacity duration-200 ${
          controlsVisible && !isLocked ? 'opacity-100' : 'opacity-0'
        }`}
      >
        {/* Top Gradient Bar */}
        <div
          className={`bg-gradient-to-b from-black/85 via-black/40 to-transparent px-3 py-3 md:px-5 ${
            controlsVisible && !isLocked ? 'pointer-events-auto' : 'pointer-events-none'
          }`}
          onPointerDown={(e) => e.stopPropagation()}
        >
          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={handleBackClick}
              aria-label="Turn off video and return"
              title="Turn off video and return"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white/10 text-white transition-colors hover:bg-white/25 active:scale-95"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>
            <h1 className="min-w-0 flex-1 truncate text-[15px] font-semibold">
              {currentEntry?.title ?? 'Video'}
            </h1>
            {volumePercent > 100 && (
              <span className="shrink-0 rounded-md bg-[var(--boost-orange)]/25 px-2 py-1 text-[11px] font-bold text-[var(--boost-orange)] tabular-nums">
                BOOST {volumePercent}%
              </span>
            )}
            {isBtnVisible(PlayerControlButton.DECODER) && (
              <button
                type="button"
                onClick={handleToggleDecoder}
                className={`flex shrink-0 items-center gap-1 rounded-lg px-2.5 py-1.5 text-[11px] font-bold transition-colors ${
                  useSoftwareDecoder
                    ? 'bg-[#F59E0B]/25 text-[#F59E0B]'
                    : 'bg-[#2DD4BF]/20 text-[#2DD4BF]'
                }`}
              >
                <Cpu className="h-3.5 w-3.5" />
                <span>{useSoftwareDecoder ? 'SW' : 'HW'}</span>
              </button>
            )}
          </div>

          {allTopTools.length > 0 && (
            <div className="mt-2 flex items-center gap-1.5 overflow-x-auto pb-1">
              {displayedTools.map((tool) => (
                <TopToolChip
                  key={tool}
                  tool={tool}
                  playbackSpeed={playbackSpeed}
                  repeatMode={repeatMode}
                  isRotated={isRotated}
                  isMuted={isMuted}
                  pinchScale={pinchScale}
                  sleepMinutesRemaining={sleepMinutesRemaining}
                  isBackgroundPlayEnabled={isBackgroundPlayEnabled}
                  showSpeedPicker={showSpeedPicker}
                  onZoomClick={handleCycleQuickZoom}
                  onRotate={handleToggleRotation}
                  onSpeedClick={() => setShowSpeedPicker((s) => !s)}
                  onLoopClick={handleCycleRepeat}
                  onMuteClick={handleToggleMute}
                  onSleepClick={() => setShowSleepDialog(true)}
                  onBgPlayClick={() => {
                    const next = !isBackgroundPlayEnabled;
                    setIsBackgroundPlayEnabled(next);
                    onSetAlwaysBackgroundPlay(next);
                    setStatusBanner(
                      next ? 'Background Audio Play Enabled' : 'Background Audio Play Disabled'
                    );
                  }}
                  onTracksClick={() => setShowTrackDialog(true)}
                />
              ))}

              {overflowTools.length > 0 && (
                <>
                  <button
                    type="button"
                    onClick={() => setShowMoreTools((s) => !s)}
                    aria-label="More tools"
                    className={`flex h-[42px] shrink-0 items-center justify-center rounded-xl px-2.5 transition-colors ${
                      showMoreTools ? 'bg-[#2DD4BF]/25 text-[#2DD4BF]' : 'bg-white/10 text-white'
                    }`}
                  >
                    <ChevronRight
                      className={`h-4 w-4 transition-transform ${showMoreTools ? 'rotate-180' : ''}`}
                    />
                  </button>
                  {showMoreTools &&
                    overflowTools.map((tool) => (
                      <TopToolChip
                        key={tool}
                        tool={tool}
                        playbackSpeed={playbackSpeed}
                        repeatMode={repeatMode}
                        isRotated={isRotated}
                        isMuted={isMuted}
                        pinchScale={pinchScale}
                        sleepMinutesRemaining={sleepMinutesRemaining}
                        isBackgroundPlayEnabled={isBackgroundPlayEnabled}
                        showSpeedPicker={showSpeedPicker}
                        onZoomClick={handleCycleQuickZoom}
                        onRotate={handleToggleRotation}
                        onSpeedClick={() => setShowSpeedPicker((s) => !s)}
                        onLoopClick={handleCycleRepeat}
                        onMuteClick={handleToggleMute}
                        onSleepClick={() => setShowSleepDialog(true)}
                        onBgPlayClick={() => {
                          const next = !isBackgroundPlayEnabled;
                          setIsBackgroundPlayEnabled(next);
                          onSetAlwaysBackgroundPlay(next);
                          setStatusBanner(
                            next
                              ? 'Background Audio Play Enabled'
                              : 'Background Audio Play Disabled'
                          );
                        }}
                        onTracksClick={() => setShowTrackDialog(true)}
                      />
                    ))}
                </>
              )}
            </div>
          )}

          {showSpeedPicker && (
            <div className="mt-2 inline-flex items-center gap-1 rounded-xl border border-white/15 bg-[#111827]/95 p-1">
              {[0.5, 0.75, 1.0, 1.25, 1.5, 2.0].map((spd) => {
                const active = Math.abs(playbackSpeed - spd) < 0.01;
                return (
                  <button
                    key={spd}
                    type="button"
                    onClick={() => {
                      setPlaybackSpeed(spd);
                      if (videoRef.current) videoRef.current.playbackRate = spd;
                      setStatusBanner(`Speed ${spd}x`);
                      setShowSpeedPicker(false);
                    }}
                    className={`rounded-lg px-2.5 py-1.5 text-xs font-semibold transition-colors ${
                      active ? 'bg-[#2DD4BF] text-[#03201D]' : 'text-white hover:bg-white/10'
                    }`}
                  >
                    {spd}x
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Bottom Gradient Controls */}
        <div
          className={`bg-gradient-to-t from-black/90 via-black/55 to-transparent px-4 py-4 md:px-6 ${
            controlsVisible && !isLocked ? 'pointer-events-auto' : 'pointer-events-none'
          }`}
          onPointerDown={(e) => e.stopPropagation()}
        >
          {/* Scrubber Bar (Material vs Flat style) */}
          <div className="flex items-center gap-3">
            <span className="font-mono-label w-12 text-[11px] text-white">
              {formatDuration(activePos)}
            </span>

            {preferences.progressBarStyle === 'flat' ? (
              <div
                ref={flatBarRef}
                role="slider"
                aria-label="Seek timeline"
                aria-valuemin={0}
                aria-valuemax={dur}
                aria-valuenow={activePos}
                tabIndex={0}
                onPointerDown={(e) => {
                  try {
                    e.currentTarget.setPointerCapture(e.pointerId);
                  } catch {
                    // ignore
                  }
                  setIsScrubbingBar(true);
                  scrubFlatBar(e.clientX);
                }}
                onPointerMove={(e) => {
                  if (!isScrubbingBar) return;
                  scrubFlatBar(e.clientX);
                }}
                onPointerUp={(e) => {
                  if (!isScrubbingBar) return;
                  const target = scrubFlatBar(e.clientX);
                  setIsScrubbingBar(false);
                  if (target !== undefined) handleSeekTo(target);
                }}
                className="group relative flex h-6 flex-1 cursor-pointer items-center touch-none"
              >
                <div className="h-[3px] w-full overflow-hidden bg-white/25 transition-all group-hover:h-[5px]">
                  <div
                    className="h-full bg-[#2DD4BF]"
                    style={{ width: `${progressPct}%` }}
                  />
                </div>
              </div>
            ) : (
              <div className="relative flex h-6 flex-1 items-center">
                <input
                  type="range"
                  min={0}
                  max={dur}
                  value={activePos}
                  aria-label="Seek timeline"
                  onChange={(e) => {
                    const val = Number(e.target.value);
                    setIsScrubbingBar(true);
                    setScrubMs(val);
                  }}
                  onMouseUp={(e) => {
                    const val = Number((e.target as HTMLInputElement).value);
                    setIsScrubbingBar(false);
                    handleSeekTo(val);
                  }}
                  onTouchEnd={(e) => {
                    const val = Number((e.target as HTMLInputElement).value);
                    setIsScrubbingBar(false);
                    handleSeekTo(val);
                  }}
                  style={{
                    background: `linear-gradient(to right, #2DD4BF ${progressPct}%, rgba(255,255,255,0.25) ${progressPct}%)`,
                  }}
                  className="h-2 w-full cursor-pointer appearance-none rounded-full accent-[#2DD4BF] [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-[#2DD4BF] [&::-webkit-slider-thumb]:shadow-[0_0_0_4px_rgba(45,212,191,0.25)]"
                />
              </div>
            )}

            <span className="font-mono-label w-12 text-right text-[11px] text-white/85">
              {formatDuration(durationMs)}
            </span>
          </div>

          {/* Primary Transport Row */}
          <div className="mt-3 flex items-center justify-between">
            <div className="flex w-24 items-center justify-start">
              {isBtnVisible(PlayerControlButton.LOCK) && (
                <button
                  type="button"
                  onClick={handleToggleLock}
                  aria-label="Lock"
                  className="flex h-10 w-10 items-center justify-center rounded-full text-white transition-colors hover:bg-white/10"
                >
                  <Lock className="h-5 w-5" />
                </button>
              )}
            </div>

            <div className="flex items-center gap-3.5">
              {isBtnVisible(PlayerControlButton.PREV_NEXT) && (
                <button
                  type="button"
                  disabled={currentIndex <= 0}
                  onClick={handlePrevious}
                  aria-label="Previous"
                  className="flex h-11 w-11 items-center justify-center rounded-full bg-black/55 text-white transition-opacity disabled:opacity-35 hover:bg-white/15"
                >
                  <SkipBack className="h-5 w-5" />
                </button>
              )}

              <button
                type="button"
                onClick={handlePlayPause}
                aria-label={isPlaying ? 'Pause' : 'Play'}
                className="flex h-14 w-14 items-center justify-center rounded-full bg-[#2DD4BF] text-[#03201D] shadow-lg transition-transform active:scale-95"
              >
                {isPlaying ? (
                  <Pause className="h-7 w-7 fill-current" />
                ) : (
                  <Play className="h-7 w-7 translate-x-0.5 fill-current" />
                )}
              </button>

              {isBtnVisible(PlayerControlButton.PREV_NEXT) && (
                <button
                  type="button"
                  disabled={currentIndex >= playlist.length - 1}
                  onClick={handleNext}
                  aria-label="Next"
                  className="flex h-11 w-11 items-center justify-center rounded-full bg-black/55 text-white transition-opacity disabled:opacity-35 hover:bg-white/15"
                >
                  <SkipForward className="h-5 w-5" />
                </button>
              )}
            </div>

            <div className="flex items-center justify-end gap-1">
              {isBtnVisible(PlayerControlButton.FIT) && (
                <button
                  type="button"
                  onClick={handleCycleAspectMode}
                  aria-label="Aspect Mode"
                  title={`Screen Mode: ${PLAYER_ASPECT_LABELS[aspectMode]}`}
                  className={`flex h-10 w-10 items-center justify-center rounded-full transition-colors hover:bg-white/10 ${
                    aspectMode !== PlayerAspectMode.FIT ? 'text-[#2DD4BF]' : 'text-white'
                  }`}
                >
                  <FitScreenIcon className="h-5 w-5" />
                </button>
              )}
              {isBtnVisible(PlayerControlButton.PIP) && (
                <button
                  type="button"
                  onClick={handleEnterPip}
                  aria-label="PiP"
                  title="Picture-in-Picture"
                  className="flex h-10 w-10 items-center justify-center rounded-full text-white transition-colors hover:bg-white/10"
                >
                  <PictureInPicture2 className="h-5 w-5" />
                </button>
              )}
              <button
                type="button"
                onClick={handleBackClick}
                aria-label="Back"
                title="Back"
                className="flex h-10 w-10 items-center justify-center rounded-full text-white transition-colors hover:bg-white/10"
              >
                <ArrowLeft className="h-5 w-5" />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Unlock Button when locked */}
      {isLocked && controlsVisible && (
        <button
          type="button"
          onClick={handleToggleLock}
          aria-label="Unlock"
          onPointerDown={(e) => e.stopPropagation()}
          className="absolute top-5 left-1/2 flex h-12 w-12 -translate-x-1/2 items-center justify-center rounded-full bg-black/75 text-[#2DD4BF] shadow-lg"
        >
          <LockOpen className="h-5 w-5" />
        </button>
      )}

      {/* Gesture HUDs */}
      {dragMode === 'SEEK' && (
        <div className="pointer-events-none absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-[#0B0E14]/85 px-6 py-3.5 text-center backdrop-blur-sm">
          <div className="font-mono-label text-xl font-bold text-[#2DD4BF]">
            {formatDuration(previewSeekMs)}
          </div>
          <div className="font-mono-label text-xs text-white">
            {formatSignedDelta(dragSeekDeltaMs)}
          </div>
        </div>
      )}

      {dragMode === 'BRIGHTNESS' && (
        <VerticalGestureHud
          side="left"
          icon={<Sun className="h-5 w-5 text-[#F59E0B]" />}
          label={`${brightnessPercent}%`}
          fraction={brightnessPercent / 100}
          barColor="#F59E0B"
        />
      )}

      {dragMode === 'VOLUME' && (
        <VerticalGestureHud
          side="right"
          icon={
            <Volume2
              className={`h-5 w-5 ${volumePercent > 100 ? 'text-[#FF6B35]' : 'text-[#2DD4BF]'}`}
            />
          }
          label={volumePercent > 100 ? `BOOST ${volumePercent}%` : `${volumePercent}%`}
          fraction={volumePercent / 200}
          barColor={volumePercent > 100 ? '#FF6B35' : '#2DD4BF'}
        />
      )}

      {/* Status Banner */}
      {statusBanner && dragMode === 'NONE' && (
        <div className="pointer-events-none absolute top-16 left-1/2 -translate-x-1/2 rounded-full bg-[#0B0E14]/85 px-4 py-2 text-xs font-medium text-[#2DD4BF] shadow-lg">
          {statusBanner}
        </div>
      )}

      {/* Audio & Subtitles Track Dialog */}
      {showTrackDialog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
          onClick={() => setShowTrackDialog(false)}
          onPointerDown={(e) => e.stopPropagation()}
        >
          <div
            className="w-full max-w-sm rounded-3xl border border-white/15 bg-[#121720] p-5 text-white shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className="text-base font-bold">Audio & Subtitles</h2>
            <div className="mt-4 max-h-80 space-y-3 overflow-y-auto pr-1">
              <div>
                <div className="mb-1.5 flex items-center justify-between text-xs font-semibold text-[#2DD4BF]">
                  <span>Volume / Audio Boost</span>
                  <span className="font-mono-label">
                    {volumePercent > 100 ? `BOOST ${volumePercent}%` : `${volumePercent}%`}
                  </span>
                </div>
                <input
                  type="range"
                  min={0}
                  max={200}
                  value={volumePercent}
                  onChange={(e) => applyVolume(Number(e.target.value))}
                  className="w-full cursor-pointer accent-[#2DD4BF]"
                />
              </div>

              <div className="h-px bg-white/10" />

              <div className="space-y-1.5">
                <div className="text-xs font-semibold text-[#2DD4BF]">Subtitles</div>
                <TrackChoiceRow
                  label="Off (Disable Subtitles)"
                  selected={activeSubtitleId === null}
                  onClick={() => {
                    selectSubtitleTrack(null);
                    setShowTrackDialog(false);
                  }}
                />
                {subtitleTracks.map((tr) => (
                  <TrackChoiceRow
                    key={tr.id}
                    label={tr.label}
                    selected={activeSubtitleId === tr.id}
                    onClick={() => {
                      selectSubtitleTrack(tr.id);
                      setShowTrackDialog(false);
                    }}
                  />
                ))}
                <button
                  type="button"
                  onClick={() => {
                    setShowTrackDialog(false);
                    subtitleInputRef.current?.click();
                  }}
                  className="mt-2 flex w-full items-center justify-center gap-2 rounded-xl border border-white/20 px-3 py-2 text-xs font-semibold text-white hover:bg-white/10"
                >
                  <FileText className="h-4 w-4" />
                  <span>Load External Subtitle (.srt / .vtt / .ass)</span>
                </button>
              </div>
            </div>
            <div className="mt-4 flex justify-end">
              <button
                type="button"
                onClick={() => setShowTrackDialog(false)}
                className="rounded-xl px-4 py-2 text-xs font-semibold text-[#2DD4BF] hover:bg-white/10"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Sleep Timer Dialog */}
      {showSleepDialog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
          onClick={() => setShowSleepDialog(false)}
          onPointerDown={(e) => e.stopPropagation()}
        >
          <div
            className="w-full max-w-xs rounded-3xl border border-white/15 bg-[#121720] p-5 text-white shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className="mb-3 text-base font-bold">Sleep Timer</h2>
            <div className="space-y-1.5">
              {[null, 15, 30, 45, 60].map((mins) => (
                <TrackChoiceRow
                  key={mins ?? 'off'}
                  label={mins === null ? 'Off' : `${mins} minutes`}
                  selected={sleepMinutesRemaining === mins}
                  onClick={() => {
                    setSleepMinutesRemaining(mins);
                    setStatusBanner(mins === null ? 'Sleep Timer Off' : `Sleep Timer: ${mins}m`);
                    setShowSleepDialog(false);
                  }}
                />
              ))}
            </div>
            <div className="mt-4 flex justify-end">
              <button
                type="button"
                onClick={() => setShowSleepDialog(false)}
                className="rounded-xl px-4 py-2 text-xs font-semibold text-[#2DD4BF] hover:bg-white/10"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function TopToolChip({
  tool,
  playbackSpeed,
  repeatMode,
  isRotated,
  isMuted,
  pinchScale,
  sleepMinutesRemaining,
  isBackgroundPlayEnabled,
  showSpeedPicker,
  onZoomClick,
  onRotate,
  onSpeedClick,
  onLoopClick,
  onMuteClick,
  onSleepClick,
  onBgPlayClick,
  onTracksClick,
}: {
  tool: TopToolItem;
  playbackSpeed: number;
  repeatMode: RepeatMode;
  isRotated: boolean;
  isMuted: boolean;
  pinchScale: number;
  sleepMinutesRemaining: number | null;
  isBackgroundPlayEnabled: boolean;
  showSpeedPicker: boolean;
  onZoomClick: () => void;
  onRotate: () => void;
  onSpeedClick: () => void;
  onLoopClick: () => void;
  onMuteClick: () => void;
  onSleepClick: () => void;
  onBgPlayClick: () => void;
  onTracksClick: () => void;
}) {
  let Icon = RotateCw;
  let label = '';
  let active = false;
  let onClick = onRotate;

  switch (tool) {
    case 'ZOOM':
      Icon = ZoomIn;
      label = Math.abs(pinchScale - 1) > 0.05 ? `${pinchScale.toFixed(2).replace(/\.00$/, '')}x` : 'Zoom';
      active = Math.abs(pinchScale - 1) > 0.05;
      onClick = onZoomClick;
      break;
    case 'ROTATE':
      Icon = RotateCw;
      label = 'Rotate';
      active = isRotated;
      onClick = onRotate;
      break;
    case 'SPEED':
      Icon = Gauge;
      label = `${playbackSpeed}x`;
      active = Math.abs(playbackSpeed - 1) > 0.01 || showSpeedPicker;
      onClick = onSpeedClick;
      break;
    case 'LOOP':
      Icon = repeatMode === RepeatMode.ONE ? Repeat1 : Repeat;
      label =
        repeatMode === RepeatMode.ONE
          ? 'Loop 1'
          : repeatMode === RepeatMode.ALL
            ? 'Loop All'
            : 'Loop';
      active = repeatMode !== RepeatMode.OFF;
      onClick = onLoopClick;
      break;
    case 'MUTE':
      Icon = isMuted ? VolumeX : Volume2;
      label = isMuted ? 'Unmute' : 'Mute';
      active = isMuted;
      onClick = onMuteClick;
      break;
    case 'SLEEP':
      Icon = Moon;
      label = sleepMinutesRemaining !== null ? `${sleepMinutesRemaining}m` : 'Sleep';
      active = sleepMinutesRemaining !== null;
      onClick = onSleepClick;
      break;
    case 'BG_PLAY':
      Icon = Headphones;
      label = 'BG Play';
      active = isBackgroundPlayEnabled;
      onClick = onBgPlayClick;
      break;
    case 'TRACKS':
      Icon = Captions;
      label = 'Tracks';
      active = false;
      onClick = onTracksClick;
      break;
  }

  const isSleepActive = tool === 'SLEEP' && active;
  const activeBg = isSleepActive
    ? 'bg-[#F59E0B]/20 text-[#F59E0B]'
    : 'bg-[#2DD4BF]/20 text-[#2DD4BF]';

  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex shrink-0 flex-col items-center justify-center rounded-xl px-2.5 py-1.5 transition-colors ${
        active ? activeBg : 'bg-white/10 text-white hover:bg-white/15'
      }`}
    >
      <Icon className="h-4 w-4" />
      <span className="mt-0.5 text-[9.5px] font-medium whitespace-nowrap">{label}</span>
    </button>
  );
}

function VerticalGestureHud({
  side,
  icon,
  label,
  fraction,
  barColor,
}: {
  side: 'left' | 'right';
  icon: React.ReactNode;
  label: string;
  fraction: number;
  barColor: string;
}) {
  const clamped = Math.max(0.02, Math.min(1, fraction));
  return (
    <div
      className={`pointer-events-none absolute top-1/2 flex -translate-y-1/2 flex-col items-center rounded-2xl bg-[#0B0E14]/85 p-3.5 backdrop-blur-sm ${
        side === 'left' ? 'left-7' : 'right-7'
      }`}
    >
      {icon}
      <div className="my-2.5 flex h-28 w-2 items-end overflow-hidden rounded-full bg-white/20">
        <div
          className="w-full rounded-full transition-all duration-75"
          style={{ height: `${clamped * 100}%`, backgroundColor: barColor }}
        />
      </div>
      <span className="font-mono-label text-[11px] font-medium text-white">{label}</span>
    </div>
  );
}

function TrackChoiceRow({
  label,
  selected,
  onClick,
}: {
  label: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex w-full items-center justify-between gap-2 rounded-xl px-3 py-2.5 text-left text-xs transition-colors ${
        selected ? 'bg-[#2DD4BF]/15 font-semibold text-[#2DD4BF]' : 'text-white hover:bg-white/5'
      }`}
    >
      <span className="truncate">{label}</span>
      {selected && <Check className="h-4 w-4 shrink-0 text-[#2DD4BF]" />}
    </button>
  );
}
