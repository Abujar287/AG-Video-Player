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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class MediaTrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val trackGroup: TrackGroup,
    val trackIndex: Int
)

data class PlaylistEntry(
    val uriString: String,
    val title: String
)

/**
 * Process-scoped holder managing the active ExoPlayer instance, LoudnessEnhancer (up to 200% boost),
 * HW/SW decoder switching, audio/subtitle tracks, playlists, and background playback state.
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

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _currentVideoUri = MutableStateFlow<String?>(null)
    val currentVideoUri: StateFlow<String?> = _currentVideoUri.asStateFlow()

    private val _currentVideoTitle = MutableStateFlow("")
    val currentVideoTitle: StateFlow<String> = _currentVideoTitle.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<MediaTrackOption>>(emptyList())
    val audioTracks: StateFlow<List<MediaTrackOption>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<MediaTrackOption>>(emptyList())
    val subtitleTracks: StateFlow<List<MediaTrackOption>> = _subtitleTracks.asStateFlow()

    private val _subtitlesDisabled = MutableStateFlow(false)
    val subtitlesDisabled: StateFlow<Boolean> = _subtitlesDisabled.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _stopPlaybackSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopPlaybackSignal: SharedFlow<Unit> = _stopPlaybackSignal.asSharedFlow()

    private var pendingPlaylist: List<PlaylistEntry>? = null
    private var pendingStartIndex: Int = 0

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var attachedAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var onPlayerRebuiltListener: ((ExoPlayer) -> Unit)? = null
    private var lastNonZeroVolumePercent: Int = 100

    fun setPendingPlaylist(entries: List<PlaylistEntry>, startIndex: Int) {
        pendingPlaylist = entries
        pendingStartIndex = startIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    }

    fun consumePendingPlaylist(): Pair<List<PlaylistEntry>, Int>? {
        val list = pendingPlaylist ?: return null
        val idx = pendingStartIndex
        pendingPlaylist = null
        pendingStartIndex = 0
        return list to idx
    }

    fun clearPlayerError() {
        _playerError.value = null
    }

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

            override fun onTracksChanged(tracks: Tracks) {
                refreshTrackLists(tracks)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    _currentVideoUri.value = mediaItem.mediaId.ifBlank {
                        mediaItem.localConfiguration?.uri?.toString()
                    }
                    _currentVideoTitle.value = mediaItem.mediaMetadata.title?.toString() ?: "Video"
                    _playerError.value = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playerError.value = error.localizedMessage ?: "Playback error (${error.errorCodeName})"
            }
        })

        attachLoudnessEnhancer(player.audioSessionId)
        return player
    }

    fun preparePlaylist(
        context: Context,
        playlist: List<PlaylistEntry>,
        startIndex: Int,
        resumePositionMs: Long,
        playbackSpeed: Float,
        preferSoftware: Boolean
    ): ExoPlayer {
        _playerError.value = null
        val current = _playerFlow.value
        val player = if (current == null || _useSoftwareDecoder.value != preferSoftware) {
            rebuildPlayerInternal(context.applicationContext, preferSoftware, preserveState = false)
        } else {
            current
        }

        val safeIndex = startIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
        val targetEntry = playlist.getOrNull(safeIndex)
        val isSameMedia = targetEntry != null &&
            _currentVideoUri.value == targetEntry.uriString &&
            player.mediaItemCount == playlist.size

        if (targetEntry != null) {
            _currentVideoUri.value = targetEntry.uriString
            _currentVideoTitle.value = targetEntry.title
        }

        if (!isSameMedia && playlist.isNotEmpty()) {
            val mediaItems = playlist.map { entry ->
                MediaItem.Builder()
                    .setUri(Uri.parse(entry.uriString))
                    .setMediaId(entry.uriString)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(entry.title)
                            .setArtist("AG Video Player")
                            .build()
                    )
                    .build()
            }
            player.setMediaItems(mediaItems, safeIndex, resumePositionMs.coerceAtLeast(0L))
            player.setPlaybackSpeed(playbackSpeed.coerceIn(0.25f, 4.0f))
            player.prepare()
            player.playWhenReady = true
        }
        syncSystemVolume(context)
        return player
    }

    fun attachExternalSubtitle(uri: Uri, displayName: String) {
        val player = _playerFlow.value ?: return
        val currentItem = player.currentMediaItem ?: return
        val currentIndex = player.currentMediaItemIndex
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.playWhenReady

        val lower = displayName.lowercase()
        val mimeType = when {
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLabel(displayName.substringBeforeLast('.').ifBlank { "External Subtitle" })
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val updatedConfigs = currentItem.localConfiguration?.subtitleConfigurations.orEmpty() + subtitleConfig
        val updatedItem = currentItem.buildUpon()
            .setSubtitleConfigurations(updatedConfigs)
            .build()

        player.replaceMediaItem(currentIndex, updatedItem)
        player.seekTo(currentIndex, currentPos)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        _subtitlesDisabled.value = false
        player.playWhenReady = wasPlaying
    }

    fun selectAudioTrack(option: MediaTrackOption) {
        val player = _playerFlow.value ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(option.trackGroup, option.trackIndex))
            .build()
    }

    fun selectSubtitleTrack(option: MediaTrackOption?) {
        val player = _playerFlow.value ?: return
        if (option == null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            _subtitlesDisabled.value = true
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(option.trackGroup, option.trackIndex))
                .build()
            _subtitlesDisabled.value = false
        }
    }

    private fun refreshTrackLists(tracks: Tracks) {
        val audioList = mutableListOf<MediaTrackOption>()
        val subList = mutableListOf<MediaTrackOption>()
        var audioCounter = 1
        var subCounter = 1

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        if (!group.isTrackSupported(i)) continue
                        val format = group.getTrackFormat(i)
                        val lang = format.language?.takeIf { it.isNotBlank() && it != "und" }
                        val label = format.label?.takeIf { it.isNotBlank() }
                            ?: lang?.uppercase()
                            ?: "Audio Track $audioCounter"
                        audioList.add(
                            MediaTrackOption(
                                id = "audio_${group.mediaTrackGroup.id}_$i",
                                label = label,
                                language = lang,
                                isSelected = group.isTrackSelected(i),
                                trackGroup = group.mediaTrackGroup,
                                trackIndex = i
                            )
                        )
                        audioCounter++
                    }
                }

                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        if (!group.isTrackSupported(i)) continue
                        val format = group.getTrackFormat(i)
                        val lang = format.language?.takeIf { it.isNotBlank() && it != "und" }
                        val label = format.label?.takeIf { it.isNotBlank() }
                            ?: lang?.uppercase()
                            ?: "Subtitle $subCounter"
                        subList.add(
                            MediaTrackOption(
                                id = "sub_${group.mediaTrackGroup.id}_$i",
                                label = label,
                                language = lang,
                                isSelected = group.isTrackSelected(i),
                                trackGroup = group.mediaTrackGroup,
                                trackIndex = i
                            )
                        )
                        subCounter++
                    }
                }
            }
        }
        _audioTracks.value = audioList
        _subtitleTracks.value = subList
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
        val savedItems = if (preserveState && oldPlayer != null) {
            (0 until oldPlayer.mediaItemCount).map { oldPlayer.getMediaItemAt(it) }
        } else emptyList()
        val savedIndex = if (preserveState) oldPlayer?.currentMediaItemIndex ?: 0 else 0
        val savedPos = if (preserveState) oldPlayer?.currentPosition ?: 0L else 0L
        val savedPlay = if (preserveState) oldPlayer?.playWhenReady ?: true else false
        val savedSpeed = if (preserveState) oldPlayer?.playbackParameters?.speed ?: 1.0f else 1.0f
        val savedRepeat = if (preserveState) oldPlayer?.repeatMode ?: Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_OFF

        releaseLoudnessEnhancer()
        oldPlayer?.release()

        val newPlayer = buildNewPlayer(appContext, preferSoftware)
        _useSoftwareDecoder.value = preferSoftware
        _playerFlow.value = newPlayer
        onPlayerRebuiltListener?.invoke(newPlayer)

        if (savedItems.isNotEmpty()) {
            newPlayer.setMediaItems(savedItems, savedIndex, savedPos.coerceAtLeast(0L))
            newPlayer.setPlaybackSpeed(savedSpeed)
            newPlayer.repeatMode = savedRepeat
            newPlayer.prepare()
            newPlayer.playWhenReady = savedPlay
        }
        return newPlayer
    }

    fun syncSystemVolume(context: Context) {
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val currVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val basePercent = ((currVol.toFloat() / maxVol.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
            val currentTotal = _volumePercent.value
            if (currentTotal > 100 && basePercent >= 95) {
                applyLoudnessGainForPercent(currentTotal)
            } else {
                _volumePercent.value = basePercent
                applyLoudnessGainForPercent(basePercent)
            }
            _isMuted.value = _volumePercent.value == 0
        }
    }

    fun setVolumeWithBoost(context: Context, targetPercent: Int) {
        val clamped = targetPercent.coerceIn(0, 200)
        if (clamped > 0) lastNonZeroVolumePercent = clamped
        _volumePercent.value = clamped
        _isMuted.value = clamped == 0
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val streamPercent = clamped.coerceAtMost(100)
                val targetStreamVol = ((streamPercent / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamVol, 0)
            }
        }
        _playerFlow.value?.volume = if (clamped == 0) 0f else 1f
        applyLoudnessGainForPercent(clamped)
    }

    fun toggleMute(context: Context) {
        if (_isMuted.value || _volumePercent.value == 0) {
            setVolumeWithBoost(context, lastNonZeroVolumePercent.coerceAtLeast(50))
        } else {
            lastNonZeroVolumePercent = _volumePercent.value
            setVolumeWithBoost(context, 0)
        }
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

    fun stopPlaybackFromNotification() {
        _isBackgroundPlayEnabled.value = false
        _playerFlow.value?.let { player ->
            player.playWhenReady = false
            player.pause()
            player.stop()
            player.clearMediaItems()
        }
        _currentVideoUri.value = null
        _currentVideoTitle.value = ""
        _playerError.value = null
        _stopPlaybackSignal.tryEmit(Unit)
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
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _playerError.value = null
        _isBackgroundPlayEnabled.value = false
    }
}
