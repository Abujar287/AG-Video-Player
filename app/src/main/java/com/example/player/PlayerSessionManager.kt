package com.example.player

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Process-scoped holder managing the active ExoPlayer instance, LoudnessEnhancer (up to 200% boost),
 * HW/SW decoder switching, and background playback state shared with PlaybackService and PlayerActivity.
 */
@OptIn(UnstableApi::class)
object PlayerSessionManager {

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow: StateFlow<ExoPlayer?> = _playerFlow.asStateFlow()

    private val _useSoftwareDecoder = MutableStateFlow(false)
    val useSoftwareDecoder: StateFlow<Boolean> = _useSoftwareDecoder.asStateFlow()

    private val _isBackgroundPlayEnabled = MutableStateFlow(false)
    val isBackgroundPlayEnabled: StateFlow<Boolean> = _isBackgroundPlayEnabled.asStateFlow()

    private val _volumePercent = MutableStateFlow(100)
    val volumePercent: StateFlow<Int> = _volumePercent.asStateFlow()

    private val _currentVideoUri = MutableStateFlow<String?>(null)
    val currentVideoUri: StateFlow<String?> = _currentVideoUri.asStateFlow()

    private val _currentVideoTitle = MutableStateFlow("")
    val currentVideoTitle: StateFlow<String> = _currentVideoTitle.asStateFlow()

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var attachedAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var onPlayerRebuiltListener: ((ExoPlayer) -> Unit)? = null

    fun setOnPlayerRebuiltListener(listener: ((ExoPlayer) -> Unit)?) {
        onPlayerRebuiltListener = listener
    }

    fun getOrCreatePlayer(context: Context, preferSoftware: Boolean = _useSoftwareDecoder.value): ExoPlayer {
        _playerFlow.value?.let { return it }
        return buildNewPlayer(context.applicationContext, preferSoftware).also {
            _playerFlow.value = it
            _useSoftwareDecoder.value = preferSoftware
        }
    }

    private fun buildNewPlayer(appContext: Context, preferSoftware: Boolean): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setExtensionRendererMode(
                if (preferSoftware) {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                } else {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                }
            )
            .setMediaCodecSelector(DecoderModeSelector(preferSoftware))

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val player = ExoPlayer.Builder(appContext, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachLoudnessEnhancer(audioSessionId)
            }
        })

        attachLoudnessEnhancer(player.audioSessionId)
        return player
    }

    fun prepareMedia(
        context: Context,
        uriString: String,
        title: String,
        resumePositionMs: Long,
        playbackSpeed: Float,
        preferSoftware: Boolean
    ): ExoPlayer {
        val current = _playerFlow.value
        val player = if (current == null || _useSoftwareDecoder.value != preferSoftware) {
            rebuildPlayerInternal(context.applicationContext, preferSoftware, preserveState = false)
        } else {
            current
        }

        val isSameMedia = _currentVideoUri.value == uriString && player.mediaItemCount > 0
        _currentVideoUri.value = uriString
        _currentVideoTitle.value = title

        if (!isSameMedia) {
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(uriString))
                .setMediaId(uriString)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
            player.setMediaItem(mediaItem)
            if (resumePositionMs > 0L) {
                player.seekTo(resumePositionMs)
            }
            player.setPlaybackSpeed(playbackSpeed.coerceIn(0.25f, 4.0f))
            player.prepare()
            player.playWhenReady = true
        }
        syncSystemVolume(context)
        return player
    }

    fun switchDecoder(context: Context, preferSoftware: Boolean): ExoPlayer {
        if (_useSoftwareDecoder.value == preferSoftware && _playerFlow.value != null) {
            return _playerFlow.value!!
        }
        return rebuildPlayerInternal(context.applicationContext, preferSoftware, preserveState = true)
    }

    private fun rebuildPlayerInternal(
        appContext: Context,
        preferSoftware: Boolean,
        preserveState: Boolean
    ): ExoPlayer {
        val oldPlayer = _playerFlow.value
        val savedItem = if (preserveState) oldPlayer?.currentMediaItem else null
        val savedPos = if (preserveState) oldPlayer?.currentPosition ?: 0L else 0L
        val savedPlay = if (preserveState) oldPlayer?.playWhenReady ?: true else false
        val savedSpeed = if (preserveState) oldPlayer?.playbackParameters?.speed ?: 1.0f else 1.0f

        releaseLoudnessEnhancer()
        oldPlayer?.release()

        val newPlayer = buildNewPlayer(appContext, preferSoftware)
        _useSoftwareDecoder.value = preferSoftware
        _playerFlow.value = newPlayer
        onPlayerRebuiltListener?.invoke(newPlayer)

        if (savedItem != null) {
            newPlayer.setMediaItem(savedItem)
            if (savedPos > 0L) newPlayer.seekTo(savedPos)
            newPlayer.setPlaybackSpeed(savedSpeed)
            newPlayer.prepare()
            newPlayer.playWhenReady = savedPlay
        }
        return newPlayer
    }

    fun syncSystemVolume(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val currVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val basePercent = ((currVol.toFloat() / maxVol.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        // Preserve boost > 100% if already boosted at max volume
        val currentTotal = _volumePercent.value
        if (currentTotal > 100 && basePercent >= 95) {
            applyLoudnessGainForPercent(currentTotal)
        } else {
            _volumePercent.value = basePercent
            applyLoudnessGainForPercent(basePercent)
        }
    }

    fun setVolumeWithBoost(context: Context, targetPercent: Int) {
        val clamped = targetPercent.coerceIn(0, 200)
        _volumePercent.value = clamped
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val streamPercent = clamped.coerceAtMost(100)
            val targetStreamVol = ((streamPercent / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamVol, 0)
        }
        applyLoudnessGainForPercent(clamped)
    }

    private fun attachLoudnessEnhancer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        if (attachedAudioSessionId == audioSessionId && loudnessEnhancer != null) {
            applyLoudnessGainForPercent(_volumePercent.value)
            return
        }
        releaseLoudnessEnhancer()
        runCatching {
            val enhancer = LoudnessEnhancer(audioSessionId)
            loudnessEnhancer = enhancer
            attachedAudioSessionId = audioSessionId
            applyLoudnessGainForPercent(_volumePercent.value)
        }
    }

    private fun applyLoudnessGainForPercent(percent: Int) {
        val enhancer = loudnessEnhancer ?: return
        runCatching {
            if (percent > 100) {
                // Map 101%..200% to 15mB..1500mB (+15dB max extra loudness boost)
                val boostFraction = (percent - 100) / 100f
                val gainMb = (boostFraction * 1500f).roundToInt().coerceIn(0, 1500)
                enhancer.setTargetGain(gainMb)
                enhancer.enabled = true
            } else {
                enhancer.setTargetGain(0)
                enhancer.enabled = false
            }
        }
    }

    fun setBackgroundPlayEnabled(enabled: Boolean) {
        _isBackgroundPlayEnabled.value = enabled
    }

    private fun releaseLoudnessEnhancer() {
        runCatching {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        }
        loudnessEnhancer = null
        attachedAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    fun releasePlayer() {
        releaseLoudnessEnhancer()
        _playerFlow.value?.release()
        _playerFlow.value = null
        _currentVideoUri.value = null
        _currentVideoTitle.value = ""
        _isBackgroundPlayEnabled.value = false
    }
}
