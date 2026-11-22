package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.example.data.model.VideoFolder
import com.example.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoLibrarySnapshot(
    val folders: List<VideoFolder>,
    val videosByFolder: Map<Long, List<VideoItem>>,
    val allVideos: List<VideoItem>
)

class VideoLibraryRepository(private val context: Context) {

    private val thumbnailCache = object : LruCache<Long, Bitmap>(64) {}

    private val supportedVideoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
        "3gp", "3g2", "ts", "mts", "m2ts", "mpg", "mpeg", "vob", "ogv", "rmvb", "divx"
    )

    suspend fun scanVideos(): VideoLibrarySnapshot = withContext(Dispatchers.IO) {
        val seenIds = HashSet<Long>()
        val folderMetaMap = linkedMapOf<Long, MutableList<Pair<VideoItem, String>>>()

        // 1. Primary scan: MediaStore.Video.Media across all external volumes (internal + SD card)
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

        // 2. Secondary scan: MediaStore.Files for exotic video containers (.mkv, .ts, .flv, .avi, etc.)
        // that MediaScanner occasionally stores without a video/* MIME tag
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

    suspend fun loadThumbnail(videoId: Long, uriString: String): Bitmap? {
        thumbnailCache.get(videoId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bitmap = runCatching {
                val uri = Uri.parse(uriString)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
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
