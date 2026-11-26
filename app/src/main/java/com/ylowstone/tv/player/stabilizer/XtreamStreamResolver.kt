package com.ylowstone.tv.player.stabilizer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.util.concurrent.ConcurrentHashMap

enum class StreamProtocol(val shortLabel: String) {
    HLS_M3U8("HLS"),
    MPEG_TS("TS"),
    PROGRESSIVE_VOD("VOD")
}

data class XtreamCanonicalSource(
    val hostUrl: String,
    val username: String,
    val password: String,
    val streamId: String,
    val isLive: Boolean,
    val vodCategory: String = "movie",
    val vodExtension: String = "mp4"
) {
    val normalizedHost: String
        get() = hostUrl.trim().trimEnd('/')
}

data class ResolvedStream(
    val canonicalUrl: String,
    val protocol: StreamProtocol
)

/**
 * Menyusun URL kanonik Xtream Codes tanpa pernah menyimpan URL hasil redirect 302.
 * Live TV mengutamakan HLS (.m3u8) lalu otomatis fallback ke MPEG-TS (.ts)
 * jika server menolak manifest HLS.
 */
class XtreamStreamResolver {

    private val tsFallbackIds = ConcurrentHashMap.newKeySet<String>()

    fun resolve(source: XtreamCanonicalSource): ResolvedStream {
        val base = source.normalizedHost
        val user = Uri.encode(source.username.trim())
        val pass = Uri.encode(source.password.trim())
        val id = source.streamId.trim()

        if (!source.isLive) {
            val ext = source.vodExtension.trim().trimStart('.').ifEmpty { "mp4" }
            val url = "$base/${source.vodCategory}/$user/$pass/$id.$ext"
            return ResolvedStream(url, StreamProtocol.PROGRESSIVE_VOD)
        }

        val useTsFallback = tsFallbackIds.contains(id)
        return if (useTsFallback) {
            ResolvedStream("$base/live/$user/$pass/$id.ts", StreamProtocol.MPEG_TS)
        } else {
            ResolvedStream("$base/live/$user/$pass/$id.m3u8", StreamProtocol.HLS_M3U8)
        }
    }

    fun markHlsUnsupported(streamId: String): Boolean {
        return tsFallbackIds.add(streamId.trim())
    }

    fun isUsingTsFallback(streamId: String): Boolean {
        return tsFallbackIds.contains(streamId.trim())
    }

    fun clearSessionFallbacks() {
        tsFallbackIds.clear()
    }

    @OptIn(UnstableApi::class)
    fun buildMediaSource(
        resolved: ResolvedStream,
        dataSourceFactory: DataSource.Factory
    ): MediaSource {
        val uri = Uri.parse(resolved.canonicalUrl)
        return when (resolved.protocol) {
            StreamProtocol.HLS_M3U8 -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
                HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(mediaItem)
            }
            StreamProtocol.MPEG_TS -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.VIDEO_MP2T)
                    .build()
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            StreamProtocol.PROGRESSIVE_VOD -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .build()
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
        }
    }
}
