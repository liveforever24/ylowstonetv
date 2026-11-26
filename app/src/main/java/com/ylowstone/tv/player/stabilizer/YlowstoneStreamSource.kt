package com.ylowstone.tv.player.stabilizer

import android.net.Uri
import java.util.Locale

enum class StreamProtocol(val shortLabel: String) {
    HLS_M3U8("HLS"),
    MPEG_TS("TS"),
    DASH_MPD("DASH"),
    PROGRESSIVE_VOD("VOD"),
    WEB_VIDEO("WEB"),
    AUTO_DETECT("AUTO")
}

/**
 * Representasi sumber aliran universal YlowstoneTV v1.2.
 * Mencakup akun Xtream Codes, URL M3U/M3U8, file lokal, VOD, dan tautan video web.
 */
sealed class YlowstoneStreamSource {
    abstract val isLive: Boolean
    abstract val customHeaders: Map<String, String>

    data class Xtream(
        val hostUrl: String,
        val username: String,
        val password: String,
        val streamId: String,
        override val isLive: Boolean,
        val vodCategory: String = "movie",
        val vodExtension: String = "mp4",
        override val customHeaders: Map<String, String> = emptyMap()
    ) : YlowstoneStreamSource() {
        val normalizedHost: String
            get() = hostUrl.trim().trimEnd('/')
    }

    data class DirectUrl(
        val rawUrl: String,
        override val isLive: Boolean = true,
        override val customHeaders: Map<String, String> = emptyMap()
    ) : YlowstoneStreamSource()

    data class LocalMedia(
        val uriString: String,
        override val isLive: Boolean = false,
        override val customHeaders: Map<String, String> = emptyMap()
    ) : YlowstoneStreamSource()

    data class Vod(
        val rawUrl: String,
        val title: String? = null,
        override val customHeaders: Map<String, String> = emptyMap()
    ) : YlowstoneStreamSource() {
        override val isLive: Boolean = false
    }

    data class WebVideo(
        val pageUrl: String,
        override val isLive: Boolean = false,
        override val customHeaders: Map<String, String> = emptyMap()
    ) : YlowstoneStreamSource()

    companion object {
        private val VOD_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "m4v", "avi")

        private val WEB_VIDEO_PATH_REGEX = Regex(
            pattern = """/(watch|shorts|embed|live)/""",
            option = RegexOption.IGNORE_CASE
        )

        /**
         * Mengklasifikasikan tautan mentah (dari M3U remote, file .m3u/.m3u8 lokal,
         * aliran tunggal, VOD, atau video web) ke tipe sumber yang tepat.
         */
        fun fromRawUrl(
            rawUrl: String,
            isLiveHint: Boolean? = null,
            customHeaders: Map<String, String> = emptyMap()
        ): YlowstoneStreamSource {
            val trimmed = rawUrl.trim()
            val lower = trimmed.lowercase(Locale.ROOT)

            if (lower.startsWith("content://") || lower.startsWith("file://") || trimmed.startsWith("/")) {
                return LocalMedia(
                    uriString = trimmed,
                    isLive = isLiveHint ?: false,
                    customHeaders = customHeaders
                )
            }

            val parsed = runCatching { Uri.parse(trimmed) }.getOrNull()
            val host = parsed?.host.orEmpty().lowercase(Locale.ROOT)
            val path = parsed?.path.orEmpty().lowercase(Locale.ROOT)

            if (isWebVideoUrl(host, path)) {
                return WebVideo(
                    pageUrl = trimmed,
                    isLive = isLiveHint ?: path.contains("/live/"),
                    customHeaders = customHeaders
                )
            }

            val ext = path.substringAfterLast('.', missingDelimiterValue = "")
            if (ext in VOD_EXTENSIONS) {
                return Vod(
                    rawUrl = trimmed,
                    customHeaders = customHeaders
                )
            }

            val inferredLive = isLiveHint ?: (ext == "ts" || ext == "m3u8" || ext.isEmpty())
            return DirectUrl(
                rawUrl = trimmed,
                isLive = inferredLive,
                customHeaders = customHeaders
            )
        }

        private fun isWebVideoUrl(host: String, path: String): Boolean {
            if (host.isEmpty()) return false
            if (host.endsWith(".com") && WEB_VIDEO_PATH_REGEX.containsMatchIn(path) && !path.endsWith(".m3u8") && !path.endsWith(".mp4")) {
                return true
            }
            return false
        }
    }
}

/**
 * Alias kompatibilitas dengan v1.1 agar pemanggil lama tidak perlu langsung diubah.
 */
typealias XtreamCanonicalSource = YlowstoneStreamSource.Xtream
