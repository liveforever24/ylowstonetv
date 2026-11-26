package com.ylowstone.tv.player.stabilizer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ResolvedStream(
    val canonicalUrl: String,
    val protocol: StreamProtocol,
    val isLive: Boolean,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Resolver universal v1.2 untuk semua tipe aliran YlowstoneTV:
 * Xtream Codes, URL M3U/M3U8, file lokal, .ts, .mp4/VOD, .mpd, link tanpa ekstensi,
 * serta tautan video web (melalui WebVideoStreamExtractor).
 */
class XtreamStreamResolver(
    private val webVideoExtractor: WebVideoStreamExtractor = PassthroughWebVideoExtractor()
) {
    private val tsFallbackKeys = ConcurrentHashMap.newKeySet<String>()

    suspend fun resolve(source: YlowstoneStreamSource): ResolvedStream? {
        return when (source) {
            is YlowstoneStreamSource.Xtream -> resolveXtream(source)
            is YlowstoneStreamSource.DirectUrl -> resolveDirectUrl(source)
            is YlowstoneStreamSource.LocalMedia -> resolveLocalMedia(source)
            is YlowstoneStreamSource.Vod -> resolveVod(source)
            is YlowstoneStreamSource.WebVideo -> resolveWebVideo(source)
        }
    }

    private fun resolveXtream(source: YlowstoneStreamSource.Xtream): ResolvedStream {
        val base = source.normalizedHost
        val user = Uri.encode(source.username.trim())
        val pass = Uri.encode(source.password.trim())
        val id = source.streamId.trim()

        if (!source.isLive) {
            val ext = source.vodExtension.trim().trimStart('.').ifEmpty { "mp4" }
            val url = "$base/${source.vodCategory}/$user/$pass/$id.$ext"
            return ResolvedStream(
                canonicalUrl = url,
                protocol = StreamProtocol.PROGRESSIVE_VOD,
                isLive = false,
                headers = source.customHeaders
            )
        }

        val fallbackKey = fallbackKeyFor(source)
        val useTs = tsFallbackKeys.contains(fallbackKey)
        return if (useTs) {
            ResolvedStream(
                canonicalUrl = "$base/live/$user/$pass/$id.ts",
                protocol = StreamProtocol.MPEG_TS,
                isLive = true,
                headers = source.customHeaders
            )
        } else {
            ResolvedStream(
                canonicalUrl = "$base/live/$user/$pass/$id.m3u8",
                protocol = StreamProtocol.HLS_M3U8,
                isLive = true,
                headers = source.customHeaders
            )
        }
    }

    private fun resolveDirectUrl(source: YlowstoneStreamSource.DirectUrl): ResolvedStream {
        val raw = source.rawUrl.trim()
        val fallbackKey = fallbackKeyFor(source)
        if (tsFallbackKeys.contains(fallbackKey) && raw.lowercase(Locale.ROOT).endsWith(".m3u8")) {
            val tsUrl = raw.substring(0, raw.length - 5) + ".ts"
            return ResolvedStream(
                canonicalUrl = tsUrl,
                protocol = StreamProtocol.MPEG_TS,
                isLive = source.isLive,
                headers = source.customHeaders
            )
        }

        val protocol = detectProtocolFromUrl(raw, defaultIfUnknown = StreamProtocol.AUTO_DETECT)
        return ResolvedStream(
            canonicalUrl = raw,
            protocol = protocol,
            isLive = source.isLive,
            headers = source.customHeaders
        )
    }

    private fun resolveLocalMedia(source: YlowstoneStreamSource.LocalMedia): ResolvedStream {
        val raw = source.uriString.trim()
        val uriString = if (raw.startsWith("/")) Uri.fromFile(File(raw)).toString() else raw
        val protocol = detectProtocolFromUrl(uriString, defaultIfUnknown = StreamProtocol.AUTO_DETECT)
        return ResolvedStream(
            canonicalUrl = uriString,
            protocol = protocol,
            isLive = source.isLive,
            headers = source.customHeaders
        )
    }

    private fun resolveVod(source: YlowstoneStreamSource.Vod): ResolvedStream {
        val raw = source.rawUrl.trim()
        val protocol = detectProtocolFromUrl(raw, defaultIfUnknown = StreamProtocol.PROGRESSIVE_VOD)
        return ResolvedStream(
            canonicalUrl = raw,
            protocol = protocol,
            isLive = false,
            headers = source.customHeaders
        )
    }

    private suspend fun resolveWebVideo(source: YlowstoneStreamSource.WebVideo): ResolvedStream? {
        val extracted = webVideoExtractor.extract(source.pageUrl) ?: return null
        val mergedHeaders = source.customHeaders + extracted.headers
        return ResolvedStream(
            canonicalUrl = extracted.mediaUrl,
            protocol = if (extracted.protocol == StreamProtocol.AUTO_DETECT) StreamProtocol.WEB_VIDEO else extracted.protocol,
            isLive = source.isLive,
            headers = mergedHeaders
        )
    }

    fun markHlsUnsupported(source: YlowstoneStreamSource): Boolean {
        return tsFallbackKeys.add(fallbackKeyFor(source))
    }

    fun clearSessionFallbacks() {
        tsFallbackKeys.clear()
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
            StreamProtocol.DASH_MPD -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
                DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
            }
            StreamProtocol.WEB_VIDEO,
            StreamProtocol.AUTO_DETECT -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .build()
                DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
            }
        }
    }

    private fun detectProtocolFromUrl(url: String, defaultIfUnknown: StreamProtocol): StreamProtocol {
        val path = runCatching { Uri.parse(url).path.orEmpty() }
            .getOrDefault(url)
            .lowercase(Locale.ROOT)
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> StreamProtocol.HLS_M3U8
            path.endsWith(".ts") -> StreamProtocol.MPEG_TS
            path.endsWith(".mpd") -> StreamProtocol.DASH_MPD
            path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") || path.endsWith(".mov") || path.endsWith(".m4v") ->
                StreamProtocol.PROGRESSIVE_VOD
            else -> defaultIfUnknown
        }
    }

    private fun fallbackKeyFor(source: YlowstoneStreamSource): String {
        return when (source) {
            is YlowstoneStreamSource.Xtream -> "xtream:${source.normalizedHost}:${source.streamId.trim()}"
            is YlowstoneStreamSource.DirectUrl -> "direct:${source.rawUrl.trim()}"
            is YlowstoneStreamSource.LocalMedia -> "local:${source.uriString.trim()}"
            is YlowstoneStreamSource.Vod -> "vod:${source.rawUrl.trim()}"
            is YlowstoneStreamSource.WebVideo -> "web:${source.pageUrl.trim()}"
        }
    }
}
