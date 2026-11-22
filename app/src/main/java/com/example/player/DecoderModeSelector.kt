package com.example.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

object DecoderModeSelector {

    @OptIn(UnstableApi::class)
    fun buildRenderersFactory(
        context: Context,
        useSoftwareDecoder: Boolean
    ): DefaultRenderersFactory {
        val selector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val all = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )
            if (useSoftwareDecoder) {
                val sw = all.filter { isSoftwareCodec(it) }
                if (sw.isNotEmpty()) sw else all
            } else {
                val hw = all.filter { !isSoftwareCodec(it) }
                if (hw.isNotEmpty()) hw else all
            }
        }

        return DefaultRenderersFactory(context)
            .setExtensionRendererMode(
                if (useSoftwareDecoder) {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                } else {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                }
            )
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(selector)
    }

    @OptIn(UnstableApi::class)
    private fun isSoftwareCodec(info: MediaCodecInfo): Boolean {
        val name = info.name.lowercase()
        return info.softwareOnly ||
            name.startsWith("omx.google.") ||
            name.startsWith("c2.android.") ||
            name.contains(".sw.")
    }
}
