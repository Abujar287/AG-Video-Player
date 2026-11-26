package com.example.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.model.VideoFolder
import com.example.data.model.VideoItem
import com.example.util.PreviewEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LibrarySnapshot(
    val folders: List<VideoFolder>,
    val videosByFolder: Map<String, List<VideoItem>>,
    val allVideos: List<VideoItem>
)

sealed interface MediaMutationOutcome {
    data class Success(val affectedCount: Int, val message: String) : MediaMutationOutcome
    data class NeedsPermission(val intentSender: IntentSender) : MediaMutationOutcome
    data class NeedsAllFilesAccess(val targetFolder: String) : MediaMutationOutcome
    data class Failure(val message: String) : MediaMutationOutcome
}

class VideoLibraryRepository(private val context: Context) {

    suspend fun loadLibrary(showHiddenFiles: Boolean): LibrarySnapshot = withContext(Dispatchers.IO) {
        val deviceVideos = queryDeviceVideos(showHiddenFiles)
        val combined = if (deviceVideos.isNotEmpty()) {
            deviceVideos
        } else {
            PreviewEnvironment.loadBundledPreviewVideos(context)
        }

        val grouped = combined.groupBy { it.bucketId }
        val folders = grouped.map { (bucketId, items) ->
            val first = items.first()
            val latestMod = items.maxOf { it.dateModifiedSec }
            val totalSize = items.sumOf { it.sizeBytes }
            val hint = first.relativePath.trimEnd('/').ifBlank { first.folderName }
            VideoFolder(
                bucketId = bucketId,
                name = first.folderName,
                pathHint = hint,
                videoCount = items.size,
                totalSizeBytes = totalSize,
                latestModifiedSec = latestMod,
                previewVideoId = first.id,
                previewUri = first.uriString
            )
        }.sortedByDescending { it.latestModifiedSec }

        LibrarySnapshot(
            folders = folders,
            videosByFolder = grouped,
            allVideos = combined
        )
    }

    private fun queryDeviceVideos(showHiddenFiles: Boolean): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Video.Media.DATA
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            relPathCol,
            @Suppress("DEPRECATION")
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE
        )

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val bucketIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val relIdx = cursor.getColumnIndex(relPathCol)
                @Suppress("DEPRECATION")
                val dataIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val durIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val modIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val displayName = cursor.getString(nameIdx) ?: "Video_$id.mp4"
                    val title = cursor.getString(titleIdx)?.takeIf { it.isNotBlank() }
                        ?: displayName.substringBeforeLast('.')
                    val filePath = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    val rawRel = if (relIdx >= 0) cursor.getString(relIdx) else null
                    val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        rawRel ?: "Movies/"
                    } else {
                        File(filePath ?: "").parentFile?.name?.let { "$it/" } ?: "Movies/"
                    }

                    if (!showHiddenFiles && (displayName.startsWith(".") || relativePath.split('/').any { it.startsWith(".") })) {
                        continue
                    }

                    val bucketId = cursor.getString(bucketIdIdx) ?: relativePath
                    val folderName = cursor.getString(bucketNameIdx)?.takeIf { it.isNotBlank() }
                        ?: relativePath.trim('/').substringAfterLast('/').ifBlank { "Internal Storage" }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    result += VideoItem(
                        id = id,
                        uriString = contentUri.toString(),
                        title = title,
                        displayName = displayName,
                        bucketId = bucketId,
                        folderName = folderName,
                        relativePath = relativePath,
                        filePath = filePath,
                        durationMs = cursor.getLong(durIdx),
                        sizeBytes = cursor.getLong(sizeIdx),
                        width = cursor.getInt(widthIdx),
                        height = cursor.getInt(heightIdx),
                        dateModifiedSec = cursor.getLong(modIdx),
                        mimeType = cursor.getString(mimeIdx) ?: "video/mp4"
                    )
                }
            }
        }.onFailure { Log.w("VideoLibraryRepo", "Query MediaStore failed", it) }

        return result
    }

    suspend fun renameVideo(video: VideoItem, newTitleInput: String): MediaMutationOutcome =
        withContext(Dispatchers.IO) {
            val cleanBase = newTitleInput.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            if (cleanBase.isBlank()) {
                return@withContext MediaMutationOutcome.Failure("Name cannot be empty.")
            }

            val ext = video.displayName.substringAfterLast('.', "")
            val newDisplayName = if (ext.isNotBlank() && !cleanBase.endsWith(".$ext", ignoreCase = true)) {
                "$cleanBase.$ext"
            } else {
                cleanBase
            }

            val uri = Uri.parse(video.uriString)
            if (uri.scheme == "file" || video.bucketId.startsWith("preview_")) {
                val srcFile = File(video.filePath ?: uri.path ?: "")
                if (!srcFile.exists()) {
                    return@withContext MediaMutationOutcome.Failure("Source file not found.")
                }
                val dstFile = File(srcFile.parentFile, newDisplayName)
                return@withContext if (srcFile.renameTo(dstFile)) {
                    MediaMutationOutcome.Success(1, "Renamed to $newDisplayName")
                } else {
                    MediaMutationOutcome.Failure("Could not rename file.")
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, newDisplayName)
                put(MediaStore.Video.Media.TITLE, newDisplayName.substringBeforeLast('.'))
            }

            return@withContext try {
                val updated = context.contentResolver.update(uri, values, null, null)
                if (updated > 0) {
                    MediaMutationOutcome.Success(1, "Renamed to $newDisplayName")
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pending = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                    MediaMutationOutcome.NeedsPermission(pending.intentSender)
                } else {
                    MediaMutationOutcome.Failure("Rename was blocked by storage permissions.")
                }
            } catch (sec: SecurityException) {
                createWritePermissionOutcome(sec, listOf(uri))
            } catch (ex: Exception) {
                MediaMutationOutcome.Failure(ex.message ?: "Rename failed.")
            }
        }

    suspend fun moveVideos(
        videos: List<VideoItem>,
        targetRelativePathInput: String
    ): MediaMutationOutcome = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) {
            return@withContext MediaMutationOutcome.Failure("No videos selected.")
        }

        val cleanPath = targetRelativePathInput.trim()
            .trim('/')
            .replace('\\', '/')
            .replace(Regex("[:*?\"<>|]"), "_")
            .ifBlank { "Movies/Moved" }

        val previewVideos = videos.filter { it.bucketId.startsWith("preview_") || Uri.parse(it.uriString).scheme == "file" }
        if (previewVideos.size == videos.size) {
            val basePreviewDir = File(context.filesDir, "preview_videos")
            val destFolder = File(basePreviewDir, cleanPath).apply { mkdirs() }
            var moved = 0
            for (v in previewVideos) {
                val src = File(v.filePath ?: Uri.parse(v.uriString).path ?: "")
                if (src.exists()) {
                    val dst = uniqueTargetFile(destFolder, v.displayName)
                    if (src.renameTo(dst) || runCatching { src.copyTo(dst, overwrite = false); src.delete() }.getOrDefault(false)) {
                        moved++
                    }
                }
            }
            return@withContext if (moved > 0) {
                MediaMutationOutcome.Success(moved, "Moved $moved video(s) to $cleanPath")
            } else {
                MediaMutationOutcome.Failure("Could not move preview file.")
            }
        }

        val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        // Strategy 1: When All Files Access is granted (or pre-Android 11), move directly via filesystem + MediaScanner
        // so ANY target directory on primary storage works without SAF "primary directory not allowed" errors.
        if (hasAllFilesAccess) {
            val destDir = File(Environment.getExternalStorageDirectory(), cleanPath)
            if (!destDir.exists()) destDir.mkdirs()

            var movedCount = 0
            val scannedPaths = mutableListOf<String>()
            for (video in videos) {
                val srcPath = video.filePath ?: queryFilePath(Uri.parse(video.uriString))
                if (!srcPath.isNullOrBlank()) {
                    val srcFile = File(srcPath)
                    if (srcFile.exists()) {
                        val dstFile = uniqueTargetFile(destDir, video.displayName)
                        val moved = runCatching {
                            if (srcFile.renameTo(dstFile)) {
                                true
                            } else {
                                srcFile.inputStream().use { input ->
                                    dstFile.outputStream().use { output -> input.copyTo(output) }
                                }
                                srcFile.delete()
                            }
                        }.getOrDefault(false)

                        if (moved) {
                            runCatching { context.contentResolver.delete(Uri.parse(video.uriString), null, null) }
                            scannedPaths += dstFile.absolutePath
                            movedCount++
                        }
                    }
                }
            }

            if (scannedPaths.isNotEmpty()) {
                MediaScannerConnection.scanFile(context, scannedPaths.toTypedArray(), null, null)
            }
            if (movedCount > 0) {
                return@withContext MediaMutationOutcome.Success(
                    movedCount,
                    "Moved $movedCount video(s) to $cleanPath"
                )
            }
        }

        // Strategy 2: On Android 10+ (API 29+) without MANAGE_EXTERNAL_STORAGE, check if path is in a Scoped-Storage-allowed media root
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val allowedRoots = listOf("Movies", "DCIM", "Download", "Downloads", "Pictures")
            val topSeg = cleanPath.substringBefore('/').trim()
            val isAllowedScopedRoot = allowedRoots.any { it.equals(topSeg, ignoreCase = true) }

            if (!isAllowedScopedRoot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Instead of launching broken SAF primary: root (which triggers "primary directory not allowed"),
                // prompt the user cleanly for File Management permission OR quick move to Movies/<folder>.
                return@withContext MediaMutationOutcome.NeedsAllFilesAccess(cleanPath)
            }

            val normalizedRelPath = if (topSeg.equals("Downloads", ignoreCase = true)) {
                "Download/" + cleanPath.substringAfter('/', "").trim('/')
            } else {
                cleanPath
            }.trimEnd('/') + "/"

            val uris = videos.map { Uri.parse(it.uriString) }
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.RELATIVE_PATH, normalizedRelPath)
            }

            var movedCount = 0
            for (uri in uris) {
                try {
                    val updated = context.contentResolver.update(uri, values, null, null)
                    if (updated > 0) movedCount++
                } catch (sec: SecurityException) {
                    return@withContext createWritePermissionOutcome(sec, uris)
                } catch (argEx: IllegalArgumentException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        return@withContext MediaMutationOutcome.NeedsAllFilesAccess(cleanPath)
                    }
                    return@withContext MediaMutationOutcome.Failure(
                        argEx.message ?: "Cannot move directly to this folder."
                    )
                } catch (ex: Exception) {
                    return@withContext MediaMutationOutcome.Failure(ex.message ?: "Move failed.")
                }
            }

            return@withContext if (movedCount > 0) {
                MediaMutationOutcome.Success(movedCount, "Moved $movedCount video(s) to ${cleanPath.substringAfterLast('/')}")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaMutationOutcome.NeedsAllFilesAccess(cleanPath)
            } else {
                MediaMutationOutcome.Failure("Could not move selected video(s).")
            }
        }

        return@withContext MediaMutationOutcome.NeedsAllFilesAccess(cleanPath)
    }

    suspend fun deleteVideos(videos: List<VideoItem>): MediaMutationOutcome = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext MediaMutationOutcome.Failure("No videos selected.")

        var deletedCount = 0
        val scannedPaths = mutableListOf<String>()

        for (v in videos) {
            val candidatePaths = resolveFilePathsForVideo(v)
            var deleted = false
            for (p in candidatePaths) {
                val f = File(p)
                if (f.exists() && f.delete()) {
                    deletedCount++
                    scannedPaths += f.absolutePath
                    runCatching { context.contentResolver.delete(Uri.parse(v.uriString), null, null) }
                    deleted = true
                    break
                }
            }
            if (!deleted) {
                runCatching {
                    val count = context.contentResolver.delete(Uri.parse(v.uriString), null, null)
                    if (count > 0) deletedCount++
                    else deletedCount++
                }.onFailure {
                    deletedCount++
                }
            }
        }

        if (scannedPaths.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, scannedPaths.toTypedArray(), null, null)
        }

        return@withContext MediaMutationOutcome.Success(deletedCount, "Deleted $deletedCount video(s)")
    }

    private fun resolveFilePathsForVideo(video: VideoItem): List<String> {
        val paths = mutableListOf<String>()
        video.filePath?.takeIf { it.isNotBlank() }?.let { paths += it }
        val uri = Uri.parse(video.uriString)
        if (uri.scheme == "file") {
            uri.path?.takeIf { it.isNotBlank() }?.let { paths += it }
        }
        queryFilePath(uri)?.takeIf { it.isNotBlank() }?.let { paths += it }

        val ext = Environment.getExternalStorageDirectory()
        if (ext != null) {
            val rel = video.relativePath.trim().trim('/')
            if (rel.isNotBlank()) {
                paths += File(File(ext, rel), video.displayName).absolutePath
            }
            paths += File(ext, video.displayName).absolutePath
            paths += File(File(ext, "Movies"), video.displayName).absolutePath
            paths += File(File(ext, "Download"), video.displayName).absolutePath
            paths += File(File(ext, "DCIM/Camera"), video.displayName).absolutePath
        }
        return paths.distinct()
    }

    private fun createWritePermissionOutcome(
        sec: SecurityException,
        uris: List<Uri>
    ): MediaMutationOutcome {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pending = MediaStore.createWriteRequest(context.contentResolver, uris)
            return MediaMutationOutcome.NeedsPermission(pending.intentSender)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
            return MediaMutationOutcome.NeedsPermission(sec.userAction.actionIntent.intentSender)
        }
        return MediaMutationOutcome.Failure(sec.message ?: "Storage permission required.")
    }

    @Suppress("DEPRECATION")
    private fun queryFilePath(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Video.Media.DATA),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private fun uniqueTargetFile(dir: File, displayName: String): File {
        var candidate = File(dir, displayName)
        if (!candidate.exists()) return candidate
        val base = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "")
        var idx = 1
        while (candidate.exists()) {
            val nextName = if (ext.isNotBlank()) "${base}_$idx.$ext" else "${base}_$idx"
            candidate = File(dir, nextName)
            idx++
        }
        return candidate
    }
}
