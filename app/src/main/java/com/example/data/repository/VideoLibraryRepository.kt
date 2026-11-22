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

    suspend fun scanVideos(): VideoLibrarySnapshot = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.RELATIVE_PATH
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Media.DATA
            }
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val folderMetaMap = linkedMapOf<Long, MutableList<Pair<VideoItem, String>>>()

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val pathCol = cursor.getColumnIndex(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.RELATIVE_PATH
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Media.DATA
                }
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(displayNameCol) ?: "video_$id.mp4"
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.')
                val bucketId = cursor.getLong(bucketIdCol)
                val folderName = cursor.getString(bucketNameCol)?.takeIf { it.isNotBlank() }
                    ?: "Internal Storage"
                val duration = cursor.getLong(durationCol).coerceAtLeast(0L)
                val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                val width = cursor.getInt(widthCol).coerceAtLeast(0)
                val height = cursor.getInt(heightCol).coerceAtLeast(0)
                val modified = cursor.getLong(modifiedCol).coerceAtLeast(0L)
                val mimeType = cursor.getString(mimeCol) ?: "video/*"
                val rawPath = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                val pathHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    rawPath.trimEnd('/').ifBlank { folderName }
                } else {
                    rawPath.substringBeforeLast('/', folderName)
                }

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
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
                    mimeType = mimeType
                )
                videos.add(item)
                folderMetaMap.getOrPut(bucketId) { mutableListOf() }.add(item to pathHint)
            }
        }

        val folders = folderMetaMap.map { (bucketId, entries) ->
            val firstItem = entries.first().first
            val pathHint = entries.first().second
            VideoFolder(
                bucketId = bucketId,
                name = firstItem.folderName,
                pathHint = pathHint,
                videoCount = entries.size,
                totalSizeBytes = entries.sumOf { it.first.sizeBytes },
                latestModifiedSec = entries.maxOf { it.first.dateModifiedSec },
                previewVideoId = firstItem.id,
                previewUri = firstItem.uriString
            )
        }.sortedByDescending { it.latestModifiedSec }

        val byFolder = folderMetaMap.mapValues { (_, entries) -> entries.map { it.first } }
        VideoLibrarySnapshot(
            folders = folders,
            videosByFolder = byFolder,
            allVideos = videos
        )
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
