package com.ylowstone.tv.model

enum class DeviceMode { SMARTPHONE, TV }
enum class ThemeMode { AMOLED, LIGHT, SYSTEM }
enum class AppLanguage { ID, EN }

enum class StylePreset(
    val accentHex: Long,
    val secondaryHex: Long,
    val darkSurfaceHex: Long,
    val darkElevatedHex: Long,
    val label: String
) {
    APPLE_MUSIC(0xFFFA2D48, 0xFFFF6B81, 0xFF121216, 0xFF1C1C24, "Apple Music"),
    YOUTUBE(0xFFFF0033, 0xFFCC0022, 0xFF0F0F0F, 0xFF212121, "YouTube"),
    PRIME_VIDEO(0xFF00A8E1, 0xFF006EA8, 0xFF0F171E, 0xFF1A2634, "Prime Video"),
    NETFLIX(0xFFE50914, 0xFF99070E, 0xFF141414, 0xFF231F20, "Netflix"),
    HBO(0xFF9D4EDD, 0xFF5A189A, 0xFF100B1C, 0xFF1E1633, "HBO")
}

enum class PlaylistType { XTREAM, M3U, DIRECT }
enum class MediaType { LIVE, MOVIE, SERIES }
enum class StreamFormat(val ext: String, val label: String) {
    HLS("m3u8", "HLS (.m3u8)"),
    TS("ts", "MPEG-TS (.ts)")
}

enum class PlayerMode { NONE, FULLSCREEN, FLOATING }

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
    val stylePreset: StylePreset = StylePreset.APPLE_MUSIC,
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
