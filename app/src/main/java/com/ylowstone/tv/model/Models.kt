package com.ylowstone.tv.model

enum class DeviceMode { SMARTPHONE, TV }
enum class ThemeMode { AMOLED, LIGHT, SYSTEM }
enum class AppLanguage { ID, EN }
enum class AccentTheme(val hex: Long, val label: String) {
    APPLE_ROSE(0xFFFA2D48, "Apple Rose"),
    COBALT_BLUE(0xFF0A84FF, "Cobalt Blue"),
    EMERALD(0xFF30D158, "Emerald"),
    AMBER(0xFFFF9F0A, "Warm Amber"),
    PURPLE(0xFFBF5AF2, "Ultra Violet")
}

enum class PlaylistType { XTREAM, M3U, DIRECT }
enum class MediaType { LIVE, MOVIE, SERIES }
enum class StreamFormat(val ext: String, val label: String) {
    HLS("m3u8", "HLS (.m3u8)"),
    TS("ts", "MPEG-TS (.ts)")
}

data class PlaylistProfile(
    val id: Long = 0L,
    val name: String,
    val type: PlaylistType,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val userAgent: String = "",
    val colorIndex: Int = 0,
    val lastUpdated: Long = 0L
) {
    fun buildShareableLink(format: StreamFormat = StreamFormat.HLS): String {
        return when (type) {
            PlaylistType.XTREAM -> {
                val base = url.trim().trimEnd('/')
                val out = if (format == StreamFormat.HLS) "m3u8" else "ts"
                "$base/get.php?username=$username&password=$password&type=m3u_plus&output=$out"
            }
            PlaylistType.M3U -> url.trim()
            PlaylistType.DIRECT -> url.trim()
        }
    }

    fun buildMaskedLink(format: StreamFormat = StreamFormat.HLS): String {
        return when (type) {
            PlaylistType.XTREAM -> {
                val base = url.trim().trimEnd('/')
                val out = if (format == StreamFormat.HLS) "m3u8" else "ts"
                val maskedPass = if (password.isEmpty()) "" else "•".repeat(password.length.coerceAtMost(10))
                "$base/get.php?username=$username&password=$maskedPass&type=m3u_plus&output=$out"
            }
            else -> url.trim()
        }
    }
}

data class ChannelItem(
    val id: Long = 0L,
    val playlistId: Long,
    val streamId: String = "",
    val name: String,
    val category: String,
    val logoUrl: String = "",
    val streamUrl: String,
    val mediaType: MediaType = MediaType.LIVE,
    val containerExtension: String = "",
    val rating: String = "",
    val isFavorite: Boolean = false,
    val lastWatchedAt: Long = 0L
)

data class CategoryInfo(
    val name: String,
    val count: Int
)

data class EpisodeItem(
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val streamUrl: String,
    val plot: String = ""
)

data class AppSettings(
    val hasSelectedMode: Boolean = false,
    val deviceMode: DeviceMode = DeviceMode.SMARTPHONE,
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    val accent: AccentTheme = AccentTheme.APPLE_ROSE,
    val language: AppLanguage = AppLanguage.ID,
    val showPlayerGestures: Boolean = true,
    val streamFormat: StreamFormat = StreamFormat.HLS,
    val pinCode: String = "1234",
    val use24HourClock: Boolean = true,
    val preferExternalPlayer: Boolean = false
)

data class UpdateInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val htmlUrl: String,
    val hasUpdate: Boolean
)
