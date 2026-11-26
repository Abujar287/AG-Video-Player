package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey
    val videoUri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayedAt: Long,
    val playbackSpeed: Float = 1.0f,
    val useSoftwareDecoder: Boolean = false
)
