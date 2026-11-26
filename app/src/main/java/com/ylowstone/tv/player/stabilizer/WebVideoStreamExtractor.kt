package com.ylowstone.tv.player.stabilizer

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

data class ExtractedWebStream(
    val mediaUrl: String,
    val protocol: StreamProtocol,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Kontrak ekstraktor untuk mengubah tautan halaman video web menjadi URL media
 * yang dapat diputar langsung oleh Media3 (HLS .m3u8, DASH .mpd, atau MP4).
 */
fun interface WebVideoStreamExtractor {
    suspend fun extract(pageUrl: String): ExtractedWebStream?
}

/**
 * Implementasi default apabila backend ekstraktor belum dikonfigurasi:
 * meneruskan langsung URL yang sudah berupa media stream valid.
 */
class PassthroughWebVideoExtractor : WebVideoStreamExtractor {
    override suspend fun extract(pageUrl: String): ExtractedWebStream? {
        val lower = pageUrl.lowercase(Locale.ROOT)
        return when {
            lower.contains(".m3u8") -> ExtractedWebStream(pageUrl, StreamProtocol.HLS_M3U8)
            lower.contains(".mpd") -> ExtractedWebStream(pageUrl, StreamProtocol.DASH_MPD)
            lower.contains(".mp4") || lower.contains(".webm") -> ExtractedWebStream(pageUrl, StreamProtocol.PROGRESSIVE_VOD)
            else -> null
        }
    }
}

/**
 * Ekstraktor berbasis endpoint API internal YlowstoneTV (misalnya di folder api/).
 * Format respons JSON yang diharapkan:
 * { "stream_url": "https://...", "protocol": "hls|dash|mp4", "headers": { "Referer": "..." } }
 */
class RemoteApiWebVideoExtractor(
    private val resolverEndpointUrl: String,
    private val connectionGuard: StrictConnectionGuard
) : WebVideoStreamExtractor {

    override suspend fun extract(pageUrl: String): ExtractedWebStream? {
        return withContext(Dispatchers.IO) {
            val encoded = Uri.encode(pageUrl.trim())
            val sep = if (resolverEndpointUrl.contains("?")) "&" else "?"
            val fullUrl = "${resolverEndpointUrl.trim()}${sep}url=$encoded"

            runCatching {
                val request = Request.Builder().url(fullUrl).get().build()
                connectionGuard.apiHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    parseResponse(body)
                }
            }.getOrNull()
        }
    }

    private fun parseResponse(rawJson: String): ExtractedWebStream? {
        val root = JSONObject(rawJson)
        val streamUrl = root.optString("stream_url", "").trim()
        if (streamUrl.isEmpty()) return null

        val protoRaw = root.optString("protocol", "").lowercase(Locale.ROOT)
        val protocol = when (protoRaw) {
            "hls", "m3u8" -> StreamProtocol.HLS_M3U8
            "dash", "mpd" -> StreamProtocol.DASH_MPD
            "ts" -> StreamProtocol.MPEG_TS
            "mp4", "vod", "progressive" -> StreamProtocol.PROGRESSIVE_VOD
            else -> StreamProtocol.AUTO_DETECT
        }

        val headers = mutableMapOf<String, String>()
        root.optJSONObject("headers")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key, "")
                if (value.isNotBlank()) headers[key] = value
            }
        }

        return ExtractedWebStream(
            mediaUrl = streamUrl,
            protocol = protocol,
            headers = headers
        )
    }
}
