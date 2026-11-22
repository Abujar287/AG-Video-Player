package com.example.player

import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Custom MediaCodecSelector that prioritizes either Hardware (HW) or Software (SW) decoders
 * while keeping fallback codecs available so playback never fails on unsupported formats.
 */
@OptIn(UnstableApi::class)
class DecoderModeSelector(private val preferSoftware: Boolean) : MediaCodecSelector {

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): MutableList<MediaCodecInfo> {
        val defaultInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder
        )
        if (defaultInfos.size <= 1) return defaultInfos.toMutableList()

        val sorted = if (preferSoftware) {
            defaultInfos.sortedByDescending { isSoftwareCodec(it) }
        } else {
            defaultInfos.sortedBy { isSoftwareCodec(it) }
        }
        return sorted.toMutableList()
    }

    private fun isSoftwareCodec(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info.softwareOnly) {
            return true
        }
        val name = info.name.lowercase()
        return name.startsWith("omx.google.") ||
            name.startsWith("c2.android.") ||
            name.contains(".sw.") ||
            name.contains("ffmpeg")
    }
}
