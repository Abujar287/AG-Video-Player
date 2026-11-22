package com.example.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.local.VeloDatabase
import com.example.data.repository.PlaybackRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.theme.VeloPlayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var prefsRepository: UserPreferencesRepository

    private var videoUri: String = ""
    private var videoTitle: String = "Video"
    private var isInPipState by mutableStateOf(false)
    private var autoPipEnabled by mutableStateOf(true)
    private var pipSourceRect: android.graphics.Rect? = null

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val player = PlayerSessionManager.playerFlow.value ?: return
            when (intent?.action) {
                ACTION_PIP_REWIND -> player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                ACTION_PIP_FORWARD -> {
                    val dur = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(dur))
                }
                ACTION_PIP_PLAY_PAUSE -> {
                    if (player.isPlaying) player.pause() else player.play()
                    updatePipParams(player)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        playbackRepository = PlaybackRepository(VeloDatabase.getInstance(this).playbackHistoryDao())
        prefsRepository = UserPreferencesRepository(this)

        handleLaunchIntent(intent)

        // Ensure PlaybackService is active for MediaSession / optional background play
        startService(Intent(this, PlaybackService::class.java))

        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter().apply {
                addAction(ACTION_PIP_PLAY_PAUSE)
                addAction(ACTION_PIP_REWIND)
                addAction(ACTION_PIP_FORWARD)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            addOnUserLeaveHintListener {
                val player = PlayerSessionManager.playerFlow.value
                if (autoPipEnabled &&
                    player?.isPlaying == true &&
                    !PlayerSessionManager.isBackgroundPlayEnabled.value
                ) {
                    enterPipModeNow()
                }
            }
        }

        setContent {
            val scope = rememberCoroutineScope()
            val player by PlayerSessionManager.playerFlow.collectAsStateWithLifecycle()
            val useSoftwareDecoder by PlayerSessionManager.useSoftwareDecoder.collectAsStateWithLifecycle()
            val isBackgroundPlayEnabled by PlayerSessionManager.isBackgroundPlayEnabled.collectAsStateWithLifecycle()
            val volumePercent by PlayerSessionManager.volumePercent.collectAsStateWithLifecycle()
            val prefs by prefsRepository.preferences.collectAsStateWithLifecycle(
                initialValue = com.example.data.repository.UserPreferences()
            )

            LaunchedEffect(prefs.autoPipOnHome) {
                autoPipEnabled = prefs.autoPipOnHome
            }

            var isPlaying by remember { mutableStateOf(false) }
            var currentPosMs by remember { mutableLongStateOf(0L) }
            var durationMs by remember { mutableLongStateOf(0L) }
            var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
            var isLocked by remember { mutableStateOf(false) }
            var controlsVisible by remember { mutableStateOf(true) }
            var aspectMode by remember { mutableStateOf(PlayerAspectMode.FIT) }
            var brightnessPercent by remember { mutableIntStateOf(65) }
            var doubleTapFeedback by remember { mutableStateOf<String?>(null) }
            var statusBanner by remember { mutableStateOf<String?>(null) }

            // Initialize playback & restore exact saved timestamp
            LaunchedEffect(videoUri) {
                if (videoUri.isBlank()) return@LaunchedEffect
                val saved = playbackRepository.getSavedState(videoUri)
                val initialPrefs = prefsRepository.preferences.first()
                val startPos = saved?.positionMs ?: 0L
                val startSpeed = saved?.playbackSpeed ?: 1.0f
                val startSw = saved?.useSoftwareDecoder ?: initialPrefs.defaultSoftwareDecoder
                playbackSpeed = startSpeed

                PlayerSessionManager.prepareMedia(
                    context = this@PlayerActivity,
                    uriString = videoUri,
                    title = videoTitle,
                    resumePositionMs = startPos,
                    playbackSpeed = startSpeed,
                    preferSoftware = startSw
                )
                if (startPos > 1_500L) {
                    statusBanner = "Resumed from ${com.example.util.FormatUtils.formatDuration(startPos)}"
                }
            }

            // Observe player state and auto-save playback position every 3 seconds
            DisposableEffect(player) {
                val activePlayer = player ?: return@DisposableEffect onDispose {}
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        updatePipParams(activePlayer)
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        updatePipParams(activePlayer)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            durationMs = activePlayer.duration.coerceAtLeast(0L)
                            updatePipParams(activePlayer)
                        }
                    }
                }
                activePlayer.addListener(listener)
                isPlaying = activePlayer.isPlaying
                durationMs = activePlayer.duration.coerceAtLeast(0L)
                onDispose {
                    activePlayer.removeListener(listener)
                }
            }

            LaunchedEffect(player) {
                var ticks = 0
                while (true) {
                    val activePlayer = player
                    if (activePlayer != null) {
                        currentPosMs = activePlayer.currentPosition.coerceAtLeast(0L)
                        val dur = activePlayer.duration.coerceAtLeast(0L)
                        if (dur > 0L) durationMs = dur
                        isPlaying = activePlayer.isPlaying
                        if (ticks % 6 == 0 && videoUri.isNotBlank() && dur > 0L) {
                            playbackRepository.savePlaybackState(
                                videoUri = videoUri,
                                title = videoTitle,
                                positionMs = currentPosMs,
                                durationMs = dur,
                                playbackSpeed = playbackSpeed,
                                useSoftwareDecoder = useSoftwareDecoder
                            )
                        }
                    }
                    ticks++
                    delay(500L)
                }
            }

            // Auto-hide controls after 3.5s while playing
            LaunchedEffect(controlsVisible, isPlaying, isLocked) {
                if (controlsVisible && (isPlaying || isLocked)) {
                    delay(3_500L)
                    controlsVisible = false
                }
            }

            LaunchedEffect(doubleTapFeedback) {
                if (doubleTapFeedback != null) {
                    delay(900L)
                    doubleTapFeedback = null
                }
            }

            LaunchedEffect(statusBanner) {
                if (statusBanner != null) {
                    delay(2_200L)
                    statusBanner = null
                }
            }

            VeloPlayTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .onGloballyPositioned { coords ->
                            pipSourceRect = coords.boundsInWindow().toAndroidRectF().toRect()
                            updatePipParams(player)
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                keepScreenOn = true
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        update = { view ->
                            if (view.player !== player) {
                                view.player = player
                            }
                            view.resizeMode = when (aspectMode) {
                                PlayerAspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                PlayerAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                PlayerAspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    PlayerOverlay(
                        title = videoTitle,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPosMs,
                        durationMs = durationMs,
                        playbackSpeed = playbackSpeed,
                        useSoftwareDecoder = useSoftwareDecoder,
                        isLocked = isLocked,
                        isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                        aspectMode = aspectMode,
                        controlsVisible = controlsVisible,
                        isInPipMode = isInPipState,
                        brightnessPercent = brightnessPercent,
                        volumePercent = volumePercent,
                        doubleTapFeedback = doubleTapFeedback,
                        statusBanner = statusBanner,
                        onBack = {
                            persistPositionBlocking()
                            if (!isBackgroundPlayEnabled) {
                                player?.pause()
                            }
                            finish()
                        },
                        onToggleControls = { controlsVisible = !controlsVisible },
                        onTogglePlayPause = {
                            val p = player ?: return@PlayerOverlay
                            if (p.isPlaying) p.pause() else p.play()
                            controlsVisible = true
                        },
                        onSeekTo = { target ->
                            player?.seekTo(target.coerceIn(0L, durationMs.coerceAtLeast(0L)))
                            currentPosMs = target
                        },
                        onSeekBy = { delta ->
                            val p = player ?: return@PlayerOverlay
                            val next = (p.currentPosition + delta).coerceIn(0L, durationMs.coerceAtLeast(0L))
                            p.seekTo(next)
                            currentPosMs = next
                        },
                        onToggleLock = {
                            isLocked = !isLocked
                            controlsVisible = true
                            statusBanner = if (isLocked) "Controls Locked" else "Controls Unlocked"
                        },
                        onToggleRotation = {
                            val currentOrientation = resources.configuration.orientation
                            requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        },
                        onCycleSpeed = { newSpeed ->
                            playbackSpeed = newSpeed
                            player?.setPlaybackSpeed(newSpeed)
                            statusBanner = "Speed ${newSpeed}x"
                        },
                        onToggleDecoder = {
                            val nextSw = !useSoftwareDecoder
                            PlayerSessionManager.switchDecoder(this@PlayerActivity, nextSw)
                            scope.launch { prefsRepository.setDefaultSoftwareDecoder(nextSw) }
                            statusBanner = if (nextSw) "Switched to SW Decoder" else "Switched to HW Decoder"
                        },
                        onEnterPip = { enterPipModeNow() },
                        onToggleBackgroundPlay = {
                            val next = !isBackgroundPlayEnabled
                            PlayerSessionManager.setBackgroundPlayEnabled(next)
                            updatePipParams(player)
                            statusBanner = if (next) {
                                "Background Audio Play Enabled"
                            } else {
                                "Background Audio Play Disabled"
                            }
                        },
                        onCycleAspectMode = {
                            val modes = PlayerAspectMode.entries
                            aspectMode = modes[(aspectMode.ordinal + 1) % modes.size]
                            statusBanner = "Screen: ${aspectMode.label}"
                        },
                        onBrightnessChange = { nextPercent ->
                            brightnessPercent = nextPercent
                            val lp = window.attributes
                            lp.screenBrightness = (nextPercent / 100f).coerceIn(0.02f, 1.0f)
                            window.attributes = lp
                        },
                        onVolumeChange = { nextPercent ->
                            PlayerSessionManager.setVolumeWithBoost(this@PlayerActivity, nextPercent)
                        },
                        onDoubleTapSeek = { isRight ->
                            val delta = if (isRight) 10_000L else -10_000L
                            val p = player ?: return@PlayerOverlay
                            val next = (p.currentPosition + delta).coerceIn(0L, durationMs.coerceAtLeast(0L))
                            p.seekTo(next)
                            currentPosMs = next
                            doubleTapFeedback = if (isRight) "+10s >>" else "<< -10s"
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        val uriExtra = intent?.getStringExtra(EXTRA_VIDEO_URI) ?: intent?.dataString
        val titleExtra = intent?.getStringExtra(EXTRA_VIDEO_TITLE)
        if (!uriExtra.isNullOrBlank()) {
            videoUri = uriExtra
            videoTitle = titleExtra?.takeIf { it.isNotBlank() } ?: "Video"
        } else if (videoUri.isBlank()) {
            videoUri = PlayerSessionManager.currentVideoUri.value.orEmpty()
            videoTitle = PlayerSessionManager.currentVideoTitle.value.ifBlank { "Video" }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun enterPipModeNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = buildPipParams(PlayerSessionManager.playerFlow.value)
            enterPictureInPictureMode(params)
        }
    }

    private fun updatePipParams(player: ExoPlayer?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { setPictureInPictureParams(buildPipParams(player)) }
        }
    }

    private fun buildPipParams(player: ExoPlayer?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val videoSize = player?.videoSize
            if (videoSize != null && videoSize != VideoSize.UNKNOWN && videoSize.width > 0 && videoSize.height > 0) {
                val rawRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                // Clamp strictly to Android PiP allowed bounds [1/2.39 .. 2.39]
                val clampedRatio = rawRatio.coerceIn(1f / 2.39f, 2.39f)
                val scaledW = (clampedRatio * 1000).toInt().coerceAtLeast(100)
                builder.setAspectRatio(Rational(scaledW, 1000))
            } else {
                builder.setAspectRatio(Rational(16, 9))
            }
            pipSourceRect?.let { builder.setSourceRectHint(it) }

            val isPlayingNow = player?.isPlaying == true
            val rewindAction = RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_rew),
                "Rewind",
                "Rewind 10 seconds",
                PendingIntent.getBroadcast(
                    this, 101, Intent(ACTION_PIP_REWIND).setPackage(packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            val playPauseAction = RemoteAction(
                Icon.createWithResource(
                    this,
                    if (isPlayingNow) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                ),
                if (isPlayingNow) "Pause" else "Play",
                if (isPlayingNow) "Pause video" else "Play video",
                PendingIntent.getBroadcast(
                    this, 102, Intent(ACTION_PIP_PLAY_PAUSE).setPackage(packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            val forwardAction = RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_ff),
                "Forward",
                "Forward 10 seconds",
                PendingIntent.getBroadcast(
                    this, 103, Intent(ACTION_PIP_FORWARD).setPackage(packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            builder.setActions(listOf(rewindAction, playPauseAction, forwardAction))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val shouldAutoPip = autoPipEnabled &&
                player?.isPlaying == true &&
                !PlayerSessionManager.isBackgroundPlayEnabled.value
            builder.setAutoEnterEnabled(shouldAutoPip)
        }
        return builder.build()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipState = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            hideSystemBars()
        }
    }

    override fun onPause() {
        super.onPause()
        persistPositionBlocking()
    }

    override fun onStop() {
        super.onStop()
        persistPositionBlocking()
        val player = PlayerSessionManager.playerFlow.value
        if (!isInPipState && !PlayerSessionManager.isBackgroundPlayEnabled.value && !isChangingConfigurations) {
            player?.pause()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipActionReceiver) }
        super.onDestroy()
    }

    private fun persistPositionBlocking() {
        val player = PlayerSessionManager.playerFlow.value ?: return
        if (videoUri.isBlank()) return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        val speed = player.playbackParameters.speed
        val isSw = PlayerSessionManager.useSoftwareDecoder.value
        lifecycleScope.launch {
            playbackRepository.savePlaybackState(
                videoUri = videoUri,
                title = videoTitle,
                positionMs = pos,
                durationMs = dur,
                playbackSpeed = speed,
                useSoftwareDecoder = isSw
            )
        }
    }

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        private const val ACTION_PIP_PLAY_PAUSE = "com.example.player.PIP_PLAY_PAUSE"
        private const val ACTION_PIP_REWIND = "com.example.player.PIP_REWIND"
        private const val ACTION_PIP_FORWARD = "com.example.player.PIP_FORWARD"

        fun createIntent(context: Context, videoUri: String, title: String): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URI, videoUri)
                putExtra(EXTRA_VIDEO_TITLE, title)
            }
        }
    }
}
