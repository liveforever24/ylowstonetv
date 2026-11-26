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
 * Resolver universal v1.3 untuk semua tipe aliran YlowstoneTV:
 * Xtream Codes, URL M3U/M3U8, file lokal, .ts, .mp4/VOD, .mpd, link tanpa ekstensi,
 * serta tautan video web (melalui WebVideoStreamExtractor).
 *
 * Upgrade v1.3:
 * - Mendukung fallback Live TV 2 arah (.m3u8 <-> .ts) untuk Xtream & DirectUrl M3U.
 * - Menyimpan format yang terbukti berhasil per channel di cache sesi agar klik berikutnya
 *   langsung memakai format yang cocok.
 */
class XtreamStreamResolver(
    private val webVideoExtractor: WebVideoStreamExtractor = PassthroughWebVideoExtractor()
) {
    // Format yang sudah terbukti berhasil diputar (STATE_READY) per channel pada sesi ini
    private val confirmedSessionProtocols = ConcurrentHashMap<String, StreamProtocol>()

    // Override sementara saat mencoba swap 2 arah sebelum playback terkonfirmasi siap
    private val pendingSwapProtocols = ConcurrentHashMap<String, StreamProtocol>()

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

        val key = fallbackKeyFor(source)
        val effectiveProtocol = pendingSwapProtocols[key]
            ?: confirmedSessionProtocols[key]
            ?: StreamProtocol.HLS_M3U8

        return if (effectiveProtocol == StreamProtocol.MPEG_TS) {
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
        val key = fallbackKeyFor(source)
        val preferred = if (source.isLive) {
            pendingSwapProtocols[key] ?: confirmedSessionProtocols[key]
        } else {
            null
        }

        if (preferred == StreamProtocol.MPEG_TS || preferred == StreamProtocol.HLS_M3U8) {
            val rewritten = rewriteLiveExtension(raw, preferred)
            if (rewritten != null) {
                return ResolvedStream(
                    canonicalUrl = rewritten,
                    protocol = preferred,
                    isLive = source.isLive,
                    headers = source.customHeaders
                )
            }
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

    /**
     * Memicu perpindahan format 2 arah (.m3u8 <-> .ts) untuk satu percobaan fallback.
     * Mengembalikan protokol tujuan jika sumber mendukung swap, atau null jika tidak bisa di-swap.
     */
    fun swapLiveProtocol(
        source: YlowstoneStreamSource,
        failedProtocol: StreamProtocol
    ): StreamProtocol? {
        if (!source.isLive) return null
        val targetProtocol = when (failedProtocol) {
            StreamProtocol.HLS_M3U8 -> StreamProtocol.MPEG_TS
            StreamProtocol.MPEG_TS -> StreamProtocol.HLS_M3U8
            else -> return null
        }

        val supported = when (source) {
            is YlowstoneStreamSource.Xtream -> true
            is YlowstoneStreamSource.DirectUrl -> rewriteLiveExtension(source.rawUrl, targetProtocol) != null
            else -> false
        }
        if (!supported) return null

        val key = fallbackKeyFor(source)
        pendingSwapProtocols[key] = targetProtocol
        return targetProtocol
    }

    /**
     * Menyimpan format yang terbukti sukses diputar ke cache sesi per channel.
     */
    fun confirmWorkingProtocol(source: YlowstoneStreamSource, protocol: StreamProtocol) {
        if (!source.isLive) return
        if (protocol != StreamProtocol.HLS_M3U8 && protocol != StreamProtocol.MPEG_TS) return

        val cacheable = when (source) {
            is YlowstoneStreamSource.Xtream -> true
            is YlowstoneStreamSource.DirectUrl -> rewriteLiveExtension(source.rawUrl, protocol) != null
            else -> false
        }
        if (!cacheable) return

        val key = fallbackKeyFor(source)
        confirmedSessionProtocols[key] = protocol
        pendingSwapProtocols.remove(key)
    }

    fun clearPendingSwap(source: YlowstoneStreamSource) {
        pendingSwapProtocols.remove(fallbackKeyFor(source))
    }

    /**
     * Kompatibilitas API v1.2: menandai HLS tidak didukung dan mengarahkan ke MPEG-TS.
     */
    fun markHlsUnsupported(source: YlowstoneStreamSource): Boolean {
        val key = fallbackKeyFor(source)
        pendingSwapProtocols.remove(key)
        val previous = confirmedSessionProtocols.put(key, StreamProtocol.MPEG_TS)
        return previous != StreamProtocol.MPEG_TS
    }

    fun clearSessionFallbacks() {
        confirmedSessionProtocols.clear()
        pendingSwapProtocols.clear()
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

    private fun rewriteLiveExtension(rawUrl: String, targetProtocol: StreamProtocol): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val suffixStart = trimmed.indexOfAny(charArrayOf('?', '#'))
        val basePart = if (suffixStart >= 0) trimmed.substring(0, suffixStart) else trimmed
        val suffixPart = if (suffixStart >= 0) trimmed.substring(suffixStart) else ""
        val lowerBase = basePart.lowercase(Locale.ROOT)

        return when (targetProtocol) {
            StreamProtocol.MPEG_TS -> when {
                lowerBase.endsWith(".m3u8") -> basePart.dropLast(5) + ".ts" + suffixPart
                lowerBase.endsWith(".ts") -> trimmed
                else -> null
            }
            StreamProtocol.HLS_M3U8 -> when {
                lowerBase.endsWith(".ts") -> basePart.dropLast(3) + ".m3u8" + suffixPart
                lowerBase.endsWith(".m3u8") -> trimmed
                else -> null
            }
            else -> null
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
