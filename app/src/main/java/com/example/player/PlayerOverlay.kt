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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    aspectMode: PlayerAspectMode,
    controlsVisible: Boolean,
    isInPipMode: Boolean,
    brightnessPercent: Int,
    volumePercent: Int,
    doubleTapFeedback: String?,
    statusBanner: String?,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onToggleLock: () -> Unit,
    onToggleRotation: () -> Unit,
    onCycleSpeed: (Float) -> Unit,
    onToggleDecoder: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onCycleAspectMode: () -> Unit,
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
        // Controls Overlay (when unlocked and visible)
        AnimatedVisibility(
            visible = controlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top gradient bar
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
                // Bottom gradient bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                            .padding(horizontal = 8.dp)
                    )

                    // HW / SW Decoder Pill
                    Surface(
                        onClick = onToggleDecoder,
                        shape = RoundedCornerShape(8.dp),
                        color = if (useSoftwareDecoder) AmberBoost.copy(alpha = 0.25f) else ElectricTeal.copy(alpha = 0.22f),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSeekBy(-10_000L) },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(PlayerOverlayScrim)
                            .testTag("player_rewind_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = stringResource(R.string.rewind_10_desc),
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
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
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(PlayerOverlayScrim)
                            .testTag("player_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = stringResource(R.string.forward_10_desc),
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Bottom Bar (Seekbar + Action Strip)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Optional Speed Selector Bar
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

                    // Seekbar Row
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

                    // Quick Action Buttons
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

                            // Volume Boost Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (volumePercent > 100) BoostGlowOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = if (volumePercent > 100) BoostGlowOrange else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "$volumePercent%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (volumePercent > 100) BoostGlowOrange else Color.White
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Speed Button
                            Surface(
                                onClick = { showSpeedPicker = !showSpeedPicker },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.testTag("player_speed_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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

                            Spacer(Modifier.width(6.dp))

                            // Aspect Ratio Button
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

        // Live Gesture HUDs (Seek / Brightness / Volume Boost)
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

        // Double-Tap & Status Pill Feedback
        val feedbackText = doubleTapFeedback ?: statusBanner
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
