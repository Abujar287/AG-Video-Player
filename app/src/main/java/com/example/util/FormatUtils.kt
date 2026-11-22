package com.example.util

import java.util.Locale
import kotlin.math.abs

object FormatUtils {
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatSignedDelta(deltaMs: Long): String {
        val sign = if (deltaMs >= 0) "+" else "-"
        return "$sign${formatDuration(abs(deltaMs))}"
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatResolution(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "Video"
        val minDim = minOf(width, height)
        val badge = when {
            minDim >= 2160 -> "4K"
            minDim >= 1440 -> "2K"
            minDim >= 1080 -> "1080p"
            minDim >= 720 -> "720p"
            minDim >= 480 -> "480p"
            else -> "${minDim}p"
        }
        return "$badge · ${width}×${height}"
    }
}
