package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.model.VideoItem
import java.io.File

object PreviewEnvironment {
    private const val TAG = "PreviewEnvironment"

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
        val result = mutableListOf<VideoItem>()
        val rootDir = File(context.filesDir, "preview_videos").apply { mkdirs() }
        val folders = listOf("Downloads", "Movies")
        val nowSec = System.currentTimeMillis() / 1000L

        var idx = 0L
        for (folder in folders) {
            val assetFiles = runCatching { context.assets.list("preview_videos/$folder") }.getOrNull() ?: continue
            val targetFolder = File(rootDir, folder).apply { mkdirs() }
            for (fileName in assetFiles) {
                if (!fileName.endsWith(".mp4", ignoreCase = true)) continue
                val destFile = File(targetFolder, fileName)
                if (!destFile.exists() || destFile.length() == 0L) {
                    runCatching {
                        context.assets.open("preview_videos/$folder/$fileName").use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }.onFailure { Log.w(TAG, "Failed extracting asset $fileName", it) }
                }
                if (!destFile.exists()) continue

                var durationMs = 15_000L
                var width = 1280
                var height = 720
                val retriever = MediaMetadataRetriever()
                runCatching {
                    retriever.setDataSource(destFile.absolutePath)
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15_000L
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
                }.also {
                    runCatching { retriever.release() }
                }

                idx += 1L
                val title = fileName.substringBeforeLast('.')
                result += VideoItem(
                    id = 90_000L + idx,
                    uriString = Uri.fromFile(destFile).toString(),
                    title = title,
                    displayName = fileName,
                    bucketId = "preview_$folder",
                    folderName = folder,
                    relativePath = "$folder/",
                    filePath = destFile.absolutePath,
                    durationMs = durationMs,
                    sizeBytes = destFile.length(),
                    width = width,
                    height = height,
                    dateModifiedSec = nowSec - idx * 600L,
                    mimeType = "video/mp4"
                )
            }
        }
        return result
    }
}
