package com.example.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.MediaTrackOption
import com.example.data.model.PlayerAspectMode
import com.example.data.model.PlaylistEntry
import com.example.data.model.UserPreferences
import com.example.data.repository.PlaybackRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.theme.VeloPlayTheme
import com.example.util.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlayerActivity : ComponentActivity() {

    private lateinit var playbackRepo: PlaybackRepository
    private lateinit var prefsRepo: UserPreferencesRepository

    private var exoPlayer: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var playlist: List<PlaylistEntry> = emptyList()
    private var currentIndex: Int = 0
    private var useSoftwareDecoderState by mutableStateOf(false)
    private var isInPipModeState by mutableStateOf(false)
    private var externalSubUri: Uri? = null
    private var onSubtitleAddedBanner: ((String) -> Unit)? = null

    private val subtitlePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            externalSubUri = uri
            reloadCurrentItemWithSubtitle(uri)
            onSubtitleAddedBanner?.invoke("Subtitle attached")
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        playbackRepo = PlaybackRepository(this)
        prefsRepo = UserPreferencesRepository(this)

        resolveInitialPlaylist(intent)

        setContent {
            val prefs by prefsRepo.preferencesFlow.collectAsState(initial = UserPreferences())

            var isPlaying by remember { mutableStateOf(true) }
            var currentPosMs by remember { mutableLongStateOf(0L) }
            var durationMs by remember { mutableLongStateOf(0L) }
            var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
            var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
            var sleepMinutesRemaining by remember { mutableStateOf<Int?>(null) }
            var isLocked by remember { mutableStateOf(false) }
            var isBackgroundPlayEnabled by remember(prefs.alwaysBackgroundPlay) {
                mutableStateOf(prefs.alwaysBackgroundPlay)
            }
            var controlsVisible by remember { mutableStateOf(true) }
            var aspectMode by remember { mutableStateOf(PlayerAspectMode.FIT) }
            var quickZoomScale by remember { mutableFloatStateOf(1.0f) }
            var isRotated by remember { mutableStateOf(false) }
            var brightnessPercent by remember { mutableIntStateOf(75) }
            val initialAudioPct = remember {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxSys = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val curSys = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                ((curSys.toFloat() / maxSys.toFloat()) * 100f).toInt().coerceIn(0, 100)
            }
            var volumePercent by remember { mutableIntStateOf(initialAudioPct) }
            var lastNonZeroVolume by remember { mutableIntStateOf(initialAudioPct.coerceAtLeast(50)) }
            var isMuted by remember { mutableStateOf(initialAudioPct == 0) }
            var statusBanner by remember { mutableStateOf<String?>(null) }
            var audioTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
            var subtitleTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
            var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

            onSubtitleAddedBanner = { msg -> statusBanner = msg }

            fun rebuildPlayer(software: Boolean, seekPosMs: Long = -1L) {
                exoPlayer?.release()
                loudnessEnhancer?.release()
                loudnessEnhancer = null

                val player = ExoPlayer.Builder(this@PlayerActivity)
                    .setRenderersFactory(DecoderModeSelector.buildRenderersFactory(this@PlayerActivity, software))
                    .build()

                exoPlayer = player
                playerViewRef?.player = player

                val mediaItems = playlist.map { entry ->
                    MediaItem.Builder()
                        .setUri(Uri.parse(entry.uriString))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.title).build())
                        .build()
                }
                if (mediaItems.isNotEmpty()) {
                    val startIdx = currentIndex.coerceIn(0, mediaItems.lastIndex)
                    player.setMediaItems(mediaItems, startIdx, seekPosMs.coerceAtLeast(0L))
                    player.repeatMode = repeatMode
                    player.playbackParameters = PlaybackParameters(playbackSpeed)
                    player.prepare()
                    player.playWhenReady = true
                }

                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentIndex = player.currentMediaItemIndex
                        PlayerSessionManager.updateCurrentIndex(currentIndex)
                        val entry = playlist.getOrNull(currentIndex) ?: return
                        lifecycleScope.launch { prefsRepo.markVideoPlayed(entry.uriString) }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        val parsedAudio = mutableListOf<MediaTrackOption>()
                        val parsedSub = mutableListOf<MediaTrackOption>()
                        tracks.groups.forEachIndexed { groupIdx, group ->
                            when (group.type) {
                                C.TRACK_TYPE_AUDIO -> {
                                    for (trackIdx in 0 until group.length) {
                                        val fmt = group.getTrackFormat(trackIdx)
                                        val lang = fmt.language ?: "und"
                                        val lbl = fmt.label ?: "Audio ${parsedAudio.size + 1} ($lang)"
                                        parsedAudio += MediaTrackOption(groupIdx, trackIdx, lbl, fmt.language, group.isTrackSelected(trackIdx))
                                    }
                                }
                                C.TRACK_TYPE_TEXT -> {
                                    for (trackIdx in 0 until group.length) {
                                        val fmt = group.getTrackFormat(trackIdx)
                                        val lang = fmt.language ?: "sub"
                                        val lbl = fmt.label ?: "Subtitle ${parsedSub.size + 1} ($lang)"
                                        parsedSub += MediaTrackOption(groupIdx, trackIdx, lbl, fmt.language, group.isTrackSelected(trackIdx))
                                    }
                                }
                            }
                        }
                        audioTracks = parsedAudio
                        subtitleTracks = parsedSub
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        runCatching {
                            loudnessEnhancer?.release()
                            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                                val boostGain = ((volumePercent - 100) * 15).coerceIn(0, 1500)
                                setTargetGain(boostGain)
                                enabled = volumePercent > 100
                            }
                        }
                    }
                })
            }

            // Initialize player + restore saved resume position
            LaunchedEffect(Unit) {
                val initialPrefs = prefsRepo.preferencesFlow.first()
                val firstUri = playlist.getOrNull(currentIndex)?.uriString
                val saved = firstUri?.let { playbackRepo.getHistoryForUri(it) }
                useSoftwareDecoderState = saved?.useSoftwareDecoder ?: initialPrefs.defaultSoftwareDecoder
                playbackSpeed = saved?.playbackSpeed ?: 1.0f
                val resumeMs = if (saved != null && saved.positionMs > 1500L && saved.durationMs > 0L && saved.positionMs < saved.durationMs - 1500L) {
                    saved.positionMs
                } else {
                    0L
                }
                if (firstUri != null) prefsRepo.markVideoPlayed(firstUri)
                rebuildPlayer(useSoftwareDecoderState, resumeMs)
                if (resumeMs > 0L) {
                    statusBanner = "Resumed from ${FormatUtils.formatDuration(resumeMs)}"
                }
            }

            // Poll playback progress & persist periodically
            LaunchedEffect(Unit) {
                var tick = 0
                while (true) {
                    val p = exoPlayer
                    if (p != null) {
                        currentPosMs = p.currentPosition.coerceAtLeast(0L)
                        durationMs = p.duration.coerceAtLeast(0L)
                        isPlaying = p.isPlaying
                        tick++
                        if (tick % 6 == 0) {
                            persistPlaybackSnapshot(playbackSpeed)
                        }
                    }
                    delay(500L)
                }
            }

            // Auto-hide controls after 3.5s
            LaunchedEffect(controlsVisible, isPlaying, isLocked) {
                if (controlsVisible && (isPlaying || isLocked)) {
                    delay(3500L)
                    controlsVisible = false
                }
            }

            // Auto-clear status banner
            LaunchedEffect(statusBanner) {
                if (!statusBanner.isNullOrBlank()) {
                    delay(2200L)
                    statusBanner = null
                }
            }

            // Sleep timer countdown
            LaunchedEffect(sleepMinutesRemaining) {
                val rem = sleepMinutesRemaining ?: return@LaunchedEffect
                if (rem <= 0) {
                    exoPlayer?.pause()
                    sleepMinutesRemaining = null
                    statusBanner = "Sleep timer paused playback"
                } else {
                    delay(60_000L)
                    sleepMinutesRemaining = (rem - 1).coerceAtLeast(0)
                }
            }

            fun applyVolume(nextPct: Int) {
                val clamped = nextPct.coerceIn(0, 200)
                if (clamped > 0) lastNonZeroVolume = clamped
                volumePercent = clamped
                isMuted = clamped == 0

                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxSys = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val targetSys = ((clamped.coerceAtMost(100) / 100f) * maxSys).toInt().coerceIn(0, maxSys)
                runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, targetSys, 0) }

                exoPlayer?.volume = if (clamped == 0) 0f else 1f
                runCatching {
                    loudnessEnhancer?.apply {
                        val gain = ((clamped - 100) * 15).coerceIn(0, 1500)
                        setTargetGain(gain)
                        enabled = clamped > 100
                    }
                }
            }

            fun applyBrightness(nextPct: Int) {
                val clamped = nextPct.coerceIn(2, 100)
                brightnessPercent = clamped
                val lp = window.attributes
                lp.screenBrightness = clamped / 100f
                window.attributes = lp
            }

            androidx.activity.compose.BackHandler(enabled = true) {
                lifecycleScope.launch { persistPlaybackSnapshot(playbackSpeed) }
                if (!isBackgroundPlayEnabled) {
                    exoPlayer?.pause()
                }
                finish()
            }

            VeloPlayTheme(darkTheme = true, themeId = prefs.themeId) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                player = exoPlayer
                                playerViewRef = this
                            }
                        },
                        update = { view ->
                            if (view.player !== exoPlayer) view.player = exoPlayer
                            view.resizeMode = when (aspectMode) {
                                PlayerAspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                PlayerAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                PlayerAspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .graphicsLayer(
                                scaleX = quickZoomScale,
                                scaleY = quickZoomScale
                            )
                    )

                    if (!isInPipModeState) {
                        val currentTitle = playlist.getOrNull(currentIndex)?.title ?: "Video"
                        PlayerOverlay(
                            title = currentTitle,
                            isPlaying = isPlaying,
                            currentPosMs = currentPosMs,
                            durationMs = durationMs,
                            playbackSpeed = playbackSpeed,
                            useSoftwareDecoder = useSoftwareDecoderState,
                            repeatMode = repeatMode,
                            sleepMinutesRemaining = sleepMinutesRemaining,
                            isLocked = isLocked,
                            isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                            controlsVisible = controlsVisible,
                            aspectMode = aspectMode,
                            quickZoomScale = quickZoomScale,
                            isRotated = isRotated,
                            brightnessPercent = brightnessPercent,
                            volumePercent = volumePercent,
                            isMuted = isMuted,
                            hasPrevious = currentIndex > 0,
                            hasNext = currentIndex < playlist.lastIndex,
                            preferences = prefs,
                            statusBanner = statusBanner,
                            audioTracks = audioTracks,
                            subtitleTracks = subtitleTracks,
                            onToggleControls = { controlsVisible = !controlsVisible },
                            onBack = {
                                lifecycleScope.launch { persistPlaybackSnapshot(playbackSpeed) }
                                if (!isBackgroundPlayEnabled) {
                                    exoPlayer?.pause()
                                }
                                finish()
                            },
                            onPlayPause = {
                                exoPlayer?.let { p ->
                                    if (p.isPlaying) p.pause() else p.play()
                                }
                                controlsVisible = true
                            },
                            onSeekTo = { pos ->
                                exoPlayer?.seekTo(pos)
                                currentPosMs = pos
                            },
                            onSeekBy = { delta ->
                                exoPlayer?.let { p ->
                                    val next = (p.currentPosition + delta).coerceIn(0L, p.duration.coerceAtLeast(0L))
                                    p.seekTo(next)
                                    currentPosMs = next
                                    statusBanner = FormatUtils.formatSignedDelta(delta)
                                }
                            },
                            onPrevious = {
                                exoPlayer?.let { p ->
                                    if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem()
                                }
                            },
                            onNext = {
                                exoPlayer?.let { p ->
                                    if (p.hasNextMediaItem()) p.seekToNextMediaItem()
                                }
                            },
                            onToggleLock = {
                                isLocked = !isLocked
                                controlsVisible = true
                                statusBanner = if (isLocked) "Controls Locked" else "Controls Unlocked"
                            },
                            onCycleAspectMode = {
                                val order = PlayerAspectMode.entries
                                aspectMode = order[(order.indexOf(aspectMode) + 1) % order.size]
                                quickZoomScale = 1.0f
                                statusBanner = "Screen: ${aspectMode.label}"
                            },
                            onCycleQuickZoom = {
                                val steps = listOf(1.0f, 1.25f, 1.5f, 2.0f)
                                val idx = steps.indexOfFirst { kotlin.math.abs(it - quickZoomScale) < 0.08f }
                                val next = steps[(idx + 1) % steps.size]
                                quickZoomScale = next
                                statusBanner = if (next == 1.0f) "Zoom Reset (1.0x)" else "Quick Zoom ${next}x"
                            },
                            onEnterPip = { enterPipSafe() },
                            onToggleDecoder = {
                                val nextSw = !useSoftwareDecoderState
                                useSoftwareDecoderState = nextSw
                                val pos = exoPlayer?.currentPosition ?: 0L
                                rebuildPlayer(nextSw, pos)
                                lifecycleScope.launch { prefsRepo.setDefaultSoftwareDecoder(nextSw) }
                                statusBanner = if (nextSw) "Switched to SW Decoder" else "Switched to HW Decoder"
                            },
                            onToggleRotation = {
                                isRotated = !isRotated
                                requestedOrientation = if (isRotated) {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                                statusBanner = if (isRotated) "Orientation: Landscape" else "Orientation: Auto"
                            },
                            onSelectSpeed = { spd ->
                                playbackSpeed = spd
                                exoPlayer?.playbackParameters = PlaybackParameters(spd)
                                statusBanner = "Speed ${spd}x"
                            },
                            onCycleRepeat = {
                                repeatMode = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                exoPlayer?.repeatMode = repeatMode
                                statusBanner = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> "Repeat: Current Video"
                                    Player.REPEAT_MODE_ALL -> "Repeat: Playlist"
                                    else -> "Repeat: Off"
                                }
                            },
                            onToggleMute = {
                                if (isMuted || volumePercent == 0) {
                                    applyVolume(lastNonZeroVolume.coerceAtLeast(50))
                                } else {
                                    applyVolume(0)
                                }
                            },
                            onSelectSleepMinutes = { mins ->
                                sleepMinutesRemaining = mins
                                statusBanner = if (mins == null) "Sleep Timer Off" else "Sleep Timer: ${mins}m"
                            },
                            onToggleBackgroundPlay = {
                                val next = !isBackgroundPlayEnabled
                                isBackgroundPlayEnabled = next
                                lifecycleScope.launch { prefsRepo.setAlwaysBackgroundPlay(next) }
                                statusBanner = if (next) "Background Audio Play Enabled" else "Background Audio Play Disabled"
                            },
                            onSelectAudioTrack = { opt ->
                                val player = exoPlayer ?: return@PlayerOverlay
                                val group = player.currentTracks.groups.getOrNull(opt.groupIndex)?.mediaTrackGroup ?: return@PlayerOverlay
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(group, opt.trackIndex))
                                    .build()
                                statusBanner = "Audio: ${opt.label}"
                            },
                            onSelectSubtitleTrack = { opt ->
                                val player = exoPlayer ?: return@PlayerOverlay
                                val builder = player.trackSelectionParameters.buildUpon()
                                if (opt == null) {
                                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    statusBanner = "Subtitles Off"
                                } else {
                                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    val group = player.currentTracks.groups.getOrNull(opt.groupIndex)?.mediaTrackGroup
                                    if (group != null) {
                                        builder.setOverrideForType(TrackSelectionOverride(group, opt.trackIndex))
                                    }
                                    statusBanner = "Subtitle: ${opt.label}"
                                }
                                player.trackSelectionParameters = builder.build()
                            },
                            onImportExternalSubtitle = {
                                subtitlePickerLauncher.launch(arrayOf("*/*"))
                            },
                            onChangeBrightness = { applyBrightness(it) },
                            onChangeVolume = { applyVolume(it) }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resolveInitialPlaylist(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val prefs = runCatching {
            kotlinx.coroutines.runBlocking { prefsRepo.preferencesFlow.first() }
        }.getOrNull()
        if (prefs?.autoPipOnHome == true && prefs.alwaysBackgroundPlay.not()) {
            enterPipSafe()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { persistPlaybackSnapshot(exoPlayer?.playbackParameters?.speed ?: 1f) }
        val prefs = runCatching {
            kotlinx.coroutines.runBlocking { prefsRepo.preferencesFlow.first() }
        }.getOrNull()
        if (!isInPipModeState && prefs?.alwaysBackgroundPlay != true) {
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    private fun resolveInitialPlaylist(intent: Intent?) {
        val dataUri = intent?.data
        if (dataUri != null) {
            val title = dataUri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Video"
            playlist = listOf(PlaylistEntry(dataUri.toString(), title))
            currentIndex = 0
            return
        }
        val session = PlayerSessionManager.sessionMeta.value
        if (session.playlist.isNotEmpty()) {
            playlist = session.playlist
            currentIndex = session.currentIndex
        }
    }

    private fun reloadCurrentItemWithSubtitle(subUri: Uri) {
        val player = exoPlayer ?: return
        val entry = playlist.getOrNull(currentIndex) ?: return
        val pos = player.currentPosition
        val mime = when {
            subUri.toString().endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
            subUri.toString().endsWith(".ass", true) || subUri.toString().endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val subConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
            .setMimeType(mime)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val updated = MediaItem.Builder()
            .setUri(Uri.parse(entry.uriString))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.title).build())
            .setSubtitleConfigurations(listOf(subConfig))
            .build()
        player.setMediaItem(updated, pos)
        player.prepare()
        player.playWhenReady = true
    }

    private fun enterPipSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    private suspend fun persistPlaybackSnapshot(speed: Float) {
        val p = exoPlayer ?: return
        val entry = playlist.getOrNull(currentIndex) ?: return
        val dur = p.duration
        if (dur > 0L) {
            playbackRepo.savePlaybackState(
                videoUri = entry.uriString,
                title = entry.title,
                positionMs = p.currentPosition,
                durationMs = dur,
                playbackSpeed = speed,
                useSoftwareDecoder = useSoftwareDecoderState
            )
        }
    }

    private fun hideSystemBars() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
