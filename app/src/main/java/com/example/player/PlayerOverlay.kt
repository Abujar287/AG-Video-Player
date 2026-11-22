package com.example.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.R
import com.example.data.repository.DoubleClickAction
import com.example.data.repository.PlayerControlsVisibility
import com.example.data.repository.SingleClickAction
import com.example.ui.theme.AmberBoost
import com.example.ui.theme.BoostGlowOrange
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.PlayerOverlayScrim
import com.example.util.FormatUtils
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PlayerAspectMode(val label: String) {
    FIT("Fit"),
    FILL("Stretch"),
    ZOOM("Crop")
}

private enum class ActiveDragGesture {
    NONE,
    SEEK,
    BRIGHTNESS,
    VOLUME
}

@Composable
fun PlayerOverlay(
    title: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    useSoftwareDecoder: Boolean,
    isLocked: Boolean,
    isBackgroundPlayEnabled: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    repeatMode: Int,
    sleepMinutesRemaining: Int?,
    aspectMode: PlayerAspectMode,
    controlsVisible: Boolean,
    isInPipMode: Boolean,
    brightnessPercent: Int,
    volumePercent: Int,
    isMuted: Boolean,
    audioTracks: List<MediaTrackOption>,
    subtitleTracks: List<MediaTrackOption>,
    subtitlesDisabled: Boolean,
    doubleTapFeedback: String?,
    statusBanner: String?,
    singleClickAction: SingleClickAction,
    doubleClickAction: DoubleClickAction,
    enableSwipeGestures: Boolean,
    gestureSeekStepSeconds: Int,
    controlsVisibility: PlayerControlsVisibility,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onPreviousVideo: () -> Unit,
    onNextVideo: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleRotation: () -> Unit,
    onCycleSpeed: (Float) -> Unit,
    onToggleDecoder: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSetSleepTimerMinutes: (Int?) -> Unit,
    onCycleAspectMode: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectAudioTrack: (MediaTrackOption) -> Unit,
    onSelectSubtitleTrack: (MediaTrackOption?) -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onDoubleTapPlayPause: () -> Unit,
    onDoubleTapSeek: (Long) -> Unit
) {
    if (isInPipMode) return

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeGesture by remember { mutableStateOf(ActiveDragGesture.NONE) }
    var dragStartPosMs by remember { mutableLongStateOf(0L) }
    var previewSeekMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var startBrightness by remember { mutableFloatStateOf(brightnessPercent.toFloat()) }
    var startVolume by remember { mutableFloatStateOf(volumePercent.toFloat()) }

    var showMoreTools by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    var isScrubbingBar by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

    val latestIsLocked by rememberUpdatedState(isLocked)
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestPositionMs by rememberUpdatedState(currentPositionMs)
    val latestBrightness by rememberUpdatedState(brightnessPercent)
    val latestVolume by rememberUpdatedState(volumePercent)
    val latestContainerSize by rememberUpdatedState(containerSize)
    val latestSingleClickAction by rememberUpdatedState(singleClickAction)
    val latestDoubleClickAction by rememberUpdatedState(doubleClickAction)
    val latestEnableSwipeGestures by rememberUpdatedState(enableSwipeGestures)
    val latestSeekStepMs by rememberUpdatedState((gestureSeekStepSeconds.coerceIn(5, 60)) * 1000L)
    val latestOnToggleControls by rememberUpdatedState(onToggleControls)
    val latestOnTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
    val latestOnToggleMute by rememberUpdatedState(onToggleMute)
    val latestOnDoubleTapPlayPause by rememberUpdatedState(onDoubleTapPlayPause)
    val latestOnDoubleTapSeek by rememberUpdatedState(onDoubleTapSeek)
    val latestOnSeekTo by rememberUpdatedState(onSeekTo)
    val latestOnBrightnessChange by rememberUpdatedState(onBrightnessChange)
    val latestOnVolumeChange by rememberUpdatedState(onVolumeChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showSpeedPicker = false
                        if (latestIsLocked) {
                            latestOnToggleControls()
                        } else {
                            when (latestSingleClickAction) {
                                SingleClickAction.TOGGLE_CONTROLS -> latestOnToggleControls()
                                SingleClickAction.PLAY_PAUSE -> latestOnTogglePlayPause()
                                SingleClickAction.TOGGLE_MUTE -> latestOnToggleMute()
                                SingleClickAction.NONE -> Unit
                            }
                        }
                    },
                    onDoubleTap = { tapOffset ->
                        if (!latestIsLocked) {
                            val isLeftSide = tapOffset.x < (latestContainerSize.width / 2f)
                            when (latestDoubleClickAction) {
                                DoubleClickAction.PLAY_PAUSE -> latestOnDoubleTapPlayPause()
                                DoubleClickAction.SEEK_10S -> latestOnDoubleTapSeek(
                                    if (isLeftSide) -10_000L else 10_000L
                                )
                                DoubleClickAction.SEEK_20S -> latestOnDoubleTapSeek(
                                    if (isLeftSide) -20_000L else 20_000L
                                )
                                DoubleClickAction.NONE -> Unit
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var totalDx = 0f
                var totalDy = 0f
                var gestureStartX = 0f
                detectDragGestures(
                    onDragStart = { startOffset ->
                        activeGesture = ActiveDragGesture.NONE
                        totalDx = 0f
                        totalDy = 0f
                        gestureStartX = startOffset.x
                        dragStartPosMs = latestPositionMs
                        previewSeekMs = latestPositionMs
                        seekDeltaMs = 0L
                        startBrightness = latestBrightness.toFloat()
                        startVolume = latestVolume.toFloat()
                    },
                    onDragEnd = {
                        if (!latestIsLocked && latestEnableSwipeGestures && activeGesture == ActiveDragGesture.SEEK) {
                            latestOnSeekTo(previewSeekMs)
                        }
                        activeGesture = ActiveDragGesture.NONE
                    },
                    onDragCancel = {
                        activeGesture = ActiveDragGesture.NONE
                    },
                    onDrag = { change, dragAmount ->
                        if (latestIsLocked || !latestEnableSwipeGestures) return@detectDragGestures
                        change.consume()
                        totalDx += dragAmount.x
                        totalDy += dragAmount.y
                        val sizeNow = latestContainerSize

                        if (activeGesture == ActiveDragGesture.NONE) {
                            if (abs(totalDx) > 20f && abs(totalDx) > abs(totalDy) * 1.15f) {
                                activeGesture = ActiveDragGesture.SEEK
                            } else if (abs(totalDy) > 16f && abs(totalDy) > abs(totalDx) * 1.15f) {
                                val midX = if (sizeNow.width > 0) sizeNow.width / 2f else 500f
                                activeGesture = if (gestureStartX < midX) {
                                    ActiveDragGesture.BRIGHTNESS
                                } else {
                                    ActiveDragGesture.VOLUME
                                }
                            }
                        }

                        when (activeGesture) {
                            ActiveDragGesture.SEEK -> {
                                val stepPx = 110f
                                val steps = ((abs(totalDx) - 20f).coerceAtLeast(0f) / stepPx).toInt() + 1
                                val deltaMs = if (totalDx >= 0f) {
                                    steps * latestSeekStepMs
                                } else {
                                    -steps * latestSeekStepMs
                                }
                                seekDeltaMs = deltaMs
                                val dur = latestDurationMs
                                val targetUpper = if (dur > 0L) dur else Long.MAX_VALUE
                                previewSeekMs = (dragStartPosMs + deltaMs).coerceIn(0L, targetUpper)
                            }
                            ActiveDragGesture.BRIGHTNESS -> {
                                val heightPx = (sizeNow.height * 0.55f).coerceAtLeast(180f)
                                val deltaPercent = (-totalDy / heightPx) * 100f
                                val nextBrightness = (startBrightness + deltaPercent).roundToInt().coerceIn(2, 100)
                                latestOnBrightnessChange(nextBrightness)
                            }
                            ActiveDragGesture.VOLUME -> {
                                val heightPx = (sizeNow.height * 0.55f).coerceAtLeast(180f)
                                val deltaPercent = (-totalDy / heightPx) * 200f
                                val nextVol = (startVolume + deltaPercent).roundToInt().coerceIn(0, 200)
                                latestOnVolumeChange(nextVol)
                            }
                            ActiveDragGesture.NONE -> Unit
                        }
                    }
                )
            }
    ) {
        // Controls Overlay (hidden completely on single click)
        AnimatedVisibility(
            visible = controlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                            )
                        )
                )

                // Top Bar: Back + Video Name + HW/SW on Title Line; Rotate + Speed + '>' Expandable Tools below
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_desc),
                                tint = Color.White
                            )
                        }

                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // HW / SW Decoder Pill on Top Name Line (no Sound % button)
                        if (controlsVisibility.showDecoderButton) {
                            Surface(
                                onClick = onToggleDecoder,
                                shape = RoundedCornerShape(8.dp),
                                color = if (useSoftwareDecoder) {
                                    AmberBoost.copy(alpha = 0.25f)
                                } else {
                                    ElectricTeal.copy(alpha = 0.22f)
                                },
                                modifier = Modifier.testTag("player_decoder_toggle")
                            ) {
                                Text(
                                    text = if (useSoftwareDecoder) {
                                        stringResource(R.string.decoder_sw)
                                    } else {
                                        stringResource(R.string.decoder_hw)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (useSoftwareDecoder) AmberBoost else ElectricTeal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    val allTopTools = buildList {
                        if (controlsVisibility.showRotateButton) add(TopTool.ROTATE)
                        if (controlsVisibility.showSpeedButton) add(TopTool.SPEED)
                        if (controlsVisibility.showLoopButton) add(TopTool.LOOP)
                        if (controlsVisibility.showMuteButton) add(TopTool.MUTE)
                        if (controlsVisibility.showSleepButton) add(TopTool.SLEEP)
                        if (controlsVisibility.showBgPlayButton) add(TopTool.BG_PLAY)
                        if (controlsVisibility.showTracksButton) add(TopTool.TRACKS)
                    }

                    val displayTopTools = allTopTools.take(4)
                    val overflowTopTools = allTopTools.drop(4)
                    val hasTopOverflow = overflowTopTools.isNotEmpty()

                    if (allTopTools.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            displayTopTools.forEach { tool ->
                                renderTopToolButton(
                                    tool = tool,
                                    playbackSpeed = playbackSpeed,
                                    showSpeedPicker = showSpeedPicker,
                                    repeatMode = repeatMode,
                                    isMuted = isMuted,
                                    sleepMinutesRemaining = sleepMinutesRemaining,
                                    isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                                    subtitlesDisabled = subtitlesDisabled,
                                    subtitleTracks = subtitleTracks,
                                    onToggleRotation = onToggleRotation,
                                    onToggleSpeedPicker = { showSpeedPicker = !showSpeedPicker },
                                    onCycleRepeatMode = onCycleRepeatMode,
                                    onToggleMute = onToggleMute,
                                    onOpenSleepDialog = { showSleepDialog = true },
                                    onToggleBackgroundPlay = onToggleBackgroundPlay,
                                    onOpenTrackDialog = { showTrackDialog = true }
                                )
                            }

                            if (hasTopOverflow) {
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (showMoreTools) 180f else 0f,
                                    label = "more_tools_arrow_rotation"
                                )
                                Surface(
                                    onClick = { showMoreTools = !showMoreTools },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (showMoreTools) {
                                        ElectricTeal.copy(alpha = 0.22f)
                                    } else {
                                        Color.White.copy(alpha = 0.12f)
                                    },
                                    modifier = Modifier.testTag("player_more_tools_toggle")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = if (showMoreTools) "Hide Overflow" else "More Tools",
                                            tint = if (showMoreTools) ElectricTeal else Color.White,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .rotate(arrowRotation)
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = showMoreTools && hasTopOverflow,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    overflowTopTools.forEach { tool ->
                                        renderTopToolButton(
                                            tool = tool,
                                            playbackSpeed = playbackSpeed,
                                            showSpeedPicker = showSpeedPicker,
                                            repeatMode = repeatMode,
                                            isMuted = isMuted,
                                            sleepMinutesRemaining = sleepMinutesRemaining,
                                            isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                                            subtitlesDisabled = subtitlesDisabled,
                                            subtitleTracks = subtitleTracks,
                                            onToggleRotation = onToggleRotation,
                                            onToggleSpeedPicker = { showSpeedPicker = !showSpeedPicker },
                                            onCycleRepeatMode = onCycleRepeatMode,
                                            onToggleMute = onToggleMute,
                                            onOpenSleepDialog = { showSleepDialog = true },
                                            onToggleBackgroundPlay = onToggleBackgroundPlay,
                                            onOpenTrackDialog = { showTrackDialog = true }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Speed Picker Popover (opened from top Speed button)
                    AnimatedVisibility(visible = showSpeedPicker) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xD9111827),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                speedOptions.forEach { speed ->
                                    val selected = abs(playbackSpeed - speed) < 0.01f
                                    Surface(
                                        onClick = {
                                            onCycleSpeed(speed)
                                            showSpeedPicker = false
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) ElectricTeal else Color.Transparent
                                    ) {
                                        Text(
                                            text = "${speed}x",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) Color(0xFF03201D) else Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Bar: Slim Duration Bar + Primary Controls (Lock, Prev, Play/Pause, Next, PiP)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {

                    // Slim, Professional Duration Bar (thin bold-bordered line, not thick/chunky)
                    val displayPos = if (isScrubbingBar) scrubPositionMs else currentPositionMs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(displayPos),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White
                        )
                        SlimDurationBar(
                            positionMs = displayPos,
                            durationMs = durationMs,
                            onScrubChange = { next ->
                                isScrubbingBar = true
                                scrubPositionMs = next
                            },
                            onScrubFinished = { finalMs ->
                                isScrubbingBar = false
                                onSeekTo(finalMs)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                                .testTag("player_seek_slider")
                        )
                        Text(
                            text = FormatUtils.formatDuration(durationMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Primary Control Line: Lock | Previous · Play/Pause · Next | Fit + PiP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Lock
                        if (controlsVisibility.showLockButton) {
                            IconButton(
                                onClick = onToggleLock,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_lock_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = stringResource(R.string.lock_controls_desc),
                                    tint = Color.White
                                )
                            }
                        } else {
                            Spacer(Modifier.width(48.dp))
                        }

                        // Center: Previous, Play/Pause, Next
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (controlsVisibility.showPrevNextButtons) {
                                IconButton(
                                    onClick = onPreviousVideo,
                                    enabled = hasPrevious,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(PlayerOverlayScrim)
                                        .testTag("player_prev_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous Video",
                                        tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onTogglePlayPause,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(ElectricTeal.copy(alpha = 0.94f))
                                    .testTag("player_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.play_pause_desc),
                                    tint = Color(0xFF03201D),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            if (controlsVisibility.showPrevNextButtons) {
                                IconButton(
                                    onClick = onNextVideo,
                                    enabled = hasNext,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(PlayerOverlayScrim)
                                        .testTag("player_next_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next Video",
                                        tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Right side: Fit/Full directly to the LEFT of Picture-in-Picture
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (controlsVisibility.showFitButton) {
                                IconButton(
                                    onClick = onCycleAspectMode,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("player_fit_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = when (aspectMode) {
                                            PlayerAspectMode.FIT -> "Fit Screen"
                                            PlayerAspectMode.FILL -> "Full Screen"
                                            PlayerAspectMode.ZOOM -> "Crop Screen"
                                        },
                                        tint = if (aspectMode != PlayerAspectMode.FIT) ElectricTeal else Color.White
                                    )
                                }
                            }
                            if (controlsVisibility.showPipButton) {
                                IconButton(
                                    onClick = onEnterPip,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("player_pip_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = stringResource(R.string.pip_mode_desc),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Locked Screen Unlock Button (icon only, no text label)
        AnimatedVisibility(
            visible = isLocked && controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 16.dp)
        ) {
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PlayerOverlayScrim)
                    .testTag("player_unlock_button")
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = stringResource(R.string.unlock_controls_desc),
                    tint = ElectricTeal,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Gesture HUDs
        if (activeGesture == ActiveDragGesture.SEEK) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xD90B0E14),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = FormatUtils.formatDuration(previewSeekMs),
                        style = MaterialTheme.typography.headlineMedium,
                        color = ElectricTeal
                    )
                    Text(
                        text = FormatUtils.formatSignedDelta(seekDeltaMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        if (activeGesture == ActiveDragGesture.BRIGHTNESS) {
            VerticalGestureMeter(
                label = "$brightnessPercent%",
                fraction = brightnessPercent / 100f,
                accentColor = AmberBoost,
                icon = Icons.Default.BrightnessHigh,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 36.dp)
            )
        }

        if (activeGesture == ActiveDragGesture.VOLUME) {
            val isBoosted = volumePercent > 100
            VerticalGestureMeter(
                label = if (isBoosted) "BOOST $volumePercent%" else "$volumePercent%",
                fraction = (volumePercent / 200f).coerceIn(0f, 1f),
                accentColor = if (isBoosted) BoostGlowOrange else ElectricTeal,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 36.dp)
            )
        }

        // Feedback Banner
        val feedbackText = doubleTapFeedback ?: statusBanner ?: sleepMinutesRemaining?.let {
            "Sleep timer: ${it}m left"
        }
        AnimatedVisibility(
            visible = feedbackText != null && activeGesture == ActiveDragGesture.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 64.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xCC0B0E14)
            ) {
                Text(
                    text = feedbackText.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricTeal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Audio & Subtitle Track Picker Dialog
        if (showTrackDialog) {
            AlertDialog(
                onDismissRequest = { showTrackDialog = false },
                title = { Text("Audio & Subtitles") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Audio Tracks", style = MaterialTheme.typography.titleSmall)
                        }

                        if (audioTracks.isEmpty()) {
                            Text(
                                text = "Default audio track active",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            audioTracks.forEach { track ->
                                TrackOptionRow(
                                    label = track.label,
                                    selected = track.isSelected,
                                    onClick = {
                                        onSelectAudioTrack(track)
                                        showTrackDialog = false
                                    }
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Subtitles", style = MaterialTheme.typography.titleSmall)
                        }

                        TrackOptionRow(
                            label = "Off (Disable Subtitles)",
                            selected = subtitlesDisabled || subtitleTracks.none { it.isSelected },
                            onClick = {
                                onSelectSubtitleTrack(null)
                                showTrackDialog = false
                            }
                        )

                        subtitleTracks.forEach { track ->
                            TrackOptionRow(
                                label = track.label,
                                selected = !subtitlesDisabled && track.isSelected,
                                onClick = {
                                    onSelectSubtitleTrack(track)
                                    showTrackDialog = false
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                showTrackDialog = false
                                onPickExternalSubtitle()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Load Subtitle (.srt / .vtt / .ass)")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTrackDialog = false }) {
                        Text(stringResource(R.string.close_cta))
                    }
                }
            )
        }

        // Sleep Timer Dialog
        if (showSleepDialog) {
            AlertDialog(
                onDismissRequest = { showSleepDialog = false },
                title = { Text("Sleep Timer") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf<Int?>(null, 15, 30, 45, 60).forEach { mins ->
                            val label = if (mins == null) "Off" else "$mins minutes"
                            TrackOptionRow(
                                label = label,
                                selected = sleepMinutesRemaining == mins,
                                onClick = {
                                    onSetSleepTimerMinutes(mins)
                                    showSleepDialog = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepDialog = false }) {
                        Text(stringResource(R.string.close_cta))
                    }
                }
            )
        }
    }
}

/**
 * Slim, sleek, bold-bordered seek bar (3dp thin track with crisp 1dp border, not chunky).
 */
@Composable
private fun SlimDurationBar(
    positionMs: Long,
    durationMs: Long,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }

    val latestDuration by rememberUpdatedState(safeDuration)
    val latestOnChange by rememberUpdatedState(onScrubChange)
    val latestOnFinish by rememberUpdatedState(onScrubFinished)

    Box(
        modifier = modifier
            .height(34.dp)
            .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / barWidthPx).coerceIn(0f, 1f)
                    latestOnFinish((frac * latestDuration).toLong())
                }
            }
            .pointerInput(Unit) {
                var latestScrubMs = 0L
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        isDragging = true
                        val frac = (startOffset.x / barWidthPx).coerceIn(0f, 1f)
                        latestScrubMs = (frac * latestDuration).toLong()
                        latestOnChange(latestScrubMs)
                    },
                    onDragEnd = {
                        isDragging = false
                        latestOnFinish(latestScrubMs)
                    },
                    onDragCancel = {
                        isDragging = false
                        latestOnFinish(latestScrubMs)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                        latestScrubMs = (frac * latestDuration).toLong()
                        latestOnChange(latestScrubMs)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val trackHeight = 3.dp.toPx()
            val trackTop = (size.height - trackHeight) / 2f
            val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            val activeWidth = (size.width * fraction).coerceIn(0f, size.width)

            // Slim track background
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = corner
            )

            // Crisp bold border hairline around the slim duration bar
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx())
            )

            // Active progress fill
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = ElectricTeal,
                    topLeft = Offset(0f, trackTop),
                    size = Size(activeWidth, trackHeight),
                    cornerRadius = corner
                )
            }

            // Sleek minimal thumb dot
            val thumbRadius = if (isDragging) 6.5.dp.toPx() else 4.5.dp.toPx()
            drawCircle(
                color = Color.White,
                radius = thumbRadius + 1.dp.toPx(),
                center = Offset(activeWidth, size.height / 2f)
            )
            drawCircle(
                color = ElectricTeal,
                radius = thumbRadius,
                center = Offset(activeWidth, size.height / 2f)
            )
        }
    }
}

@Composable
private fun SecondaryToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeTint: Color = ElectricTeal,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (active) activeTint.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) activeTint else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = if (active) activeTint else Color.White.copy(alpha = 0.9f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TrackOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun VerticalGestureMeter(
    label: String,
    fraction: Float,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xCC0B0E14),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction.coerceIn(0.02f, 1f))
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

private enum class TopTool {
    ROTATE, SPEED, LOOP, MUTE, SLEEP, BG_PLAY, TRACKS
}

@Composable
private fun renderTopToolButton(
    tool: TopTool,
    playbackSpeed: Float,
    showSpeedPicker: Boolean,
    repeatMode: Int,
    isMuted: Boolean,
    sleepMinutesRemaining: Int?,
    isBackgroundPlayEnabled: Boolean,
    subtitlesDisabled: Boolean,
    subtitleTracks: List<MediaTrackOption>,
    onToggleRotation: () -> Unit,
    onToggleSpeedPicker: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenSleepDialog: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onOpenTrackDialog: () -> Unit
) {
    when (tool) {
        TopTool.ROTATE -> {
            SecondaryToolButton(
                icon = Icons.Default.ScreenRotation,
                label = "Rotate",
                active = false,
                onClick = onToggleRotation
            )
        }
        TopTool.SPEED -> {
            SecondaryToolButton(
                icon = Icons.Default.Speed,
                label = "${playbackSpeed}x",
                active = abs(playbackSpeed - 1.0f) > 0.01f || showSpeedPicker,
                onClick = onToggleSpeedPicker
            )
        }
        TopTool.LOOP -> {
            SecondaryToolButton(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                label = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Loop 1"
                    Player.REPEAT_MODE_ALL -> "Loop All"
                    else -> "Loop"
                },
                active = repeatMode != Player.REPEAT_MODE_OFF,
                onClick = onCycleRepeatMode
            )
        }
        TopTool.MUTE -> {
            SecondaryToolButton(
                icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                label = if (isMuted) "Unmute" else "Mute",
                active = isMuted,
                onClick = onToggleMute
            )
        }
        TopTool.SLEEP -> {
            SecondaryToolButton(
                icon = Icons.Default.Bedtime,
                label = sleepMinutesRemaining?.let { "${it}m" } ?: "Sleep",
                active = sleepMinutesRemaining != null,
                activeTint = AmberBoost,
                onClick = onOpenSleepDialog
            )
        }
        TopTool.BG_PLAY -> {
            SecondaryToolButton(
                icon = Icons.Default.Headphones,
                label = "BG Play",
                active = isBackgroundPlayEnabled,
                onClick = onToggleBackgroundPlay
            )
        }
        TopTool.TRACKS -> {
            SecondaryToolButton(
                icon = Icons.Default.Subtitles,
                label = "Tracks",
                active = !subtitlesDisabled && subtitleTracks.any { it.isSelected },
                onClick = onOpenTrackDialog
            )
        }
    }
}
