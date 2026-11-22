package com.example.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
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
import com.example.data.repository.UserPreferences
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.theme.VeloPlayTheme
import com.example.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var prefsRepository: UserPreferencesRepository
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var playlistEntries by mutableStateOf<List<PlaylistEntry>>(emptyList())
    private var startPlaylistIndex by mutableIntStateOf(0)
    private var isInPipState by mutableStateOf(false)
    private var autoPipEnabled by mutableStateOf(true)
    private var pipSourceRect: android.graphics.Rect? = null
    private var hasPipFeature: Boolean = false

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

        hasPipFeature = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

        playbackRepository = PlaybackRepository(VeloDatabase.getInstance(this).playbackHistoryDao())
        prefsRepository = UserPreferencesRepository(this)

        handleLaunchIntent(intent)
        runCatching { startService(Intent(this, PlaybackService::class.java)) }

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

        if (hasPipFeature && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
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
            val isMuted by PlayerSessionManager.isMuted.collectAsStateWithLifecycle()
            val activeTitle by PlayerSessionManager.currentVideoTitle.collectAsStateWithLifecycle()
            val activeUri by PlayerSessionManager.currentVideoUri.collectAsStateWithLifecycle()
            val audioTracks by PlayerSessionManager.audioTracks.collectAsStateWithLifecycle()
            val subtitleTracks by PlayerSessionManager.subtitleTracks.collectAsStateWithLifecycle()
            val subtitlesDisabled by PlayerSessionManager.subtitlesDisabled.collectAsStateWithLifecycle()
            val playerError by PlayerSessionManager.playerError.collectAsStateWithLifecycle()
            val prefs by prefsRepository.preferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )

            LaunchedEffect(prefs.autoPipOnHome) {
                autoPipEnabled = prefs.autoPipOnHome
            }

            var isPlaying by remember { mutableStateOf(false) }
            var currentPosMs by remember { mutableLongStateOf(0L) }
            var durationMs by remember { mutableLongStateOf(0L) }
            var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
            var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
            var sleepMinutesRemaining by remember { mutableStateOf<Int?>(null) }
            var isLocked by remember { mutableStateOf(false) }
            var controlsVisible by remember { mutableStateOf(true) }
            var aspectMode by remember { mutableStateOf(PlayerAspectMode.FIT) }
            var brightnessPercent by remember { mutableIntStateOf(65) }
            var doubleTapFeedback by remember { mutableStateOf<String?>(null) }
            var statusBanner by remember { mutableStateOf<String?>(null) }

            val subtitlePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { subUri: Uri? ->
                if (subUri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            subUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    val subName = resolveDisplayName(subUri) ?: "subtitle.srt"
                    PlayerSessionManager.attachExternalSubtitle(subUri, subName)
                    statusBanner = "Loaded subtitle: $subName"
                }
            }

            LaunchedEffect(playerError) {
                if (playerError != null) {
                    statusBanner = playerError
                }
            }

            // Prepare playlist & restore exact saved timestamp
            LaunchedEffect(playlistEntries, startPlaylistIndex) {
                val target = playlistEntries.getOrNull(startPlaylistIndex) ?: return@LaunchedEffect
                val saved = playbackRepository.getSavedState(target.uriString)
                val initialPrefs = prefsRepository.preferences.first()
                val startPos = saved?.positionMs ?: 0L
                val startSpeed = saved?.playbackSpeed ?: 1.0f
                val startSw = saved?.useSoftwareDecoder ?: initialPrefs.defaultSoftwareDecoder
                playbackSpeed = startSpeed

                PlayerSessionManager.preparePlaylist(
                    context = this@PlayerActivity,
                    playlist = playlistEntries,
                    startIndex = startPlaylistIndex,
                    resumePositionMs = startPos,
                    playbackSpeed = startSpeed,
                    preferSoftware = startSw
                )
                if (startPos > 1_500L) {
                    statusBanner = "Resumed from ${FormatUtils.formatDuration(startPos)}"
                }
            }

            // Sleep timer countdown
            LaunchedEffect(sleepMinutesRemaining) {
                val mins = sleepMinutesRemaining ?: return@LaunchedEffect
                if (mins <= 0) {
                    player?.pause()
                    sleepMinutesRemaining = null
                    statusBanner = "Sleep timer paused playback"
                } else {
                    delay(60_000L)
                    sleepMinutesRemaining = (mins - 1).coerceAtLeast(0)
                }
            }

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

                    override fun onRepeatModeChanged(newRepeatMode: Int) {
                        repeatMode = newRepeatMode
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            val dur = activePlayer.duration.coerceAtLeast(0L)
                            durationMs = dur
                            updatePipParams(activePlayer)
                            val uriNow = activeUri.orEmpty()
                            if (uriNow.isNotBlank() && dur > 0L) {
                                scope.launch {
                                    playbackRepository.savePlaybackState(
                                        videoUri = uriNow,
                                        title = activeTitle,
                                        positionMs = activePlayer.currentPosition.coerceAtLeast(0L),
                                        durationMs = dur,
                                        playbackSpeed = playbackSpeed,
                                        useSoftwareDecoder = useSoftwareDecoder
                                    )
                                }
                            }
                        }
                    }
                }
                activePlayer.addListener(listener)
                isPlaying = activePlayer.isPlaying
                repeatMode = activePlayer.repeatMode
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
                        val uriNow = activeUri.orEmpty()
                        if (ticks % 6 == 0 && uriNow.isNotBlank() && dur > 0L) {
                            playbackRepository.savePlaybackState(
                                videoUri = uriNow,
                                title = activeTitle,
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
                    delay(2_400L)
                    statusBanner = null
                }
            }

            VeloPlayTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                val newRect = coords.boundsInWindow().toAndroidRectF().toRect()
                                if (newRect != pipSourceRect && newRect.width() > 0 && newRect.height() > 0) {
                                    pipSourceRect = newRect
                                    updatePipParams(player)
                                }
                            }
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

                    val hasPrev = (player?.hasPreviousMediaItem() == true) || playlistEntries.size > 1
                    val hasNext = (player?.hasNextMediaItem() == true) || playlistEntries.size > 1

                    PlayerOverlay(
                        title = activeTitle.ifBlank { "Video" },
                        isPlaying = isPlaying,
                        currentPositionMs = currentPosMs,
                        durationMs = durationMs,
                        playbackSpeed = playbackSpeed,
                        useSoftwareDecoder = useSoftwareDecoder,
                        isLocked = isLocked,
                        isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                        hasPrevious = hasPrev && (player?.currentMediaItemIndex ?: 0) > 0,
                        hasNext = hasNext && (player?.currentMediaItemIndex ?: 0) < (playlistEntries.size - 1),
                        repeatMode = repeatMode,
                        sleepMinutesRemaining = sleepMinutesRemaining,
                        aspectMode = aspectMode,
                        controlsVisible = controlsVisible,
                        isInPipMode = isInPipState,
                        brightnessPercent = brightnessPercent,
                        volumePercent = volumePercent,
                        isMuted = isMuted,
                        audioTracks = audioTracks,
                        subtitleTracks = subtitleTracks,
                        subtitlesDisabled = subtitlesDisabled,
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
                        onPreviousVideo = {
                            persistPositionBlocking()
                            player?.seekToPreviousMediaItem()
                        },
                        onNextVideo = {
                            persistPositionBlocking()
                            player?.seekToNextMediaItem()
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
                        onCycleRepeatMode = {
                            val nextMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                else -> Player.REPEAT_MODE_OFF
                            }
                            repeatMode = nextMode
                            player?.repeatMode = nextMode
                            statusBanner = when (nextMode) {
                                Player.REPEAT_MODE_ONE -> "Repeat: Current Video"
                                Player.REPEAT_MODE_ALL -> "Repeat: Folder Playlist"
                                else -> "Repeat: Off"
                            }
                        },
                        onSetSleepTimerMinutes = { mins ->
                            sleepMinutesRemaining = mins
                            statusBanner = if (mins == null) "Sleep Timer Off" else "Sleep Timer: ${mins}m"
                        },
                        onCycleAspectMode = {
                            val modes = PlayerAspectMode.entries
                            aspectMode = modes[(aspectMode.ordinal + 1) % modes.size]
                            statusBanner = "Screen: ${aspectMode.label}"
                        },
                        onToggleMute = {
                            PlayerSessionManager.toggleMute(this@PlayerActivity)
                        },
                        onSelectAudioTrack = { track ->
                            PlayerSessionManager.selectAudioTrack(track)
                            statusBanner = "Audio: ${track.label}"
                        },
                        onSelectSubtitleTrack = { track ->
                            PlayerSessionManager.selectSubtitleTrack(track)
                            statusBanner = if (track == null) "Subtitles Off" else "Subtitle: ${track.label}"
                        },
                        onPickExternalSubtitle = {
                            subtitlePickerLauncher.launch(arrayOf("*/*"))
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
                        onDoubleTapPlayPause = {
                            val p = player ?: return@PlayerOverlay
                            if (p.isPlaying) {
                                p.pause()
                                doubleTapFeedback = "Paused"
                            } else {
                                p.play()
                                doubleTapFeedback = "Playing"
                            }
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
        val pending = PlayerSessionManager.consumePendingPlaylist()
        if (pending != null && pending.first.isNotEmpty()) {
            playlistEntries = pending.first
            startPlaylistIndex = pending.second
            return
        }

        val uriExtra = intent?.getStringExtra(EXTRA_VIDEO_URI) ?: intent?.dataString
        val titleExtra = intent?.getStringExtra(EXTRA_VIDEO_TITLE)
            ?: intent?.data?.let { resolveDisplayName(it) }
        if (!uriExtra.isNullOrBlank()) {
            playlistEntries = listOf(PlaylistEntry(uriExtra, titleExtra?.takeIf { it.isNotBlank() } ?: "Video"))
            startPlaylistIndex = 0
        } else if (playlistEntries.isEmpty()) {
            val activeUri = PlayerSessionManager.currentVideoUri.value
            if (!activeUri.isNullOrBlank()) {
                playlistEntries = listOf(
                    PlaylistEntry(
                        activeUri,
                        PlayerSessionManager.currentVideoTitle.value.ifBlank { "Video" }
                    )
                )
                startPlaylistIndex = 0
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun enterPipModeNow() {
        if (!hasPipFeature || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val params = buildPipParams(PlayerSessionManager.playerFlow.value)
            enterPictureInPictureMode(params)
        }
    }

    private fun updatePipParams(player: ExoPlayer?) {
        if (!hasPipFeature || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching { setPictureInPictureParams(buildPipParams(player)) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(player: ExoPlayer?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val videoSize = player?.videoSize
            if (videoSize != null && videoSize != VideoSize.UNKNOWN && videoSize.width > 0 && videoSize.height > 0) {
                val rawRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                val clampedRatio = rawRatio.coerceIn(1f / 2.39f, 2.39f)
                val scaledW = (clampedRatio * 1000).toInt().coerceAtLeast(100)
                builder.setAspectRatio(Rational(scaledW, 1000))
            } else {
                builder.setAspectRatio(Rational(16, 9))
            }
            pipSourceRect?.let {
                if (it.width() > 0 && it.height() > 0) {
                    builder.setSourceRectHint(it)
                }
            }

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
        val uri = PlayerSessionManager.currentVideoUri.value ?: return
        val title = PlayerSessionManager.currentVideoTitle.value
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        val speed = player.playbackParameters.speed
        val isSw = PlayerSessionManager.useSoftwareDecoder.value
        persistScope.launch {
            playbackRepository.savePlaybackState(
                videoUri = uri,
                title = title,
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

        fun createPlaylistIntent(
            context: Context,
            playlist: List<PlaylistEntry>,
            startIndex: Int
        ): Intent {
            PlayerSessionManager.setPendingPlaylist(playlist, startIndex)
            return Intent(context, PlayerActivity::class.java)
        }
    }
}
