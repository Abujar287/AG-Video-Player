package com.example.data.repository

import android.content.Context
import com.example.data.local.PlaybackHistoryEntity
import com.example.data.local.VeloDatabase
import kotlinx.coroutines.flow.Flow

class PlaybackRepository(context: Context) {
    private val dao = VeloDatabase.getInstance(context).playbackHistoryDao()

    fun observeHistory(): Flow<List<PlaybackHistoryEntity>> = dao.observeAll()

    suspend fun getHistoryForUri(uri: String): PlaybackHistoryEntity? = dao.getByUri(uri)

    suspend fun getLatestHistory(): PlaybackHistoryEntity? = dao.getLatest()

    suspend fun savePlaybackState(
        videoUri: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float,
        useSoftwareDecoder: Boolean
    ) {
        if (videoUri.isBlank() || durationMs <= 0L) return
        val clampedPos = positionMs.coerceIn(0L, durationMs)
        val savedPos = if (durationMs > 5000L && clampedPos >= durationMs - 1500L) 0L else clampedPos
        dao.upsert(
            PlaybackHistoryEntity(
                videoUri = videoUri,
                title = title,
                positionMs = savedPos,
                durationMs = durationMs,
                lastPlayedAt = System.currentTimeMillis(),
                playbackSpeed = playbackSpeed,
                useSoftwareDecoder = useSoftwareDecoder
            )
        )
    }

    suspend fun clearPosition(videoUri: String) {
        dao.deleteByUri(videoUri)
    }
}
