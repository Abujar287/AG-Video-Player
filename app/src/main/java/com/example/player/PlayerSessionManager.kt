package com.example.player

import com.example.data.model.PlaylistEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveSessionMetadata(
    val playlist: List<PlaylistEntry> = emptyList(),
    val currentIndex: Int = 0,
    val isPlayingInBackground: Boolean = false
)

object PlayerSessionManager {
    private val _sessionMeta = MutableStateFlow(ActiveSessionMetadata())
    val sessionMeta: StateFlow<ActiveSessionMetadata> = _sessionMeta.asStateFlow()

    fun setPlaylist(entries: List<PlaylistEntry>, startIndex: Int) {
        _sessionMeta.value = ActiveSessionMetadata(
            playlist = entries,
            currentIndex = startIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)),
            isPlayingInBackground = false
        )
    }

    fun updateCurrentIndex(index: Int) {
        val curr = _sessionMeta.value
        _sessionMeta.value = curr.copy(
            currentIndex = index.coerceIn(0, (curr.playlist.size - 1).coerceAtLeast(0))
        )
    }

    fun setBackgroundPlaying(active: Boolean) {
        _sessionMeta.value = _sessionMeta.value.copy(isPlayingInBackground = active)
    }

    fun clear() {
        _sessionMeta.value = ActiveSessionMetadata()
    }
}
