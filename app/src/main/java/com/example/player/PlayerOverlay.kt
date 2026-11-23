package com.example.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.data.model.DoubleClickAction
import com.example.data.model.MediaTrackOption
import com.example.data.model.PlayerAspectMode
import com.example.data.model.PlayerControlButton
import com.example.data.model.ProgressBarStyle
import com.example.data.model.SingleClickAction
import com.example.data.model.UserPreferences
import com.example.ui.components.FitScreenIcon
import com.example.ui.theme.BoostOrange
import com.example.ui.theme.DarkSecondary
import com.example.util.FormatUtils
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME }

private enum class TopToolItem { ZOOM, MUTE, ROTATE, SPEED, LOOP, SLEEP, BG_PLAY, TRACKS }

@Composable
fun PlayerOverlay(
    title: String,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    useSoftwareDecoder: Boolean,
    repeatMode: Int,
    sleepMinutesRemaining: Int?,
    isLocked: Boolean,
    isBackgroundPlayEnabled: Boolean,
    controlsVisible: Boolean,
    aspectMode: PlayerAspectMode,
    quickZoomScale: Float,
    isRotated: Boolean,
    brightnessPercent: Int,
    volumePercent: Int,
    isMuted: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    preferences: UserPreferences,
    statusBanner: String?,
    audioTracks: List<MediaTrackOption>,
    subtitleTracks: List<MediaTrackOption>,
    onToggleControls: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleLock: () -> Unit,
    onCycleAspectMode: () -> Unit,
    onCycleQuickZoom: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleDecoder: () -> Unit,
    onToggleRotation: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectSleepMinutes: (Int?) -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onSelectAudioTrack: (MediaTrackOption) -> Unit,
    onSelectSubtitleTrack: (MediaTrackOption?) -> Unit,
    onImportExternalSubtitle: () -> Unit,
    onChangeBrightness: (Int) -> Unit,
    onChangeVolume: (Int) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var dragStartPosMs by remember { mutableLongStateOf(0L) }
    var previewSeekMs by remember { mutableLongStateOf(0L) }
    var dragSeekDeltaMs by remember { mutableLongStateOf(0L) }
    var dragStartBrightness by remember { mutableIntStateOf(75) }
    var dragStartVolume by remember { mutableIntStateOf(100) }
    var dragAccumX by remember { mutableFloatStateOf(0f) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    val latestLocked by rememberUpdatedState(isLocked)
    val latestPrefs by rememberUpdatedState(preferences)
    val latestPosMs by rememberUpdatedState(currentPosMs)
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestBrightness by rememberUpdatedState(brightnessPercent)
    val latestVolume by rememberUpdatedState(volumePercent)
    val latestOnToggleControls by rememberUpdatedState(onToggleControls)
    val latestOnPlayPause by rememberUpdatedState(onPlayPause)
    val latestOnToggleMute by rememberUpdatedState(onToggleMute)
    val latestOnSeekTo by rememberUpdatedState(onSeekTo)
    val latestOnSeekBy by rememberUpdatedState(onSeekBy)
    val latestOnChangeBrightness by rememberUpdatedState(onChangeBrightness)
    val latestOnChangeVolume by rememberUpdatedState(onChangeVolume)

    var showMoreTools by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var isScrubbingBar by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }

    val allTopTools = remember(preferences.hiddenButtons) {
        buildList {
            if (preferences.isButtonVisible(PlayerControlButton.ZOOM)) add(TopToolItem.ZOOM)
            if (preferences.isButtonVisible(PlayerControlButton.MUTE)) add(TopToolItem.MUTE)
            if (preferences.isButtonVisible(PlayerControlButton.ROTATE)) add(TopToolItem.ROTATE)
            if (preferences.isButtonVisible(PlayerControlButton.SPEED)) add(TopToolItem.SPEED)
            if (preferences.isButtonVisible(PlayerControlButton.LOOP)) add(TopToolItem.LOOP)
            if (preferences.isButtonVisible(PlayerControlButton.SLEEP)) add(TopToolItem.SLEEP)
            if (preferences.isButtonVisible(PlayerControlButton.BG_PLAY)) add(TopToolItem.BG_PLAY)
            if (preferences.isButtonVisible(PlayerControlButton.TRACKS)) add(TopToolItem.TRACKS)
        }
    }

    val displayedTools = allTopTools.take(4)
    val overflowTools = allTopTools.drop(4)

    androidx.compose.runtime.LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            showSpeedPicker = false
        }
    }

    val doubleTapEnabled = !isLocked && preferences.doubleClickAction != DoubleClickAction.NONE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(doubleTapEnabled) {
                detectTapGestures(
                    onTap = {
                        showSpeedPicker = false
                        if (latestLocked) {
                            latestOnToggleControls()
                        } else {
                            when (latestPrefs.singleClickAction) {
                                SingleClickAction.TOGGLE_CONTROLS -> latestOnToggleControls()
                                SingleClickAction.PLAY_PAUSE -> latestOnPlayPause()
                                SingleClickAction.TOGGLE_MUTE -> latestOnToggleMute()
                                SingleClickAction.NONE -> Unit
                            }
                        }
                    },
                    onDoubleTap = if (doubleTapEnabled) {
                        { offset ->
                            if (!latestLocked) {
                                val leftHalf = offset.x < boxSize.width / 2f
                                when (latestPrefs.doubleClickAction) {
                                    DoubleClickAction.PLAY_PAUSE -> latestOnPlayPause()
                                    DoubleClickAction.SEEK_10S -> latestOnSeekBy(if (leftHalf) -10_000L else 10_000L)
                                    DoubleClickAction.SEEK_20S -> latestOnSeekBy(if (leftHalf) -20_000L else 20_000L)
                                    DoubleClickAction.NONE -> Unit
                                }
                            }
                        }
                    } else null
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { _ ->
                        dragMode = DragMode.NONE
                        dragAccumX = 0f
                        dragAccumY = 0f
                        dragStartPosMs = latestPosMs
                        previewSeekMs = latestPosMs
                        dragSeekDeltaMs = 0L
                        dragStartBrightness = latestBrightness
                        dragStartVolume = latestVolume
                    },
                    onDrag = { change, dragAmount ->
                        if (latestLocked || !latestPrefs.enableSwipeGestures || latestPrefs.oneFingerGesture == "None") {
                            return@detectDragGestures
                        }
                        change.consume()
                        dragAccumX += dragAmount.x
                        dragAccumY += dragAmount.y

                        if (dragMode == DragMode.NONE) {
                            if (abs(dragAccumX) > 20f && abs(dragAccumX) > abs(dragAccumY) * 1.15f) {
                                dragMode = DragMode.SEEK
                            } else if (
                                latestPrefs.oneFingerGesture != "Seek & Controls" &&
                                abs(dragAccumY) > 16f &&
                                abs(dragAccumY) > abs(dragAccumX) * 1.15f
                            ) {
                                dragMode = if (change.position.x < boxSize.width / 2f) {
                                    DragMode.BRIGHTNESS
                                } else {
                                    DragMode.VOLUME
                                }
                            }
                        }

                        when (dragMode) {
                            DragMode.SEEK -> {
                                val widthPx = boxSize.width.coerceAtLeast(300).toFloat()
                                val realDur = latestDurationMs.coerceAtLeast(1_000L)
                                val rawDelta = if (latestPrefs.gestureSeekStepSeconds <= 0) {
                                    val spanMs = realDur.coerceIn(15_000L, 120_000L)
                                    ((dragAccumX / widthPx) * spanMs).toLong()
                                } else {
                                    val stepMs = latestPrefs.gestureSeekStepSeconds * 1000L
                                    val steps = (dragAccumX / (widthPx * 0.14f)).toInt()
                                    steps * stepMs
                                }
                                dragSeekDeltaMs = rawDelta
                                previewSeekMs = (dragStartPosMs + rawDelta).coerceIn(0L, realDur)
                            }
                            DragMode.BRIGHTNESS -> {
                                val heightPx = (boxSize.height * 0.45f).coerceAtLeast(180f)
                                val deltaPct = ((-dragAccumY / heightPx) * 100f).roundToInt()
                                latestOnChangeBrightness((dragStartBrightness + deltaPct).coerceIn(5, 100))
                            }
                            DragMode.VOLUME -> {
                                val heightPx = (boxSize.height * 0.45f).coerceAtLeast(180f)
                                val deltaPct = ((-dragAccumY / heightPx) * 200f).roundToInt()
                                latestOnChangeVolume((dragStartVolume + deltaPct).coerceIn(0, 200))
                            }
                            DragMode.NONE -> Unit
                        }
                    },
                    onDragEnd = {
                        if (dragMode == DragMode.SEEK) {
                            latestOnSeekTo(previewSeekMs)
                        }
                        dragMode = DragMode.NONE
                    },
                    onDragCancel = { dragMode = DragMode.NONE }
                )
            }
    ) {
        // Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Gradient Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.4f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (volumePercent > 100) {
                            Text(
                                text = "BOOST $volumePercent%",
                                color = BoostOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BoostOrange.copy(alpha = 0.22f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        if (preferences.isButtonVisible(PlayerControlButton.DECODER)) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (useSoftwareDecoder) DarkSecondary.copy(alpha = 0.24f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    )
                                    .clickable(onClick = onToggleDecoder)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Memory,
                                    contentDescription = null,
                                    tint = if (useSoftwareDecoder) DarkSecondary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (useSoftwareDecoder) "SW" else "HW",
                                    color = if (useSoftwareDecoder) DarkSecondary else MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (allTopTools.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            displayedTools.forEach { tool ->
                                TopToolChip(
                                    tool = tool,
                                    playbackSpeed = playbackSpeed,
                                    repeatMode = repeatMode,
                                    quickZoomScale = quickZoomScale,
                                    isRotated = isRotated,
                                    isMuted = isMuted,
                                    sleepMinutesRemaining = sleepMinutesRemaining,
                                    isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                                    showSpeedPicker = showSpeedPicker,
                                    onZoomClick = onCycleQuickZoom,
                                    onRotate = onToggleRotation,
                                    onSpeedClick = { showSpeedPicker = !showSpeedPicker },
                                    onLoopClick = onCycleRepeat,
                                    onMuteClick = onToggleMute,
                                    onSleepClick = { showSleepDialog = true },
                                    onBgPlayClick = onToggleBackgroundPlay,
                                    onTracksClick = { showTrackDialog = true }
                                )
                            }
                            if (overflowTools.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (showMoreTools) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f))
                                        .clickable { showMoreTools = !showMoreTools }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "More tools",
                                        tint = if (showMoreTools) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(if (showMoreTools) 180f else 0f)
                                    )
                                }
                                if (showMoreTools) {
                                    overflowTools.forEach { tool ->
                                        TopToolChip(
                                            tool = tool,
                                            playbackSpeed = playbackSpeed,
                                            repeatMode = repeatMode,
                                            quickZoomScale = quickZoomScale,
                                            isRotated = isRotated,
                                            isMuted = isMuted,
                                            sleepMinutesRemaining = sleepMinutesRemaining,
                                            isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                                            showSpeedPicker = showSpeedPicker,
                                            onZoomClick = onCycleQuickZoom,
                                            onRotate = onToggleRotation,
                                            onSpeedClick = { showSpeedPicker = !showSpeedPicker },
                                            onLoopClick = onCycleRepeat,
                                            onMuteClick = onToggleMute,
                                            onSleepClick = { showSleepDialog = true },
                                            onBgPlayClick = onToggleBackgroundPlay,
                                            onTracksClick = { showTrackDialog = true }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showSpeedPicker) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF111827).copy(alpha = 0.92f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                val active = abs(playbackSpeed - spd) < 0.01f
                                Text(
                                    text = "${spd}x",
                                    color = if (active) Color(0xFF03201D) else Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable {
                                            onSelectSpeed(spd)
                                            showSpeedPicker = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Gradient Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    val activePos = if (isScrubbingBar) scrubMs else currentPosMs
                    val dur = durationMs.coerceAtLeast(1L)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(activePos),
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.width(46.dp)
                        )
                        val progressFrac = (activePos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                        if (preferences.progressBarStyle == ProgressBarStyle.FLAT) {
                            var flatBarWidth by remember { mutableFloatStateOf(1f) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .padding(horizontal = 8.dp)
                                    .onSizeChanged { flatBarWidth = it.width.coerceAtLeast(1).toFloat() }
                                    .pointerInput(dur) {
                                        detectTapGestures { offset ->
                                            val target = ((offset.x / flatBarWidth).coerceIn(0f, 1f) * dur).toLong()
                                            onSeekTo(target)
                                        }
                                    }
                                    .pointerInput(dur) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                isScrubbingBar = true
                                                scrubMs = ((offset.x / flatBarWidth).coerceIn(0f, 1f) * dur).toLong()
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                scrubMs = ((change.position.x / flatBarWidth).coerceIn(0f, 1f) * dur).toLong()
                                            },
                                            onDragEnd = {
                                                isScrubbingBar = false
                                                onSeekTo(scrubMs)
                                            },
                                            onDragCancel = { isScrubbingBar = false }
                                        )
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.White.copy(alpha = 0.25f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progressFrac)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        } else {
                            Slider(
                                value = progressFrac,
                                onValueChange = { frac ->
                                    isScrubbingBar = true
                                    scrubMs = (frac * dur).toLong()
                                },
                                onValueChangeFinished = {
                                    isScrubbingBar = false
                                    onSeekTo(scrubMs)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = FormatUtils.formatDuration(durationMs),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            modifier = Modifier.width(46.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.width(88.dp)) {
                            if (preferences.isButtonVisible(PlayerControlButton.LOCK)) {
                                IconButton(onClick = onToggleLock) {
                                    Icon(Icons.Rounded.Lock, contentDescription = "Lock", tint = Color.White)
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (preferences.isButtonVisible(PlayerControlButton.PREV_NEXT)) {
                                IconButton(
                                    enabled = hasPrevious,
                                    onClick = onPrevious,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                ) {
                                    Icon(
                                        Icons.Rounded.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.35f)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onPlayPause,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color(0xFF03201D),
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            if (preferences.isButtonVisible(PlayerControlButton.PREV_NEXT)) {
                                IconButton(
                                    enabled = hasNext,
                                    onClick = onNext,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                ) {
                                    Icon(
                                        Icons.Rounded.SkipNext,
                                        contentDescription = "Next",
                                        tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.wrapContentWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (preferences.isButtonVisible(PlayerControlButton.FIT)) {
                                IconButton(onClick = onCycleAspectMode) {
                                    FitScreenIcon(
                                        tint = if (aspectMode != PlayerAspectMode.FIT) MaterialTheme.colorScheme.primary else Color.White,
                                        size = 21.dp
                                    )
                                }
                            }
                            if (preferences.isButtonVisible(PlayerControlButton.PIP)) {
                                IconButton(onClick = onEnterPip) {
                                    Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                                }
                            }
                        }
                    }
                }
                }

                // Right-side middle back button (hides/shows with controls on single click)
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back / Turn Off Video",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Unlock button when locked
        if (isLocked && controlsVisible) {
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.72f))
            ) {
                Icon(Icons.Rounded.LockOpen, contentDescription = "Unlock", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Gesture HUDs
        if (dragMode == DragMode.SEEK) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0B0E14).copy(alpha = 0.85f))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(FormatUtils.formatDuration(previewSeekMs), color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(FormatUtils.formatSignedDelta(dragSeekDeltaMs), color = Color.White, fontSize = 12.sp)
            }
        } else if (dragMode == DragMode.BRIGHTNESS) {
            VerticalGestureHud(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 28.dp),
                icon = Icons.Rounded.BrightnessHigh,
                label = "$brightnessPercent%",
                fraction = brightnessPercent / 100f,
                accentColor = DarkSecondary
            )
        } else if (dragMode == DragMode.VOLUME) {
            VerticalGestureHud(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp),
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                label = if (volumePercent > 100) "BOOST $volumePercent%" else "$volumePercent%",
                fraction = volumePercent / 200f,
                accentColor = if (volumePercent > 100) BoostOrange else MaterialTheme.colorScheme.primary
            )
        }

        // Status Feedback Pill
        if (!statusBanner.isNullOrBlank() && dragMode == DragMode.NONE) {
            Text(
                text = statusBanner,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0B0E14).copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    if (showTrackDialog) {
        AlertDialog(
            onDismissRequest = { showTrackDialog = false },
            title = { Text("Audio & Subtitles", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Volume / Audio Boost ($volumePercent%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Slider(
                        value = volumePercent.toFloat(),
                        onValueChange = { onChangeVolume(it.toInt()) },
                        valueRange = 0f..200f,
                        colors = SliderDefaults.colors(
                            thumbColor = if (volumePercent > 100) BoostOrange else MaterialTheme.colorScheme.primary,
                            activeTrackColor = if (volumePercent > 100) BoostOrange else MaterialTheme.colorScheme.primary
                        )
                    )
                    if (audioTracks.isNotEmpty()) {
                        Text("Audio Tracks", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        audioTracks.forEach { tr ->
                            TrackChoiceRow(tr.label, tr.isSelected) {
                                onSelectAudioTrack(tr)
                                showTrackDialog = false
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Subtitles", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    TrackChoiceRow("Off (Disable Subtitles)", subtitleTracks.none { it.isSelected }) {
                        onSelectSubtitleTrack(null)
                        showTrackDialog = false
                    }
                    subtitleTracks.forEach { tr ->
                        TrackChoiceRow(tr.label, tr.isSelected) {
                            onSelectSubtitleTrack(tr)
                            showTrackDialog = false
                        }
                    }
                    OutlinedButton(
                        onClick = { showTrackDialog = false; onImportExternalSubtitle() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.FileOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Load External Subtitle (.srt / .vtt / .ass)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackDialog = false }) { Text("Close") }
            }
        )
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Sleep Timer", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Int?>(null, 15, 30, 45, 60).forEach { mins ->
                        TrackChoiceRow(
                            label = if (mins == null) "Off" else "$mins minutes",
                            selected = sleepMinutesRemaining == mins
                        ) {
                            onSelectSleepMinutes(mins)
                            showSleepDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun TopToolChip(
    tool: TopToolItem,
    playbackSpeed: Float,
    repeatMode: Int,
    quickZoomScale: Float,
    isRotated: Boolean,
    isMuted: Boolean,
    sleepMinutesRemaining: Int?,
    isBackgroundPlayEnabled: Boolean,
    showSpeedPicker: Boolean,
    onZoomClick: () -> Unit,
    onRotate: () -> Unit,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onMuteClick: () -> Unit,
    onSleepClick: () -> Unit,
    onBgPlayClick: () -> Unit,
    onTracksClick: () -> Unit
) {
    val (icon, label, active) = when (tool) {
        TopToolItem.ZOOM -> Triple(
            Icons.Rounded.ZoomIn,
            if (abs(quickZoomScale - 1.0f) > 0.05f) "${quickZoomScale}x" else "Zoom",
            abs(quickZoomScale - 1.0f) > 0.05f
        )
        TopToolItem.ROTATE -> Triple(Icons.Rounded.ScreenRotation, "Rotate", isRotated)
        TopToolItem.SPEED -> Triple(Icons.Rounded.Speed, "${playbackSpeed}x", abs(playbackSpeed - 1f) > 0.01f || showSpeedPicker)
        TopToolItem.LOOP -> Triple(
            if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> "Loop 1"
                Player.REPEAT_MODE_ALL -> "Loop All"
                else -> "Loop"
            },
            repeatMode != Player.REPEAT_MODE_OFF
        )
        TopToolItem.MUTE -> Triple(
            if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            if (isMuted) "Unmute" else "Mute",
            isMuted
        )
        TopToolItem.SLEEP -> Triple(
            Icons.Rounded.NightsStay,
            if (sleepMinutesRemaining != null) "${sleepMinutesRemaining}m" else "Sleep",
            sleepMinutesRemaining != null
        )
        TopToolItem.BG_PLAY -> Triple(Icons.Rounded.Headphones, "BG Play", isBackgroundPlayEnabled)
        TopToolItem.TRACKS -> Triple(Icons.Rounded.Subtitles, "Tracks", false)
    }

    val onClick = when (tool) {
        TopToolItem.ZOOM -> onZoomClick
        TopToolItem.ROTATE -> onRotate
        TopToolItem.SPEED -> onSpeedClick
        TopToolItem.LOOP -> onLoopClick
        TopToolItem.MUTE -> onMuteClick
        TopToolItem.SLEEP -> onSleepClick
        TopToolItem.BG_PLAY -> onBgPlayClick
        TopToolItem.TRACKS -> onTracksClick
    }

    val accent = if (tool == TopToolItem.SLEEP && active) DarkSecondary else MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.09f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (active) accent else Color.White, modifier = Modifier.size(17.dp))
        Text(label, color = if (active) accent else Color.White, fontSize = 9.5.sp, maxLines = 1)
    }
}

@Composable
private fun VerticalGestureHud(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    fraction: Float,
    accentColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0B0E14).copy(alpha = 0.85f))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(110.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction.coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TrackChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}
