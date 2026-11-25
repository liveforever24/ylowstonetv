package com.ylowstone.tv.model

import androidx.annotation.DrawableRes
import com.ylowstone.tv.R

enum class DeviceMode { SMARTPHONE, TV }
enum class ThemeMode { SYSTEM, LIGHT, AMOLED }
enum class AppLanguage { ID, EN }
enum class FavoriteViewMode { GRID, LIST }

enum class AccentPreset(val key: String, val label: String) {
    AMBER_SUNSTONE("amber_sunstone", "Ylowstone Signature"),
    AMOLED_NOIR("amoled_noir", "AMOLED Noir"),
    VELVET_CRIMSON("velvet_crimson", "Velvet Crimson"),
    SLATE_MIST("slate_mist", "Slate Mist"),
    HYDROGEN_ICE("hydrogen_ice", "Hydrogen Ice"),
    EMERALD_STUDIO("emerald_studio", "Emerald Studio");

    companion object {
        fun fromKey(k: String?): AccentPreset = when (k) {
            "pixel_fog" -> SLATE_MIST
            "obsidian_crimson" -> VELVET_CRIMSON
            else -> entries.firstOrNull { it.key == k } ?: AMBER_SUNSTONE
        }
    }
}

enum class LoadingAnimStyle(val key: String) {
    CHROMA_BOOMERANG("chroma_boomerang"),
    YLLOWSTONE_EMBER("ylowstone_ember"),
    AURORA_EMERALD("aurora_emerald");

    companion object {
        fun fromKey(k: String?): LoadingAnimStyle = when (k) {
            "moto_boomerang" -> CHROMA_BOOMERANG
            else -> entries.firstOrNull { it.key == k } ?: CHROMA_BOOMERANG
        }
    }
}

enum class SkipIntroMode { OFF, MANUAL, AUTO }

enum class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackMs: Int,
    val rebufferMs: Int
) {
    LOW_LATENCY(10_000, 25_000, 1_000, 2_000),
    BALANCED(20_000, 50_000, 2_000, 4_000),
    ANTI_BUFFER(35_000, 90_000, 3_500, 6_500)
}

enum class AutoSubtitleLang { ID, EN, OFF }

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
    AUTO("m3u8", "Auto"),
    HLS("m3u8", "HLS (.m3u8)"),
    TS("ts", "MPEG-TS (.ts)")
}

enum class PlayerMode { NONE, SPLIT, FULLSCREEN, FLOATING }

enum class StreamHealthState { ACTIVE, DELAY, DEAD }

data class StreamHealthResult(
    val state: StreamHealthState,
    val latencyMs: Long,
    val httpCode: Int = 0,
    val detail: String = ""
)

val DEFAULT_WATCH_GROUPS: List<String> = listOf(
    "Sports",
    "Event Pertandingan",
    "Daftar Tontonan"
)

private val ADULT_CONTENT_REGEX = Regex(
    pattern = """(?i)(\b(adults?|porn\w*|xxx|xxl|nsfw|erotic\w*|sex|sexy|jav|bokep|hentai|playboy|dorcel|redlight|brazzers|xvideos|xhamster|penthouse|softcore|hardcore|onlyfans|camgirls?|naughty|milf|fetish|dewasa|stripclub|vivid|bangbros|hustler|private\s+tv|blue\s+hustler|vod\s+porn|for\s+adults)\b|(?:^|[\s\[\(\-_\|/])(?:18\+|\+18)(?:$|[\s\]\)\-_\|/]))"""
)

fun isAdultCategoryOrTitle(category: String, name: String = ""): Boolean {
    return ADULT_CONTENT_REGEX.containsMatchIn("$category $name")
}

data class PlaylistProfile(
    val id: Long = 0L,
    val name: String,
    val type: PlaylistType,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val userAgent: String = "",
    val liveCategories: List<String> = emptyList(),
    val movieCategories: List<String> = emptyList(),
    val seriesCategories: List<String> = emptyList(),
    val colorIndex: Int = 0,
    val lastUpdated: Long = 0L
) {
    fun matchesCategoryFilter(categoryName: String, mediaType: MediaType): Boolean {
        val rules = when (mediaType) {
            MediaType.LIVE -> liveCategories
            MediaType.MOVIE -> movieCategories
            MediaType.SERIES -> seriesCategories
        }
        if (rules.isEmpty()) return true
        val cat = categoryName.trim().lowercase()
        if (cat.isEmpty()) return false
        for (rule in rules) {
            val r = rule.trim().lowercase()
            if (r.isEmpty()) continue
            if (cat == r) return true
            if (cat.length > r.length && cat.startsWith(r)) {
                val afterChar = cat[r.length]
                if (!afterChar.isLetterOrDigit()) return true
            }
            var idx = cat.indexOf(r)
            while (idx >= 0) {
                val leftOk = idx == 0 || !cat[idx - 1].isLetter()
                val rightIdx = idx + r.length
                val rightOk = rightIdx >= cat.length || !cat[rightIdx].isLetter()
                if (leftOk && rightOk && idx == 0) return true
                if (leftOk && rightOk && (r == "vip" || r.length > 2)) return true
                idx = cat.indexOf(r, idx + 1)
            }
        }
        return false
    }

    fun matchesLiveCategoryFilter(categoryName: String): Boolean =
        matchesCategoryFilter(categoryName, MediaType.LIVE)

    fun buildShareableLink(format: StreamFormat = StreamFormat.AUTO): String {
        return when (type) {
            PlaylistType.XTREAM -> {
                val base = url.trim().trimEnd('/')
                val out = if (format == StreamFormat.TS) "ts" else "m3u8"
                "$base/get.php?username=$username&password=$password&type=m3u_plus&output=$out"
            }
            PlaylistType.M3U -> url.trim()
            PlaylistType.DIRECT -> url.trim()
        }
    }

    fun buildMaskedLink(format: StreamFormat = StreamFormat.AUTO): String {
        return when (type) {
            PlaylistType.XTREAM -> {
                val base = url.trim().trimEnd('/')
                val out = if (format == StreamFormat.TS) "ts" else "m3u8"
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
) {
    fun isAdultContent(): Boolean = isAdultCategoryOrTitle(category, name)
    fun isProviderSeparator(): Boolean {
        val t = name.trim()
        return t.startsWith("###") || t.endsWith("###") || t.startsWith("===") || t.endsWith("===")
    }
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

data class EpisodeItem(
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val streamUrl: String,
    val plot: String = ""
)

data class AppSettings(
    val hasSeenWelcome: Boolean = false,
    val hasSelectedMode: Boolean = false,
    val deviceMode: DeviceMode = DeviceMode.SMARTPHONE,
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    val accentPreset: AccentPreset = AccentPreset.AMBER_SUNSTONE,
    val loadingAnimStyle: LoadingAnimStyle = LoadingAnimStyle.CHROMA_BOOMERANG,
    val appIcon: AppIconOption = AppIconOption.OBSIDIAN,
    val language: AppLanguage = AppLanguage.ID,
    val favoriteViewMode: FavoriteViewMode = FavoriteViewMode.GRID,
    val showPlayerGestures: Boolean = true,
    val streamFormat: StreamFormat = StreamFormat.AUTO,
    val bufferProfile: BufferProfile = BufferProfile.BALANCED,
    val autoReconnectLive: Boolean = true,
    val autoSubtitleLang: AutoSubtitleLang = AutoSubtitleLang.ID,
    val skipIntroMode: SkipIntroMode = SkipIntroMode.MANUAL,
    val skipIntroSeconds: Int = 85,
    val autoNextEpisode: Boolean = true,
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

data class AdminAuthResult(
    val username: String,
    val role: String,
    val playlists: List<PlaylistProfile>
)
