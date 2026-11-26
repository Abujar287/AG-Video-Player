package com.example.util

import android.content.Context
import android.os.Build
import com.example.data.model.VideoItem

object PreviewEnvironment {

    fun isPreviewHost(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()
        return fp.contains("generic") ||
            fp.contains("emulator") ||
            model.contains("sdk_gphone") ||
            model.contains("emulator") ||
            product.contains("sdk")
    }

    fun loadBundledPreviewVideos(context: Context): List<VideoItem> {
        val nowSec = System.currentTimeMillis() / 1000L
        return listOf(
            VideoItem(
                id = 90001L,
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sample_Lecture_Clip.mp4",
                title = "Sample Lecture Clip",
                displayName = "Sample_Lecture_Clip.mp4",
                bucketId = "preview_Downloads",
                folderName = "Downloads",
                relativePath = "Downloads/",
                filePath = null,
                durationMs = 30_000L,
                sizeBytes = 1_048_576L,
                width = 1280,
                height = 720,
                dateModifiedSec = nowSec - 600L,
                mimeType = "video/mp4"
            ),
            VideoItem(
                id = 90002L,
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                title = "Cinema Test Reel",
                displayName = "Cinema_Test_Reel.mp4",
                bucketId = "preview_Movies",
                folderName = "Movies",
                relativePath = "Movies/",
                filePath = null,
                durationMs = 60_000L,
                sizeBytes = 2_097_152L,
                width = 1920,
                height = 1080,
                dateModifiedSec = nowSec - 1200L,
                mimeType = "video/mp4"
            ),
            VideoItem(
                id = 90003L,
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                title = "Neon Horizon Preview",
                displayName = "Neon_Horizon_Preview.mp4",
                bucketId = "preview_Movies",
                folderName = "Movies",
                relativePath = "Movies/",
                filePath = null,
                durationMs = 45_000L,
                sizeBytes = 1_572_864L,
                width = 1920,
                height = 1080,
                dateModifiedSec = nowSec - 1800L,
                mimeType = "video/mp4"
            ),
            VideoItem(
                id = 90004L,
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                title = "Ocean Pulse Clip",
                displayName = "Ocean_Pulse_Clip.mp4",
                bucketId = "preview_Movies",
                folderName = "Movies",
                relativePath = "Movies/",
                filePath = null,
                durationMs = 50_000L,
                sizeBytes = 1_835_008L,
                width = 1280,
                height = 720,
                dateModifiedSec = nowSec - 2400L,
                mimeType = "video/mp4"
            )
        )
    }
}
