package com.example.data.model

data class VideoFolder(
    val bucketId: Long,
    val name: String,
    val pathHint: String,
    val videoCount: Int,
    val totalSizeBytes: Long,
    val latestModifiedSec: Long,
    val previewVideoId: Long,
    val previewUri: String
)

data class VideoItem(
    val id: Long,
    val uriString: String,
    val title: String,
    val displayName: String,
    val bucketId: Long,
    val folderName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateModifiedSec: Long,
    val mimeType: String
)
