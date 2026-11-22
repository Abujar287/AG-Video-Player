package com.example.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.FileProvider
import com.example.data.model.VideoFolder
import com.example.data.model.VideoItem
import com.example.util.PreviewEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class VideoLibrarySnapshot(
    val folders: List<VideoFolder>,
    val videosByFolder: Map<Long, List<VideoItem>>,
    val allVideos: List<VideoItem>
)

sealed interface PendingMediaOp {
    data class DeleteVideos(val videos: List<VideoItem>) : PendingMediaOp
    data class RenameVideo(val video: VideoItem, val newTitle: String) : PendingMediaOp
    data class MoveVideos(val videos: List<VideoItem>, val targetRelativePath: String) : PendingMediaOp
}

sealed interface MediaFileOpResult {
    data class Success(val message: String, val affectedUris: List<String> = emptyList()) : MediaFileOpResult
    data class NeedsPermission(
        val intentSender: IntentSender,
        val pendingOp: PendingMediaOp
    ) : MediaFileOpResult
    data class Error(val message: String) : MediaFileOpResult
}

class VideoLibraryRepository(private val context: Context) {

    private val thumbnailCache = object : LruCache<Long, Bitmap>(64) {}

    private val supportedVideoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
        "3gp", "3g2", "ts", "mts", "m2ts", "mpg", "mpeg", "vob", "ogv", "rmvb", "divx"
    )

    suspend fun scanVideos(): VideoLibrarySnapshot = withContext(Dispatchers.IO) {
        val seenIds = HashSet<Long>()
        val folderMetaMap = linkedMapOf<Long, MutableList<Pair<VideoItem, String>>>()

        val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        queryMediaCollection(
            collection = videoCollection,
            isFilesTable = false,
            seenIds = seenIds,
            folderMetaMap = folderMetaMap
        )

        val filesCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        queryMediaCollection(
            collection = filesCollection,
            isFilesTable = true,
            seenIds = seenIds,
            folderMetaMap = folderMetaMap
        )

        if (folderMetaMap.isEmpty() && PreviewEnvironment.isEmulator()) {
            loadPreviewLibrary(folderMetaMap)
        }

        val folders = folderMetaMap.map { (bucketId, entries) ->
            val sortedEntries = entries.sortedByDescending { it.first.dateModifiedSec }
            val firstItem = sortedEntries.first().first
            val pathHint = sortedEntries.first().second
            VideoFolder(
                bucketId = bucketId,
                name = firstItem.folderName,
                pathHint = pathHint,
                videoCount = sortedEntries.size,
                totalSizeBytes = sortedEntries.sumOf { it.first.sizeBytes },
                latestModifiedSec = sortedEntries.maxOf { it.first.dateModifiedSec },
                previewVideoId = firstItem.id,
                previewUri = firstItem.uriString
            )
        }.sortedBy { it.name.lowercase() }

        val byFolder = folderMetaMap.mapValues { (_, entries) ->
            entries.map { it.first }.sortedByDescending { it.dateModifiedSec }
        }
        val allVideos = byFolder.values.flatten()
        VideoLibrarySnapshot(
            folders = folders,
            videosByFolder = byFolder,
            allVideos = allVideos
        )
    }

    private fun queryMediaCollection(
        collection: Uri,
        isFilesTable: Boolean,
        seenIds: HashSet<Long>,
        folderMetaMap: LinkedHashMap<Long, MutableList<Pair<VideoItem, String>>>
    ) {
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.MediaColumns.DATA
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            pathColumn
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val displayNameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val durationCol = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val modifiedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val pathCol = cursor.getColumnIndex(pathColumn)

                if (idCol < 0) return@use

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    if (!seenIds.add(id)) continue

                    val displayName = if (displayNameCol >= 0) {
                        cursor.getString(displayNameCol) ?: "video_$id"
                    } else {
                        "video_$id"
                    }
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    val ext = displayName.substringAfterLast('.', "").lowercase()

                    if (isFilesTable) {
                        val isVideoMime = mimeType.startsWith("video/", ignoreCase = true)
                        val isVideoExt = ext in supportedVideoExtensions
                        if (!isVideoMime && !isVideoExt) {
                            seenIds.remove(id)
                            continue
                        }
                    }

                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    if (size == 0L && isFilesTable) continue

                    val rawPath = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                    val derivedFolderName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        rawPath.trimEnd('/').substringAfterLast('/', "").ifBlank { "Internal Storage" }
                    } else {
                        rawPath.substringBeforeLast('/', "").substringAfterLast('/', "").ifBlank { "Internal Storage" }
                    }

                    val rawBucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null
                    val folderName = rawBucketName?.takeIf { it.isNotBlank() } ?: derivedFolderName

                    val rawBucketId = if (bucketIdCol >= 0) cursor.getLong(bucketIdCol) else 0L
                    val bucketId = if (rawBucketId != 0L) rawBucketId else folderName.lowercase().hashCode().toLong()

                    val pathHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        rawPath.trimEnd('/').ifBlank { folderName }
                    } else {
                        rawPath.substringBeforeLast('/', folderName)
                    }

                    val title = if (titleCol >= 0) {
                        cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                            ?: displayName.substringBeforeLast('.')
                    } else {
                        displayName.substringBeforeLast('.')
                    }

                    val duration = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                    val width = if (widthCol >= 0) cursor.getInt(widthCol).coerceAtLeast(0) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol).coerceAtLeast(0) else 0
                    val modified = if (modifiedCol >= 0) cursor.getLong(modifiedCol).coerceAtLeast(0L) else 0L

                    val contentUri = ContentUris.withAppendedId(
                        if (isFilesTable && !mimeType.startsWith("video/", ignoreCase = true)) {
                            MediaStore.Files.getContentUri("external")
                        } else {
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        },
                        id
                    ).toString()

                    val item = VideoItem(
                        id = id,
                        uriString = contentUri,
                        title = title,
                        displayName = displayName,
                        bucketId = bucketId,
                        folderName = folderName,
                        relativePath = pathHint,
                        durationMs = duration,
                        sizeBytes = size,
                        width = width,
                        height = height,
                        dateModifiedSec = modified,
                        mimeType = mimeType.ifBlank { "video/*" }
                    )
                    folderMetaMap.getOrPut(bucketId) { mutableListOf() }.add(item to pathHint)
                }
            }
        }
    }

    suspend fun deleteVideos(videos: List<VideoItem>): MediaFileOpResult = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext MediaFileOpResult.Error("No videos selected.")
        val previewVideos = videos.filter { resolvePreviewFile(it.uriString) != null }
        if (previewVideos.size == videos.size) {
            val deletedUris = mutableListOf<String>()
            previewVideos.forEach { video ->
                val file = resolvePreviewFile(video.uriString)
                if (file != null && file.exists() && file.delete()) {
                    deletedUris.add(video.uriString)
                }
            }
            return@withContext if (deletedUris.isNotEmpty()) {
                MediaFileOpResult.Success("Deleted ${deletedUris.size} video(s)", deletedUris)
            } else {
                MediaFileOpResult.Error("Could not delete video(s).")
            }
        }
        val uris = videos.mapNotNull { runCatching { Uri.parse(it.uriString) }.getOrNull() }
        if (uris.isEmpty()) return@withContext MediaFileOpResult.Error("Invalid video URI.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Try direct delete first; if any require user approval, launch system delete dialog
            val remaining = mutableListOf<Uri>()
            val deletedUris = mutableListOf<String>()
            for (video in videos) {
                val uri = Uri.parse(video.uriString)
                val deleted = runCatching { context.contentResolver.delete(uri, null, null) }.getOrDefault(0)
                if (deleted > 0) {
                    deletedUris.add(video.uriString)
                } else {
                    remaining.add(uri)
                }
            }
            if (remaining.isEmpty()) {
                return@withContext MediaFileOpResult.Success(
                    "Deleted ${deletedUris.size} video(s)",
                    deletedUris
                )
            }
            return@withContext runCatching {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, remaining)
                MediaFileOpResult.NeedsPermission(pi.intentSender, PendingMediaOp.DeleteVideos(videos))
            }.getOrElse {
                MediaFileOpResult.Error(it.localizedMessage ?: "Failed to request delete permission.")
            }
        }

        // API 24–29
        try {
            val deletedUris = mutableListOf<String>()
            for (video in videos) {
                val uri = Uri.parse(video.uriString)
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) {
                    deletedUris.add(video.uriString)
                }
            }
            if (deletedUris.isNotEmpty()) {
                MediaFileOpResult.Success("Deleted ${deletedUris.size} video(s)", deletedUris)
            } else {
                MediaFileOpResult.Error("Could not delete video(s).")
            }
        } catch (sec: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
                MediaFileOpResult.NeedsPermission(
                    sec.userAction.actionIntent.intentSender,
                    PendingMediaOp.DeleteVideos(videos)
                )
            } else {
                MediaFileOpResult.Error(sec.localizedMessage ?: "Storage permission denied.")
            }
        } catch (e: Exception) {
            MediaFileOpResult.Error(e.localizedMessage ?: "Delete failed.")
        }
    }

    suspend fun renameVideo(video: VideoItem, newTitleInput: String): MediaFileOpResult =
        withContext(Dispatchers.IO) {
            val cleanBase = newTitleInput.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            if (cleanBase.isBlank()) return@withContext MediaFileOpResult.Error("Name cannot be empty.")

            val ext = video.displayName.substringAfterLast('.', "")
            val newDisplayName = if (ext.isNotBlank() && !cleanBase.endsWith(".$ext", ignoreCase = true)) {
                "$cleanBase.$ext"
            } else {
                cleanBase
            }
            val newTitle = newDisplayName.substringBeforeLast('.')
            val previewFile = resolvePreviewFile(video.uriString)
            if (previewFile != null && previewFile.exists()) {
                val dest = File(previewFile.parentFile, newDisplayName)
                return@withContext if (previewFile.renameTo(dest)) {
                    MediaFileOpResult.Success("Renamed to $newDisplayName", listOf(video.uriString))
                } else {
                    MediaFileOpResult.Error("Unable to rename preview video.")
                }
            }
            val uri = Uri.parse(video.uriString)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                        put(MediaStore.MediaColumns.TITLE, newTitle)
                    }
                    val updated = context.contentResolver.update(uri, values, null, null)
                    if (updated > 0) {
                        MediaFileOpResult.Success("Renamed to $newDisplayName", listOf(video.uriString))
                    } else {
                        MediaFileOpResult.Error("Rename was not applied by system.")
                    }
                } else {
                    val legacyPath = queryLegacyFilePath(uri)
                    if (legacyPath != null) {
                        val src = File(legacyPath)
                        val dest = File(src.parentFile, newDisplayName)
                        if (src.exists() && src.renameTo(dest)) {
                            val values = ContentValues().apply {
                                @Suppress("DEPRECATION")
                                put(MediaStore.MediaColumns.DATA, dest.absolutePath)
                                put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                                put(MediaStore.MediaColumns.TITLE, newTitle)
                            }
                            context.contentResolver.update(uri, values, null, null)
                            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                            MediaFileOpResult.Success("Renamed to $newDisplayName", listOf(video.uriString))
                        } else {
                            MediaFileOpResult.Error("Unable to rename file on storage.")
                        }
                    } else {
                        MediaFileOpResult.Error("File path unavailable.")
                    }
                }
            } catch (sec: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pi = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                    MediaFileOpResult.NeedsPermission(
                        pi.intentSender,
                        PendingMediaOp.RenameVideo(video, cleanBase)
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
                    MediaFileOpResult.NeedsPermission(
                        sec.userAction.actionIntent.intentSender,
                        PendingMediaOp.RenameVideo(video, cleanBase)
                    )
                } else {
                    MediaFileOpResult.Error(sec.localizedMessage ?: "Permission required to rename.")
                }
            } catch (e: Exception) {
                MediaFileOpResult.Error(e.localizedMessage ?: "Rename failed.")
            }
        }

    suspend fun moveVideos(
        videos: List<VideoItem>,
        targetRelativePathInput: String
    ): MediaFileOpResult = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext MediaFileOpResult.Error("No videos selected.")
        val previewVideos = videos.filter { resolvePreviewFile(it.uriString) != null }
        if (previewVideos.size == videos.size) {
            val folderSeg = targetRelativePathInput.trim().trim('/').replace(Regex("[\\\\:*?\"<>|]"), "_")
                .substringAfterLast('/')
                .ifBlank { "Movies" }
            val targetDir = File(previewRootDir(), folderSeg).apply { mkdirs() }
            val movedUris = mutableListOf<String>()
            previewVideos.forEach { video ->
                val src = resolvePreviewFile(video.uriString)
                if (src != null && src.exists()) {
                    val dest = File(targetDir, src.name)
                    if (src.absolutePath == dest.absolutePath || src.renameTo(dest) || copyFileFallback(src, dest)) {
                        movedUris.add(video.uriString)
                    }
                }
            }
            return@withContext if (movedUris.isNotEmpty()) {
                MediaFileOpResult.Success("Moved ${movedUris.size} video(s) to $folderSeg", movedUris)
            } else {
                MediaFileOpResult.Error("Could not move selected video(s).")
            }
        }
        val normalizedRelPath = normalizeTargetRelativePath(targetRelativePathInput)
        val uris = videos.map { Uri.parse(it.uriString) }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var movedCount = 0
                val movedUris = mutableListOf<String>()
                for (video in videos) {
                    val uri = Uri.parse(video.uriString)
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, normalizedRelPath)
                    }
                    val updated = context.contentResolver.update(uri, values, null, null)
                    if (updated > 0) {
                        movedCount++
                        movedUris.add(video.uriString)
                    } else {
                        // Fallback: copy to target MediaStore relative path and delete source
                        val copied = copyToTargetMediaStore(video, normalizedRelPath)
                        if (copied) {
                            context.contentResolver.delete(uri, null, null)
                            movedCount++
                            movedUris.add(video.uriString)
                        }
                    }
                }
                if (movedCount > 0) {
                    MediaFileOpResult.Success(
                        "Moved $movedCount video(s) to ${normalizedRelPath.trimEnd('/')}",
                        movedUris
                    )
                } else {
                    MediaFileOpResult.Error("Could not move selected video(s).")
                }
            } else {
                // Legacy API 24–28 file move
                @Suppress("DEPRECATION")
                val extRoot = Environment.getExternalStorageDirectory()
                val destDir = File(extRoot, normalizedRelPath.trim('/')).apply { mkdirs() }
                var movedCount = 0
                val movedUris = mutableListOf<String>()
                val pathsToScan = mutableListOf<String>()

                for (video in videos) {
                    val uri = Uri.parse(video.uriString)
                    val srcPath = queryLegacyFilePath(uri) ?: continue
                    val srcFile = File(srcPath)
                    val destFile = File(destDir, srcFile.name)
                    if (srcFile.exists() && (srcFile.renameTo(destFile) || copyFileFallback(srcFile, destFile))) {
                        val values = ContentValues().apply {
                            @Suppress("DEPRECATION")
                            put(MediaStore.MediaColumns.DATA, destFile.absolutePath)
                        }
                        context.contentResolver.update(uri, values, null, null)
                        pathsToScan.add(destFile.absolutePath)
                        movedCount++
                        movedUris.add(video.uriString)
                    }
                }
                if (pathsToScan.isNotEmpty()) {
                    MediaScannerConnection.scanFile(context, pathsToScan.toTypedArray(), null, null)
                }
                if (movedCount > 0) {
                    MediaFileOpResult.Success(
                        "Moved $movedCount video(s) to ${normalizedRelPath.trimEnd('/')}",
                        movedUris
                    )
                } else {
                    MediaFileOpResult.Error("Move failed on storage.")
                }
            }
        } catch (sec: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createWriteRequest(context.contentResolver, uris)
                MediaFileOpResult.NeedsPermission(
                    pi.intentSender,
                    PendingMediaOp.MoveVideos(videos, normalizedRelPath)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
                MediaFileOpResult.NeedsPermission(
                    sec.userAction.actionIntent.intentSender,
                    PendingMediaOp.MoveVideos(videos, normalizedRelPath)
                )
            } else {
                MediaFileOpResult.Error(sec.localizedMessage ?: "Permission required to move.")
            }
        } catch (e: Exception) {
            MediaFileOpResult.Error(e.localizedMessage ?: "Failed to move video(s).")
        }
    }

    private fun copyToTargetMediaStore(video: VideoItem, relativePath: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, video.displayName)
                put(MediaStore.Video.Media.TITLE, video.title)
                put(MediaStore.Video.Media.MIME_TYPE, video.mimeType.ifBlank { "video/mp4" })
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val destUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openInputStream(Uri.parse(video.uriString))?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)
            true
        }.getOrDefault(false)
    }

    private fun copyFileFallback(src: File, dest: File): Boolean {
        return runCatching {
            src.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            src.delete()
            true
        }.getOrDefault(false)
    }

    private fun normalizeTargetRelativePath(raw: String): String {
        val trimmed = raw.trim().trim('/')
        if (trimmed.isBlank()) return "${Environment.DIRECTORY_MOVIES}/"
        val top = trimmed.substringBefore('/')
        val allowedRoots = setOf(
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_PICTURES
        )
        val finalPath = if (allowedRoots.any { it.equals(top, ignoreCase = true) }) {
            trimmed
        } else {
            "${Environment.DIRECTORY_MOVIES}/$trimmed"
        }
        return "$finalPath/"
    }

    private fun queryLegacyFilePath(uri: Uri): String? {
        return runCatching {
            @Suppress("DEPRECATION")
            val proj = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private fun previewRootDir(): File = File(context.filesDir, "preview_videos")

    private fun resolvePreviewFile(uriString: String): File? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (uri.authority != "${context.packageName}.fileprovider") return null
        val path = uri.path ?: return null
        if (!path.startsWith("/preview_videos/")) return null
        val rel = path.removePrefix("/preview_videos/").trimStart('/')
        if (rel.isBlank()) return null
        val root = previewRootDir().canonicalFile
        val candidate = File(root, rel).canonicalFile
        return if (candidate.path.startsWith(root.path + File.separator)) candidate else null
    }

    private fun seedPreviewAssetsIfNeeded() {
        val root = previewRootDir()
        val marker = File(root, ".seeded")
        if (marker.exists()) return
        root.mkdirs()
        val assetFolders = runCatching { context.assets.list("preview_videos").orEmpty() }.getOrDefault(emptyArray())
        assetFolders.forEach { folder ->
            val files = runCatching { context.assets.list("preview_videos/$folder").orEmpty() }
                .getOrDefault(emptyArray())
            val targetDir = File(root, folder).apply { mkdirs() }
            files.forEach { fileName ->
                val outFile = File(targetDir, fileName)
                if (!outFile.exists()) {
                    runCatching {
                        context.assets.open("preview_videos/$folder/$fileName").use { input ->
                            outFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }
        runCatching { marker.writeText("1") }
    }

    private fun loadPreviewLibrary(
        folderMetaMap: LinkedHashMap<Long, MutableList<Pair<VideoItem, String>>>
    ) {
        seedPreviewAssetsIfNeeded()
        val root = previewRootDir()
        if (!root.exists()) return

        val files = root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in supportedVideoExtensions }
            .toList()

        val authority = "${context.packageName}.fileprovider"
        val retriever = MediaMetadataRetriever()
        try {
            files.forEach { file ->
                val folderName = file.parentFile?.takeIf { it != root }?.name ?: "Preview"
                val bucketId = folderName.lowercase().hashCode().toLong()
                val uri = runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull()
                    ?: return@forEach

                var durationMs = 0L
                var width = 0
                var height = 0
                runCatching {
                    retriever.setDataSource(file.absolutePath)
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                }

                val displayName = file.name
                val title = displayName.substringBeforeLast('.').replace('_', ' ')
                val id = (file.absolutePath.hashCode().toLong() and 0x7FFFFFFFL)
                val pathHint = "Movies/$folderName"

                val item = VideoItem(
                    id = id,
                    uriString = uri.toString(),
                    title = title,
                    displayName = displayName,
                    bucketId = bucketId,
                    folderName = folderName,
                    relativePath = pathHint,
                    durationMs = durationMs,
                    sizeBytes = file.length().coerceAtLeast(0L),
                    width = width,
                    height = height,
                    dateModifiedSec = (file.lastModified() / 1000L).coerceAtLeast(0L),
                    mimeType = "video/mp4"
                )
                folderMetaMap.getOrPut(bucketId) { mutableListOf() }.add(item to pathHint)
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun loadThumbnail(videoId: Long, uriString: String): Bitmap? {
        thumbnailCache.get(videoId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val previewFile = resolvePreviewFile(uriString)
            val bitmap = runCatching {
                if (previewFile != null && previewFile.exists()) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(previewFile.absolutePath)
                        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                } else {
                    val uri = Uri.parse(uriString)
                    val systemThumb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 180), null) }.getOrNull()
                    } else {
                        null
                    }
                    systemThumb ?: run {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, uri)
                            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } finally {
                            retriever.release()
                        }
                    }
                }
            }.getOrNull()
            if (bitmap != null) {
                thumbnailCache.put(videoId, bitmap)
            }
            bitmap
        }
    }
}
