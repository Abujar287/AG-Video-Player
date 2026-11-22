package com.example.data.repository

import com.example.data.local.PlaybackHistoryDao
import com.example.data.local.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaybackRepository(private val dao: PlaybackHistoryDao) {

    val historyByUri: Flow<Map<String, PlaybackHistoryEntity>> =
        dao.observeAll().map { list -> list.associateBy { it.videoUri } }

    val recentHistory: Flow<List<PlaybackHistoryEntity>> = dao.observeAll()

    suspend fun getSavedState(videoUri: String): PlaybackHistoryEntity? = dao.getByUri(videoUri)

    suspend fun savePlaybackState(
        videoUri: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float,
        useSoftwareDecoder: Boolean
    ) {
        if (videoUri.isBlank()) return
        // If playback is virtually complete (>97% and within final 3s), reset resume point to 0
        val normalizedPos = if (durationMs > 10_000L && positionMs >= durationMs - 3_000L) {
            0L
        } else {
            positionMs.coerceAtLeast(0L)
        }
        dao.upsert(
            PlaybackHistoryEntity(
                videoUri = videoUri,
                title = title,
                positionMs = normalizedPos,
                durationMs = durationMs.coerceAtLeast(0L),
                lastPlayedAt = System.currentTimeMillis(),
                playbackSpeed = playbackSpeed,
                useSoftwareDecoder = useSoftwareDecoder
            )
        )
    }

    suspend fun clearHistory(videoUri: String) = dao.deleteByUri(videoUri)
}
