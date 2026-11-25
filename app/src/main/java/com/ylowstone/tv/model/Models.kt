package com.ylowstone.tv.model

import androidx.annotation.DrawableRes
import com.ylowstone.tv.R

enum class DeviceMode {
    SMARTPHONE,
    TV
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    AMOLED
}

enum class AccentPreset(val key: String, val label: String) {
    PIXEL_FOG("pixel_fog", "Pixel Fog"),
    OBSIDIAN_CRIMSON("obsidian_crimson", "Obsidian Crimson"),
    HYDROGEN_CYAN("hydrogen_cyan", "Hydrogen Cyan"),
    EMERALD_STUDIO("emerald_studio", "Emerald Studio");

    companion object {
        fun fromKey(key: String?): AccentPreset =
            entries.firstOrNull { it.key == key } ?: PIXEL_FOG
    }
}

enum class BufferProfile(val label: String) {
    LOW_LATENCY("Cepat (Live)"),
    BALANCED("Seimbang"),
    ANTI_BUFFER("Anti-Buffering")
}

enum class FavoriteViewMode {
    GRID,
    LIST
}

enum class PlaylistType {
    XTREAM,
    M3U,
    DIRECT
}

enum class StreamFormat(val ext: String, val label: String) {
    AUTO("m3u8", "Otomatis (Ikuti Playlist)"),
    HLS("m3u8", "HLS (.m3u8)"),
    TS("ts", "MPEG-TS (.ts)")
}

enum class MediaType {
    LIVE,
    MOVIE,
    SERIES
}

enum class PlayerMode {
    NONE,
    SPLIT,
    FLOATING,
    FULLSCREEN
}

enum class AppLanguage(val code: String) {
    ID("id"),
    EN("en")
}

enum class AppIconOption(
    val key: String,
    val label: String,
    val aliasClassName: String,
    @DrawableRes val previewRes: Int
) {
    OBSIDIAN("obsidian", "Obsidian", "com.ylowstone.tv.IconObsidian", R.drawable.icon_prev_obsidian),
    CYAN("cyan", "Cyan", "com.ylowstone.tv.IconCyan", R.drawable.icon_prev_pearl_azure),
    EMERALD("emerald", "Emerald", "com.ylowstone.tv.IconEmerald", R.drawable.icon_prev_emerald),
    AMBER("amber", "Amber", "com.ylowstone.tv.IconAmber", R.drawable.icon_prev_solar_gold),
    VIOLET("violet", "Violet", "com.ylowstone.tv.IconViolet", R.drawable.icon_prev_nebula),
    ROSE("rose", "Rose", "com.ylowstone.tv.IconRose", R.drawable.icon_prev_crimson),
    COBALT("cobalt", "Cobalt", "com.ylowstone.tv.IconCobalt", R.drawable.icon_prev_cobalt),
    CRIMSON("crimson", "Crimson", "com.ylowstone.tv.IconCrimson", R.drawable.icon_prev_crimson),
    TEAL("teal", "Teal", "com.ylowstone.tv.IconTeal", R.drawable.icon_prev_pearl_mint),
    GOLD("gold", "Gold", "com.ylowstone.tv.IconGold", R.drawable.icon_prev_solar_gold),
    ICE("ice", "Ice", "com.ylowstone.tv.IconIce", R.drawable.icon_prev_slate_blue),
    PLASMA("plasma", "Plasma", "com.ylowstone.tv.IconPlasma", R.drawable.icon_prev_midnight);

    companion object {
        fun fromKey(key: String?): AppIconOption =
            entries.firstOrNull { it.key == key } ?: OBSIDIAN
    }
}

data class AppSettings(
    val hasSeenWelcome: Boolean = false,
    val hasSelectedMode: Boolean = false,
    val deviceMode: DeviceMode = DeviceMode.SMARTPHONE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentPreset: AccentPreset = AccentPreset.PIXEL_FOG,
    val bufferProfile: BufferProfile = BufferProfile.BALANCED,
    val preferredSubtitleLang: String = "id",
    val autoSubtitleEnabled: Boolean = true,
    val appIcon: AppIconOption = AppIconOption.OBSIDIAN,
    val language: AppLanguage = AppLanguage.ID,
    val favoriteViewMode: FavoriteViewMode = FavoriteViewMode.GRID,
    val showPlayerGestures: Boolean = true,
    val streamFormat: StreamFormat = StreamFormat.AUTO,
    val pinCode: String = "1234",
    val use24HourClock: Boolean = true,
    val preferExternalPlayer: Boolean = false
)

data class PlaylistProfile(
    val id: Long = 0L,
    val name: String,
    val type: PlaylistType,
    val url: String,
    val username: String = "",
    val password: String = "",
    val userAgent: String = "",
    val liveCategories: List<String> = emptyList(),
    val movieCategories: List<String> = emptyList(),
    val seriesCategories: List<String> = emptyList(),
    val colorIndex: Int = 0,
    val lastUpdated: Long = 0L
) {
    fun buildShareableLink(format: StreamFormat): String {
        val cleanBase = url.trim().trimEnd('/')
        return when (type) {
            PlaylistType.XTREAM -> {
                val u = username.trim()
                val p = password.trim()
                val ext = if (format == StreamFormat.AUTO) "m3u8" else format.ext
                "$cleanBase/get.php?username=$u&password=$p&type=m3u_plus&output=$ext"
            }
            PlaylistType.M3U, PlaylistType.DIRECT -> cleanBase
        }
    }

    fun buildMaskedLink(format: StreamFormat): String {
        val cleanBase = url.trim().trimEnd('/')
        return when (type) {
            PlaylistType.XTREAM -> {
                val ext = if (format == StreamFormat.AUTO) "m3u8" else format.ext
                "$cleanBase/get.php?username=***&password=***&type=m3u_plus&output=$ext"
            }
            PlaylistType.M3U, PlaylistType.DIRECT -> cleanBase
        }
    }

    fun matchesLiveCategoryFilter(categoryName: String): Boolean {
        if (isAdultCategoryOrTitle(categoryName)) return false
        if (liveCategories.isEmpty()) return true
        return liveCategories.any { it.equals(categoryName.trim(), ignoreCase = true) }
    }

    fun matchesCategoryFilter(categoryName: String, mediaType: MediaType): Boolean {
        if (isAdultCategoryOrTitle(categoryName)) return false
        val allowList = when (mediaType) {
            MediaType.LIVE -> liveCategories
            MediaType.MOVIE -> movieCategories
            MediaType.SERIES -> seriesCategories
        }
        if (allowList.isEmpty()) return true
        return allowList.any { it.equals(categoryName.trim(), ignoreCase = true) }
    }
}

data class ChannelItem(
    val id: Long = 0L,
    val playlistId: Long = 0L,
    val streamId: String = "",
    val name: String,
    val category: String,
    val logoUrl: String = "",
    val streamUrl: String,
    val mediaType: MediaType = MediaType.LIVE,
    val containerExtension: String = "",
    val rating: String = "",
    val isFavorite: Boolean = false,
    val lastWatched: Long = 0L,
    val lastWatchedAt: Long = 0L
) {
    fun isAdultContent(): Boolean = isAdultCategoryOrTitle(category, name)
}

data class TaggedChannel(
    val channel: ChannelItem,
    val playlistName: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

data class CategoryInfo(
    val name: String,
    val count: Int
)

typealias CategoryCount = CategoryInfo

data class EpisodeItem(
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val streamUrl: String,
    val plot: String = ""
)

data class UpdateInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val htmlUrl: String,
    val hasUpdate: Boolean
)

data class AdminAuthResult(
    val username: String,
    val role: String,
    val playlists: List<PlaylistProfile>
)

private val ADULT_KEYWORD_REGEX = Regex(
    pattern = """\b(xxx|porn|nsfw|adult|erotic|sex|hentai|cam4|chaturbate|stripchat|onlyfans|brazzers|xvideos|xnxx|pornhub|redtube|youporn|18\+|21\+|plus\s*18|dewasa|bokep)\b""",
    options = setOf(RegexOption.IGNORE_CASE)
)

fun isAdultCategoryOrTitle(vararg texts: String?): Boolean {
    for (t in texts) {
        if (t.isNullOrBlank()) continue
        if (ADULT_KEYWORD_REGEX.containsMatchIn(t)) return true
    }
    return false
}
