import { useState, type ReactNode } from 'react';
import {
  Captions,
  Check,
  CheckSquare,
  ChevronRight,
  Cpu,
  Gauge,
  Headphones,
  Lock,
  Menu,
  Moon,
  PictureInPicture2,
  RefreshCw,
  Repeat,
  RotateCw,
  SkipForward,
  SlidersHorizontal,
  Square,
  Sun,
  Touchpad,
  Volume2,
  X,
  ZoomIn,
} from 'lucide-react';
import {
  DOUBLE_CLICK_LABELS,
  DoubleClickAction,
  PLAYER_BUTTON_LABELS,
  PlayerControlButton,
  type ProgressBarStyle,
  SINGLE_CLICK_LABELS,
  SingleClickAction,
  type UserPreferences,
} from '../types/media';

const THEME_PRESETS = [
  { name: 'Teal Mint', primary: '#2dd4bf', container: '#0f3d39' },
  { name: 'Emerald', primary: '#10b981', container: '#064e3b' },
  { name: 'Amber', primary: '#f59e0b', container: '#452a04' },
  { name: 'Purple', primary: '#a855f7', container: '#3b0764' },
  { name: 'Neon Pink', primary: '#ec4899', container: '#500724' },
  { name: 'Ocean Blue', primary: '#3b82f6', container: '#1e3a8a' },
];

function applyCustomTheme(primary: string, container: string) {
  document.documentElement.style.setProperty('--primary', primary);
  document.documentElement.style.setProperty('--primary-container', container);
  localStorage.setItem('ag_custom_primary', primary);
  localStorage.setItem('ag_custom_container', container);
}

type SettingsTab = 'MENU' | 'CONTROLS' | 'PLAYBACK' | 'UPDATE';

interface TabMeta {
  id: SettingsTab;
  title: string;
  subtitle: string;
  icon: typeof Menu;
}

const SETTINGS_TABS: TabMeta[] = [
  {
    id: 'MENU',
    title: 'Menu',
    subtitle: 'Theme, entry mode, layout & hidden files',
    icon: Menu,
  },
  {
    id: 'CONTROLS',
    title: 'Controls',
    subtitle: 'Progress bar style, screen buttons, shortcuts & gestures',
    icon: Touchpad,
  },
  {
    id: 'PLAYBACK',
    title: 'Playback & Background',
    subtitle: 'Background audio play, PiP & SW decoder',
    icon: Headphones,
  },
  {
    id: 'UPDATE',
    title: 'Check Update',
    subtitle: 'GitHub version & APK download',
    icon: RefreshCw,
  },
];

export function FitScreenIcon({ className = 'h-5 w-5' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <rect x="2" y="4" width="20" height="16" rx="2.5" />
      <path d="M6 10V8h2" />
      <path d="M18 14v2h-2" />
      <path d="M16 8h2v2" />
      <path d="M8 16H6v-2" />
    </svg>
  );
}

function renderButtonIcon(btn: PlayerControlButton, className = 'h-4 w-4') {
  switch (btn) {
    case PlayerControlButton.FIT:
      return <FitScreenIcon className={className} />;
    case PlayerControlButton.ROTATE:
      return <RotateCw className={className} />;
    case PlayerControlButton.SPEED:
      return <Gauge className={className} />;
    case PlayerControlButton.LOOP:
      return <Repeat className={className} />;
    case PlayerControlButton.MUTE:
      return <Volume2 className={className} />;
    case PlayerControlButton.ZOOM:
      return <ZoomIn className={className} />;
    case PlayerControlButton.SLEEP:
      return <Moon className={className} />;
    case PlayerControlButton.BG_PLAY:
      return <Headphones className={className} />;
    case PlayerControlButton.TRACKS:
      return <Captions className={className} />;
    case PlayerControlButton.DECODER:
      return <Cpu className={className} />;
    case PlayerControlButton.LOCK:
      return <Lock className={className} />;
    case PlayerControlButton.PREV_NEXT:
      return <SkipForward className={className} />;
    case PlayerControlButton.PIP:
      return <PictureInPicture2 className={className} />;
  }
}

const QUICK_SHORTCUT_BUTTONS: PlayerControlButton[] = [
  PlayerControlButton.ZOOM,
  PlayerControlButton.MUTE,
  PlayerControlButton.ROTATE,
  PlayerControlButton.SPEED,
  PlayerControlButton.LOOP,
  PlayerControlButton.SLEEP,
  PlayerControlButton.BG_PLAY,
  PlayerControlButton.TRACKS,
];

const SCREEN_OVERLAY_BUTTONS: PlayerControlButton[] = [
  PlayerControlButton.FIT,
  PlayerControlButton.PIP,
  PlayerControlButton.LOCK,
  PlayerControlButton.PREV_NEXT,
  PlayerControlButton.DECODER,
];

interface ControlSettingsDialogProps {
  preferences: UserPreferences;
  currentVersionLabel: string;
  hasAvailableUpdate: boolean;
  onDismiss: () => void;
  onToggleTheme: () => void;
  onOpenUpdateCheck: () => void;
  onProgressBarStyleChange: (style: ProgressBarStyle) => void;
  onSingleClickActionChange: (action: SingleClickAction) => void;
  onDoubleClickActionChange: (action: DoubleClickAction) => void;
  onAlwaysBackgroundPlayChange: (enabled: boolean) => void;
  onAutoPipChange: (enabled: boolean) => void;
  onEnableSwipeGesturesChange: (enabled: boolean) => void;
  onGestureSeekStepChange: (seconds: number) => void;
  onDefaultSoftwareDecoderChange: (enabled: boolean) => void;
  onShowHiddenFilesChange: (enabled: boolean) => void;
  onMainDisplayModeChange: (mode: 'folders' | 'files') => void;
  onMainViewLayoutChange: (layout: 'list' | 'grid') => void;
  onOneFingerGestureChange: (gesture: string) => void;
  onTwoFingerGestureChange: (gesture: string) => void;
  onThreeFingerGestureChange: (gesture: string) => void;
  onTogglePlayerButtonVisibility: (button: PlayerControlButton, visible: boolean) => void;
  onShowAllButtons: () => void;
}

export function ControlSettingsDialog({
  preferences,
  currentVersionLabel,
  hasAvailableUpdate,
  onDismiss,
  onToggleTheme,
  onOpenUpdateCheck,
  onProgressBarStyleChange,
  onSingleClickActionChange,
  onDoubleClickActionChange,
  onAlwaysBackgroundPlayChange,
  onAutoPipChange,
  onEnableSwipeGesturesChange,
  onGestureSeekStepChange,
  onDefaultSoftwareDecoderChange,
  onShowHiddenFilesChange,
  onMainDisplayModeChange,
  onMainViewLayoutChange,
  onOneFingerGestureChange,
  onTwoFingerGestureChange,
  onThreeFingerGestureChange,
  onTogglePlayerButtonVisibility,
  onShowAllButtons,
}: ControlSettingsDialogProps) {
  const [activeTab, setActiveTab] = useState<SettingsTab | null>(null);
  const [selectedPrimary, setSelectedPrimary] = useState(() => 
    localStorage.getItem('ag_custom_primary') || '#2dd4bf'
  );

  const applyCustomTheme = (primary: string, container: string) => {
    document.documentElement.style.setProperty('--primary', primary);
    document.documentElement.style.setProperty('--primary-container', container);
    localStorage.setItem('ag_custom_primary', primary);
    localStorage.setItem('ag_custom_container', container);
    setSelectedPrimary(primary);
  };

  const applyRandomTheme = () => {
    const hue = Math.floor(Math.random() * 360);
    const primary = `hsl(${hue}, 85%, 55%)`;
    const container = `hsl(${hue}, 80%, 20%)`;
    applyCustomTheme(primary, container);
  };

  const activeTabMeta = activeTab ? SETTINGS_TABS.find((t) => t.id === activeTab) ?? null : null;
  const HeaderIcon = activeTabMeta?.icon ?? SlidersHorizontal;
  const hiddenSet = new Set(preferences.hiddenButtons);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-[2px]"
      onClick={onDismiss}
    >
      <div
        className="flex max-h-[86vh] w-full max-w-lg flex-col overflow-hidden rounded-3xl border border-[var(--outline)]/40 bg-[var(--bg-surface)] text-[var(--text-primary)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Settings"
      >
        {/* Header */}
        <div className="flex items-center gap-3 px-5 py-4">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[var(--primary)]/15 text-[var(--primary)]">
            <HeaderIcon className="h-5 w-5" />
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-base font-bold">
              {activeTabMeta?.title ?? 'Settings'}
            </h2>
            <p className="truncate text-xs text-[var(--text-muted)]">
              {activeTabMeta?.subtitle ?? `Tap a tab below to configure · ${currentVersionLabel}`}
            </p>
          </div>
          <button
            type="button"
            onClick={() => (activeTab ? setActiveTab(null) : onDismiss())}
            aria-label="Close"
            className="flex h-9 w-9 items-center justify-center rounded-full text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-surface-variant)] hover:text-[var(--text-primary)]"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="h-px bg-[var(--outline)]/30" />

        {/* Body */}
        <div className="flex-1 space-y-3.5 overflow-y-auto px-4 py-4">
          {activeTab === null && (
            <div className="space-y-2.5">
              {SETTINGS_TABS.map((tab) => {
                const Icon = tab.icon;
                const showNew = tab.id === 'UPDATE' && hasAvailableUpdate;
                return (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => {
                      if (tab.id === 'UPDATE') {
                        onDismiss();
                        onOpenUpdateCheck();
                      } else {
                        setActiveTab(tab.id);
                      }
                    }}
                    className="flex w-full items-center gap-3 rounded-2xl bg-[var(--bg-surface-variant)]/65 px-3.5 py-3.5 text-left transition-colors hover:bg-[var(--bg-surface-variant)]"
                  >
                    <Icon
                      className={`h-5 w-5 shrink-0 ${
                        showNew ? 'text-[var(--secondary)]' : 'text-[var(--primary)]'
                      }`}
                    />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold">{tab.title}</span>
                        {showNew && (
                          <span className="rounded-md bg-[var(--secondary)] px-1.5 py-0.5 text-[10px] font-bold text-[var(--on-secondary)]">
                            NEW
                          </span>
                        )}
                      </div>
                      <p className="truncate text-xs text-[var(--text-muted)]">{tab.subtitle}</p>
                    </div>
                    <ChevronRight className="h-4 w-4 shrink-0 text-[var(--text-muted)]" />
                  </button>
                );
              })}
            </div>
          )}

          {activeTab === 'MENU' && (
            <SettingsGroup title="Main Interface & Layout">
              <button
                type="button"
                onClick={onToggleTheme}
                className="flex w-full items-center gap-3 px-3.5 py-3 text-left transition-colors hover:bg-[var(--bg-surface)]/50"
                role="switch"
                aria-checked={preferences.isDarkMode}
              >
                {preferences.isDarkMode ? (
                  <Moon className="h-5 w-5 shrink-0 text-[var(--primary)]" />
                ) : (
                  <Sun className="h-5 w-5 shrink-0 text-[var(--secondary)]" />
                )}
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium">Dark / Light Mode</div>
                  <div className="text-xs text-[var(--text-muted)]">
                    {preferences.isDarkMode ? 'Cinema Dark theme active' : 'Crisp Light theme active'}
                  </div>
                </div>
                <div
                  className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors ${
                    preferences.isDarkMode ? 'bg-[var(--primary)]' : 'bg-[var(--outline)]'
                  }`}
                >
                  <span
                    className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
                      preferences.isDarkMode ? 'translate-x-6' : 'translate-x-1'
                    }`}
                  />
                </div>
              </button>
              <SettingsDivider />
              <div className="space-y-3 p-3.5">
                <div className="flex items-center justify-between">
                  <div className="text-sm font-semibold">Accent Theme & Randomizer</div>
                  <button
                    type="button"
                    onClick={applyRandomTheme}
                    className="flex items-center gap-1.5 rounded-xl bg-[var(--primary)] px-3 py-1.5 text-xs font-semibold text-[var(--on-primary)] transition-transform active:scale-95"
                  >
                    <span>🎲 Random Theme</span>
                  </button>
                </div>
                <div className="grid grid-cols-3 gap-2 pt-1">
                  {THEME_PRESETS.map((t) => {
                    const isSelected = selectedPrimary.toLowerCase() === t.primary.toLowerCase();
                    return (
                      <button
                        key={t.name}
                        type="button"
                        onClick={() => applyCustomTheme(t.primary, t.container)}
                        className={`flex items-center gap-2 rounded-xl px-3 py-2 text-xs font-medium transition-all ${
                          isSelected
                            ? 'bg-[var(--primary)] text-[var(--on-primary)] ring-2 ring-white/40 shadow-md font-bold'
                            : 'bg-[var(--bg-surface-variant)] text-[var(--text-primary)] hover:opacity-90'
                        }`}
                      >
                        <span className="h-3.5 w-3.5 rounded-full shrink-0 border border-white/30" style={{ backgroundColor: t.primary }} />
                        <span className="truncate flex-1 text-left">{t.name}</span>
                        {isSelected && <Check className="h-3.5 w-3.5 shrink-0" />}
                      </button>
                    );
                  })}
                </div>
              </div>

              <SettingsDivider />
              <div className="space-y-3 p-3.5">
                <div className="text-sm font-semibold">Default Entry Mode</div>
                <TwoColSelector
                  options={[
                    { value: 'folders' as const, label: 'All Folders' },
                    { value: 'files' as const, label: 'Files' },
                  ]}
                  selectedValue={preferences.mainDisplayMode}
                  onSelect={onMainDisplayModeChange}
                />
                <div className="text-sm font-semibold">View Layout</div>
                <TwoColSelector
                  options={[
                    { value: 'grid' as const, label: 'Grid' },
                    { value: 'list' as const, label: 'List' },
                  ]}
                  selectedValue={preferences.mainViewLayout}
                  onSelect={onMainViewLayoutChange}
                />
              </div>
              <SettingsDivider />
              <SettingsSwitchRow
                title="Show Hidden Files"
                subtitle="Display hidden files and folders starting with dot (.)"
                checked={preferences.showHiddenFiles}
                onChange={onShowHiddenFilesChange}
              />
            </SettingsGroup>
          )}

          {activeTab === 'CONTROLS' && (
            <div className="space-y-4">
              {/* Style: Progress Bar Dropdown */}
              <SettingsGroup title="Style & Progress Bar">
                <div className="flex items-center justify-between gap-3 px-3.5 py-3">
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-medium">Progress Bar Style</div>
                    <div className="text-xs text-[var(--text-muted)]">
                      Choose Material thumb slider or sleek Flat bar
                    </div>
                  </div>
                  <select
                    value={preferences.progressBarStyle}
                    onChange={(e) => onProgressBarStyleChange(e.target.value as ProgressBarStyle)}
                    aria-label="Progress Bar Style"
                    className="rounded-xl border border-[var(--outline)] bg-[var(--bg-surface)] px-3 py-1.5 text-xs font-semibold text-[var(--text-primary)] outline-none focus:border-[var(--primary)]"
                  >
                    <option value="material">Material</option>
                    <option value="flat">Flat</option>
                  </select>
                </div>
              </SettingsGroup>

              {/* Quick Shortcut Icons (Tick Marks) */}
              <SettingsGroup
                title="Quick Shortcut Icons"
                action={
                  <button
                    type="button"
                    onClick={onShowAllButtons}
                    className="text-xs font-semibold text-[var(--primary)] hover:underline"
                  >
                    Show All
                  </button>
                }
              >
                <div className="grid grid-cols-2 gap-1.5 p-2.5">
                  {QUICK_SHORTCUT_BUTTONS.map((btn) => {
                    const enabled = !hiddenSet.has(btn);
                    return (
                      <button
                        key={btn}
                        type="button"
                        onClick={() => onTogglePlayerButtonVisibility(btn, !enabled)}
                        className={`flex items-center gap-2 rounded-xl px-2.5 py-2.5 text-left transition-colors ${
                          enabled
                            ? 'bg-[var(--primary-container)]/70 text-[var(--on-primary-container)]'
                            : 'bg-[var(--bg-surface)]/70 text-[var(--text-muted)]'
                        }`}
                      >
                        <span className={enabled ? 'text-[var(--primary)]' : 'text-[var(--text-muted)]'}>
                          {renderButtonIcon(btn)}
                        </span>
                        <span
                          className={`min-w-0 flex-1 truncate text-xs ${
                            enabled ? 'font-semibold' : 'font-normal'
                          }`}
                        >
                          {PLAYER_BUTTON_LABELS[btn]}
                        </span>
                        {enabled ? (
                          <CheckSquare className="h-4 w-4 shrink-0 text-[var(--primary)]" />
                        ) : (
                          <Square className="h-4 w-4 shrink-0 text-[var(--text-muted)]/50" />
                        )}
                      </button>
                    );
                  })}
                </div>
              </SettingsGroup>

              {/* On-Screen Player Buttons (Tick Marks) */}
              <SettingsGroup title="On-Screen Buttons">
                <div className="grid grid-cols-2 gap-1.5 p-2.5">
                  {SCREEN_OVERLAY_BUTTONS.map((btn) => {
                    const enabled = !hiddenSet.has(btn);
                    return (
                      <button
                        key={btn}
                        type="button"
                        onClick={() => onTogglePlayerButtonVisibility(btn, !enabled)}
                        className={`flex items-center gap-2 rounded-xl px-2.5 py-2.5 text-left transition-colors ${
                          enabled
                            ? 'bg-[var(--primary-container)]/70 text-[var(--on-primary-container)]'
                            : 'bg-[var(--bg-surface)]/70 text-[var(--text-muted)]'
                        }`}
                      >
                        <span className={enabled ? 'text-[var(--primary)]' : 'text-[var(--text-muted)]'}>
                          {renderButtonIcon(btn)}
                        </span>
                        <span
                          className={`min-w-0 flex-1 truncate text-xs ${
                            enabled ? 'font-semibold' : 'font-normal'
                          }`}
                        >
                          {PLAYER_BUTTON_LABELS[btn]}
                        </span>
                        {enabled ? (
                          <CheckSquare className="h-4 w-4 shrink-0 text-[var(--primary)]" />
                        ) : (
                          <Square className="h-4 w-4 shrink-0 text-[var(--text-muted)]/50" />
                        )}
                      </button>
                    );
                  })}
                </div>
                <SettingsDivider />
                <SettingsSwitchRow
                  title="Mute Button"
                  subtitle="Show quick Mute / Unmute button in player"
                  checked={!hiddenSet.has(PlayerControlButton.MUTE)}
                  onChange={(v) => onTogglePlayerButtonVisibility(PlayerControlButton.MUTE, v)}
                />
                <SettingsDivider />
                <SettingsSwitchRow
                  title="Quick Zoom"
                  subtitle="Show quick zoom toggle (1.0x / 1.25x / 1.5x / 2.0x)"
                  checked={!hiddenSet.has(PlayerControlButton.ZOOM)}
                  onChange={(v) => onTogglePlayerButtonVisibility(PlayerControlButton.ZOOM, v)}
                />
                <SettingsDivider />
                <SettingsSwitchRow
                  title="Picture-in-Picture Mode"
                  subtitle="Auto PiP when switching away & show PiP button"
                  checked={preferences.autoPipOnHome && !hiddenSet.has(PlayerControlButton.PIP)}
                  onChange={(v) => {
                    onAutoPipChange(v);
                    onTogglePlayerButtonVisibility(PlayerControlButton.PIP, v);
                  }}
                />
                <SettingsDivider />
                <SettingsSwitchRow
                  title="Background Play Audio"
                  subtitle="Keep audio playing in mini-bar when exiting player"
                  checked={preferences.alwaysBackgroundPlay}
                  onChange={onAlwaysBackgroundPlayChange}
                />
              </SettingsGroup>

              {/* Tap & Swipe Gestures */}
              <SettingsGroup title="Tap & Swipe Gestures">
                <div className="space-y-3 p-3.5">
                  <div className="text-sm font-semibold">1 Finger Gesture</div>
                  <TwoColSelector
                    options={[
                      { value: 'Brightness / Volume Swipe', label: 'Brightness / Volume Swipe' },
                      { value: 'Seek & Controls', label: 'Seek & Controls' },
                      { value: 'None', label: 'None' },
                    ]}
                    selectedValue={preferences.oneFingerGesture}
                    onSelect={onOneFingerGestureChange}
                  />

                  <div className="text-sm font-semibold">2 Finger Gesture</div>
                  <TwoColSelector
                    options={[
                      { value: 'Zoom / Scale', label: 'Zoom / Scale' },
                      { value: 'Seek ±10s', label: 'Seek ±10s' },
                      { value: 'Brightness / Vol', label: 'Brightness / Vol' },
                      { value: 'None', label: 'None' },
                    ]}
                    selectedValue={preferences.twoFingerGesture}
                    onSelect={onTwoFingerGestureChange}
                  />

                  <div className="text-sm font-semibold">3 Finger Gesture</div>
                  <TwoColSelector
                    options={[
                      { value: 'Play / Pause', label: 'Play / Pause' },
                      { value: 'Lock / Unlock', label: 'Lock / Unlock' },
                      { value: 'Mute Audio', label: 'Mute Audio' },
                      { value: 'None', label: 'None' },
                    ]}
                    selectedValue={preferences.threeFingerGesture}
                    onSelect={onThreeFingerGestureChange}
                  />

                  <div className="text-sm font-semibold">Single Click</div>
                  <TwoColSelector
                    options={Object.values(SingleClickAction).map((a) => ({
                      value: a,
                      label: SINGLE_CLICK_LABELS[a],
                    }))}
                    selectedValue={preferences.singleClickAction}
                    onSelect={onSingleClickActionChange}
                  />

                  <div className="text-sm font-semibold">Double Click</div>
                  <TwoColSelector
                    options={Object.values(DoubleClickAction).map((a) => ({
                      value: a,
                      label: DOUBLE_CLICK_LABELS[a],
                    }))}
                    selectedValue={preferences.doubleClickAction}
                    onSelect={onDoubleClickActionChange}
                  />
                </div>
                <SettingsDivider />
                <SettingsSwitchRow
                  title="Swipe Gestures"
                  subtitle="Horizontal seek, left brightness, right volume"
                  checked={preferences.enableSwipeGestures}
                  onChange={onEnableSwipeGesturesChange}
                />
                <SettingsDivider />
                <div className="flex items-center justify-between gap-3 px-3.5 py-3">
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-medium">Swipe Seek Step</div>
                    <div className="text-xs text-[var(--text-muted)]">
                      Auto smooth scrub or fixed 10s / 20s step
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-1.5">
                    {[
                      { sec: 0, label: 'Auto' },
                      { sec: 10, label: '10s' },
                      { sec: 20, label: '20s' },
                    ].map((opt) => {
                      const selected = preferences.gestureSeekStepSeconds === opt.sec;
                      return (
                        <button
                          key={opt.sec}
                          type="button"
                          onClick={() => onGestureSeekStepChange(opt.sec)}
                          className={`rounded-lg px-2.5 py-1.5 text-xs font-semibold transition-colors ${
                            selected
                              ? 'bg-[var(--primary)] text-[var(--on-primary)]'
                              : 'bg-[var(--bg-surface)] text-[var(--text-muted)] hover:text-[var(--text-primary)]'
                          }`}
                        >
                          {opt.label}
                        </button>
                      );
                    })}
                  </div>
                </div>
              </SettingsGroup>
            </div>
          )}

          {activeTab === 'PLAYBACK' && (
            <SettingsGroup title="Playback & Background">
              <SettingsSwitchRow
                title="Always Background Play"
                subtitle="Keep playing audio when navigating back or minimizing player"
                checked={preferences.alwaysBackgroundPlay}
                onChange={onAlwaysBackgroundPlayChange}
              />
              <SettingsDivider />
              <SettingsSwitchRow
                title="Auto PiP on Home"
                subtitle="Open mini player window when switching away"
                checked={preferences.autoPipOnHome}
                onChange={onAutoPipChange}
              />
              <SettingsDivider />
              <SettingsSwitchRow
                title="Default SW Decoder"
                subtitle="Prefer Software decoder instead of Hardware"
                checked={preferences.defaultSoftwareDecoder}
                onChange={onDefaultSoftwareDecoderChange}
              />
            </SettingsGroup>
          )}
        </div>

        <div className="h-px bg-[var(--outline)]/30" />

        {/* Footer */}
        <div className="flex items-center justify-between px-4 py-3">
          {activeTab !== null ? (
            <button
              type="button"
              onClick={() => setActiveTab(null)}
              className="rounded-lg px-3 py-1.5 text-xs font-semibold text-[var(--primary)] hover:bg-[var(--bg-surface-variant)]"
            >
              Back to Tabs
            </button>
          ) : (
            <span />
          )}
          <button
            type="button"
            onClick={onDismiss}
            className="rounded-xl bg-[var(--primary)] px-4 py-2 text-xs font-semibold text-[var(--on-primary)] transition-opacity hover:opacity-90"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
}

function SettingsGroup({
  title,
  action,
  children,
}: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between px-1">
        <span className="text-xs font-bold text-[var(--primary)]">{title}</span>
        {action}
      </div>
      <div className="overflow-hidden rounded-2xl bg-[var(--bg-surface-variant)]/55">
        {children}
      </div>
    </div>
  );
}

function SettingsDivider() {
  return <div className="mx-3.5 h-px bg-[var(--outline)]/30" />;
}

function SettingsSwitchRow({
  title,
  subtitle,
  checked,
  onChange,
}: {
  title: string;
  subtitle: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className="flex w-full items-center gap-3 px-3.5 py-3 text-left transition-colors hover:bg-[var(--bg-surface)]/40"
      role="switch"
      aria-checked={checked}
    >
      <div className="min-w-0 flex-1">
        <div className="text-sm font-medium">{title}</div>
        <div className="text-xs text-[var(--text-muted)]">{subtitle}</div>
      </div>
      <div
        className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors ${
          checked ? 'bg-[var(--primary)]' : 'bg-[var(--outline)]'
        }`}
      >
        <span
          className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
            checked ? 'translate-x-6' : 'translate-x-1'
          }`}
        />
      </div>
    </button>
  );
}

function TwoColSelector<T extends string>({
  options,
  selectedValue,
  onSelect,
}: {
  options: { value: T; label: string }[];
  selectedValue: T;
  onSelect: (val: T) => void;
}) {
  return (
    <div className="grid grid-cols-2 gap-1.5">
      {options.map((opt) => {
        const selected = selectedValue === opt.value;
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => onSelect(opt.value)}
            className={`flex items-center gap-1.5 rounded-xl px-2.5 py-2.5 text-left text-xs transition-colors ${
              selected
                ? 'bg-[var(--primary)] font-semibold text-[var(--on-primary)]'
                : 'bg-[var(--bg-surface)] text-[var(--text-primary)] hover:bg-[var(--bg-surface)]/80'
            }`}
          >
            {selected && <Check className="h-3.5 w-3.5 shrink-0" />}
            <span className="truncate">{opt.label}</span>
          </button>
        );
      })}
    </div>
  );
}
