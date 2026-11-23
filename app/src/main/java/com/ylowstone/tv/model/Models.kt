package com.ylowstone.tv.model

import androidx.annotation.DrawableRes
import com.ylowstone.tv.R

enum class DeviceMode { SMARTPHONE, TV }
enum class ThemeMode { AMOLED, LIGHT, SYSTEM }
enum class AppLanguage { ID, EN }

// 5 Elegant brand-free style palettes preserving 100% identical signature colors
enum class StylePreset(
    val accentHex: Long,
    val secondaryHex: Long,
    val darkSurfaceHex: Long,
    val darkElevatedHex: Long,
    val label: String
) {
    ROSE_GLASS(0xFFFA2D48, 0xFFFF6B81, 0xFF121216, 0xFF1C1C24, "Rose Glass"),
    CRIMSON_DARK(0xFFFF0033, 0xFFCC0022, 0xFF0F0F0F, 0xFF212121, "Crimson Dark"),
    OCEAN_BLUE(0xFF00A8E1, 0xFF006EA8, 0xFF0F171E, 0xFF1A2634, "Ocean Blue"),
    RUBY_CINEMA(0xFFE50914, 0xFF99070E, 0xFF141414, 0xFF231F20, "Ruby Cinema"),
    ROYAL_VIOLET(0xFF9D4EDD, 0xFF5A189A, 0xFF100B1C, 0xFF1E1633, "Royal Violet");

    companion object {
        fun fromStored(raw: String?): StylePreset = when (raw) {
            "APPLE_MUSIC", "ROSE_GLASS" -> ROSE_GLASS
            "YOUTUBE", "CRIMSON_DARK" -> CRIMSON_DARK
            "PRIME_VIDEO", "OCEAN_BLUE" -> OCEAN_BLUE
            "NETFLIX", "RUBY_CINEMA" -> RUBY_CINEMA
            "HBO", "ROYAL_VIOLET" -> ROYAL_VIOLET
            else -> ROSE_GLASS
        }
    }
}

enum class AppIconOption(
    val key: String,
    val label: String,
    val aliasClassName: String,
    @DrawableRes val previewRes: Int
) {
    OBSIDIAN("obsidian", "Obsidian", "com.ylowstone.tv.IconObsidian", R.drawable.icon_prev_obsidian),
    CRIMSON("crimson", "Crimson", "com.ylowstone.tv.IconCrimson", R.drawable.icon_prev_crimson),
    IVORY_NOIR("ivory_noir", "Ivory Noir", "com.ylowstone.tv.IconIvoryNoir", R.drawable.icon_prev_ivory_noir),
    COBALT("cobalt", "Cobalt", "com.ylowstone.tv.IconCobalt", R.drawable.icon_prev_cobalt),
    PEARL_AZURE("pearl_azure", "Pearl Azure", "com.ylowstone.tv.IconPearlAzure", R.drawable.icon_prev_pearl_azure),
    MIDNIGHT("midnight", "Midnight", "com.ylowstone.tv.IconMidnight", R.drawable.icon_prev_midnight),
    SUNSET("sunset", "Sunset", "com.ylowstone.tv.IconSunset", R.drawable.icon_prev_sunset),
    SLATE_BLUE("slate_blue", "Slate Blue", "com.ylowstone.tv.IconSlateBlue", R.drawable.icon_prev_slate_blue),
    NEBULA("nebula", "Nebula", "com.ylowstone.tv.IconNebula", R.drawable.icon_prev_nebula),
    SOLAR_GOLD("solar_gold", "Solar Gold", "com.ylowstone.tv.IconSolarGold", R.drawable.icon_prev_solar_gold),
    PEARL_MINT("pearl_mint", "Pearl Mint", "com.ylowstone.tv.IconPearlMint", R.drawable.icon_prev_pearl_mint),
    EMERALD("emerald", "Emerald", "com.ylowstone.tv.IconEmerald", R.drawable.icon_prev_emerald);

    companion object {
        fun fromKey(k: String?): AppIconOption = entries.firstOrNull { it.key == k } ?: OBSIDIAN
    }
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

data class TaggedChannel(
    val channel: ChannelItem,
    val playlistName: String
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
    val stylePreset: StylePreset = StylePreset.ROSE_GLASS,
    val appIcon: AppIconOption = AppIconOption.OBSIDIAN,
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
