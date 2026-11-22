package com.example.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.R
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
    onDoubleTapSeek: (Boolean) -> Unit
) {
    if (isInPipMode) return

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeGesture by remember { mutableStateOf(ActiveDragGesture.NONE) }
    var dragStartPosMs by remember { mutableLongStateOf(0L) }
    var previewSeekMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var startBrightness by remember { mutableFloatStateOf(brightnessPercent.toFloat()) }
    var startVolume by remember { mutableFloatStateOf(volumePercent.toFloat()) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    var isScrubbingBar by remember { mutableStateOf(false) }
    var sliderPositionMs by remember { mutableFloatStateOf(0f) }

    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(isLocked, containerSize) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset ->
                        if (!isLocked && containerSize.width > 0) {
                            val isRight = offset.x > containerSize.width / 2f
                            onDoubleTapSeek(isRight)
                        }
                    }
                )
            }
            .pointerInput(isLocked, durationMs, currentPositionMs, brightnessPercent, volumePercent) {
                if (isLocked) return@pointerInput
                var totalDx = 0f
                var totalDy = 0f
                var gestureStartX = 0f
                detectDragGestures(
                    onDragStart = { startOffset ->
                        activeGesture = ActiveDragGesture.NONE
                        totalDx = 0f
                        totalDy = 0f
                        gestureStartX = startOffset.x
                        dragStartPosMs = currentPositionMs
                        previewSeekMs = currentPositionMs
                        seekDeltaMs = 0L
                        startBrightness = brightnessPercent.toFloat()
                        startVolume = volumePercent.toFloat()
                    },
                    onDragEnd = {
                        if (activeGesture == ActiveDragGesture.SEEK) {
                            onSeekTo(previewSeekMs)
                        }
                        activeGesture = ActiveDragGesture.NONE
                    },
                    onDragCancel = {
                        activeGesture = ActiveDragGesture.NONE
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDx += dragAmount.x
                        totalDy += dragAmount.y

                        if (activeGesture == ActiveDragGesture.NONE) {
                            if (abs(totalDx) > 22f && abs(totalDx) > abs(totalDy) * 1.2f) {
                                activeGesture = ActiveDragGesture.SEEK
                            } else if (abs(totalDy) > 22f && abs(totalDy) > abs(totalDx) * 1.2f) {
                                activeGesture = if (gestureStartX < containerSize.width / 2f) {
                                    ActiveDragGesture.BRIGHTNESS
                                } else {
                                    ActiveDragGesture.VOLUME
                                }
                            }
                        }

                        when (activeGesture) {
                            ActiveDragGesture.SEEK -> {
                                val widthPx = containerSize.width.coerceAtLeast(1).toFloat()
                                val maxSpanMs = (durationMs.coerceAtLeast(60_000L)).coerceAtMost(180_000L)
                                val deltaMs = ((totalDx / widthPx) * maxSpanMs).toLong()
                                seekDeltaMs = deltaMs
                                previewSeekMs = (dragStartPosMs + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
                            }
                            ActiveDragGesture.BRIGHTNESS -> {
                                val heightPx = (containerSize.height * 0.6f).coerceAtLeast(100f)
                                val deltaPercent = (-totalDy / heightPx) * 100f
                                val nextBrightness = (startBrightness + deltaPercent).roundToInt().coerceIn(2, 100)
                                onBrightnessChange(nextBrightness)
                            }
                            ActiveDragGesture.VOLUME -> {
                                val heightPx = (containerSize.height * 0.65f).coerceAtLeast(100f)
                                val deltaPercent = (-totalDy / heightPx) * 200f
                                val nextVol = (startVolume + deltaPercent).roundToInt().coerceIn(0, 200)
                                onVolumeChange(nextVol)
                            }
                            ActiveDragGesture.NONE -> Unit
                        }
                    }
                )
            }
    ) {
        // Controls Overlay
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
                        .height(120.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                            )
                        )
                )

                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    )

                    // Audio & Subtitle Track Picker
                    IconButton(
                        onClick = { showTrackDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_tracks_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Audio and Subtitles",
                            tint = if (!subtitlesDisabled && subtitleTracks.any { it.isSelected }) {
                                ElectricTeal
                            } else {
                                Color.White
                            }
                        )
                    }

                    // HW / SW Decoder Pill
                    Surface(
                        onClick = onToggleDecoder,
                        shape = RoundedCornerShape(8.dp),
                        color = if (useSoftwareDecoder) AmberBoost.copy(alpha = 0.25f) else ElectricTeal.copy(alpha = 0.22f),
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .testTag("player_decoder_toggle")
                    ) {
                        Text(
                            text = if (useSoftwareDecoder) stringResource(R.string.decoder_sw) else stringResource(R.string.decoder_hw),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (useSoftwareDecoder) AmberBoost else ElectricTeal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Background Play Toggle
                    IconButton(
                        onClick = onToggleBackgroundPlay,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_bg_play_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = stringResource(R.string.background_play_desc),
                            tint = if (isBackgroundPlayEnabled) ElectricTeal else Color.White
                        )
                    }

                    // PiP Button
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

                // Center Transport Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasPrevious) {
                        IconButton(
                            onClick = onPreviousVideo,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PlayerOverlayScrim)
                                .testTag("player_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Video",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { onSeekBy(-10_000L) },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(PlayerOverlayScrim)
                            .testTag("player_rewind_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = stringResource(R.string.rewind_10_desc),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(ElectricTeal.copy(alpha = 0.92f))
                            .testTag("player_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play_pause_desc),
                            tint = Color(0xFF03201D),
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = { onSeekBy(10_000L) },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(PlayerOverlayScrim)
                            .testTag("player_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = stringResource(R.string.forward_10_desc),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (hasNext) {
                        IconButton(
                            onClick = onNextVideo,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PlayerOverlayScrim)
                                .testTag("player_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Video",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Bottom Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    AnimatedVisibility(visible = showSpeedPicker) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xCC111827),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
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

                    val displayPos = if (isScrubbingBar) sliderPositionMs.toLong() else currentPositionMs
                    val safeDuration = durationMs.coerceAtLeast(1L)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(displayPos),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Slider(
                            value = displayPos.toFloat().coerceIn(0f, safeDuration.toFloat()),
                            onValueChange = { next ->
                                isScrubbingBar = true
                                sliderPositionMs = next
                            },
                            onValueChangeFinished = {
                                isScrubbingBar = false
                                onSeekTo(sliderPositionMs.toLong())
                            },
                            valueRange = 0f..safeDuration.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricTeal,
                                activeTrackColor = ElectricTeal,
                                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                                .testTag("player_seek_slider")
                        )
                        Text(
                            text = FormatUtils.formatDuration(durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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

                            IconButton(
                                onClick = onToggleRotation,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_rotate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = stringResource(R.string.rotate_screen_desc),
                                    tint = Color.White
                                )
                            }

                            IconButton(
                                onClick = onCycleRepeatMode,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_repeat_button")
                            ) {
                                Icon(
                                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                                        Icons.Default.RepeatOne
                                    } else {
                                        Icons.Default.Repeat
                                    },
                                    contentDescription = "Repeat Mode",
                                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) ElectricTeal else Color.White
                                )
                            }

                            IconButton(
                                onClick = { showSleepDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_sleep_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = "Sleep Timer",
                                    tint = if (sleepMinutesRemaining != null) AmberBoost else Color.White
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Mute / Volume Boost Badge
                            Surface(
                                onClick = onToggleMute,
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isMuted -> Color.White.copy(alpha = 0.14f)
                                    volumePercent > 100 -> BoostGlowOrange.copy(alpha = 0.25f)
                                    else -> Color.White.copy(alpha = 0.12f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) {
                                            Icons.AutoMirrored.Filled.VolumeOff
                                        } else {
                                            Icons.AutoMirrored.Filled.VolumeUp
                                        },
                                        contentDescription = "Toggle Mute",
                                        tint = if (volumePercent > 100 && !isMuted) BoostGlowOrange else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isMuted) "Mute" else "$volumePercent%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (volumePercent > 100 && !isMuted) BoostGlowOrange else Color.White
                                    )
                                }
                            }

                            Spacer(Modifier.width(6.dp))

                            // Speed Button
                            Surface(
                                onClick = { showSpeedPicker = !showSpeedPicker },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.testTag("player_speed_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = stringResource(R.string.playback_speed_desc),
                                        tint = ElectricTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "${playbackSpeed}x",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            IconButton(
                                onClick = onCycleAspectMode,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_aspect_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "${stringResource(R.string.aspect_ratio_desc)}: ${aspectMode.label}",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Locked Screen Unlock Button
        AnimatedVisibility(
            visible = isLocked && controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = PlayerOverlayScrim,
                modifier = Modifier
                    .clickable(onClick = onToggleLock)
                    .testTag("player_unlock_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = stringResource(R.string.unlock_controls_desc),
                        tint = ElectricTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Unlock Screen",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                }
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

        // Feedback Pill
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
                .padding(top = 68.dp)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
