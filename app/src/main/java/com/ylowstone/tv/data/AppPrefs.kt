package com.ylowstone.tv.data

import android.content.Context
import com.ylowstone.tv.model.*
import java.util.Locale

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ylowstone_tv_prefs", Context.MODE_PRIVATE)

    private fun detectSystemLanguage(): AppLanguage {
        val code = Locale.getDefault().language.lowercase(Locale.US)
        return if (code == "id" || code == "in") AppLanguage.ID else AppLanguage.EN
    }

    fun loadSettings(): AppSettings {
        val storedLang = prefs.getString("language", null)
        val resolvedLang = if (storedLang != null) {
            runCatching { AppLanguage.valueOf(storedLang) }.getOrDefault(detectSystemLanguage())
        } else {
            detectSystemLanguage()
        }

        return AppSettings(
            hasSeenWelcome = prefs.getBoolean("has_seen_welcome", false),
            hasSelectedMode = prefs.getBoolean("has_selected_mode", false),
            deviceMode = runCatching { DeviceMode.valueOf(prefs.getString("device_mode", "SMARTPHONE")!!) }.getOrDefault(DeviceMode.SMARTPHONE),
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", "AMOLED")!!) }.getOrDefault(ThemeMode.AMOLED),
            accentPreset = AccentPreset.fromKey(prefs.getString("accent_preset", "amber_sunstone")),
            loadingAnimStyle = LoadingAnimStyle.fromKey(prefs.getString("loading_anim_style", "chroma_boomerang")),
            appIcon = AppIconOption.fromKey(prefs.getString("app_icon", "obsidian")),
            language = resolvedLang,
            favoriteViewMode = runCatching { FavoriteViewMode.valueOf(prefs.getString("favorite_view_mode", "GRID")!!) }.getOrDefault(FavoriteViewMode.GRID),
            showPlayerGestures = prefs.getBoolean("show_gestures", true),
            streamFormat = runCatching { StreamFormat.valueOf(prefs.getString("stream_format", "AUTO")!!) }.getOrDefault(StreamFormat.AUTO),
            bufferProfile = runCatching { BufferProfile.valueOf(prefs.getString("buffer_profile", "BALANCED")!!) }.getOrDefault(BufferProfile.BALANCED),
            autoReconnectLive = prefs.getBoolean("auto_reconnect_live", true),
            autoSubtitleLang = runCatching { AutoSubtitleLang.valueOf(prefs.getString("auto_subtitle_lang", "ID")!!) }.getOrDefault(AutoSubtitleLang.ID),
            skipIntroMode = runCatching { SkipIntroMode.valueOf(prefs.getString("skip_intro_mode", "MANUAL")!!) }.getOrDefault(SkipIntroMode.MANUAL),
            skipIntroSeconds = prefs.getInt("skip_intro_seconds", 85).coerceIn(5, 600),
            autoNextEpisode = prefs.getBoolean("auto_next_episode", true),
            pinCode = prefs.getString("pin_code", "1234") ?: "1234",
            use24HourClock = prefs.getBoolean("use_24h", true),
            preferExternalPlayer = prefs.getBoolean("prefer_external", false),
            enableDoh = prefs.getBoolean("enable_doh", true)
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putBoolean("has_seen_welcome", s.hasSeenWelcome)
            .putBoolean("has_selected_mode", s.hasSelectedMode)
            .putString("device_mode", s.deviceMode.name)
            .putString("theme_mode", s.themeMode.name)
            .putString("accent_preset", s.accentPreset.key)
            .putString("loading_anim_style", s.loadingAnimStyle.key)
            .putString("app_icon", s.appIcon.key)
            .putString("language", s.language.name)
            .putString("favorite_view_mode", s.favoriteViewMode.name)
            .putBoolean("show_gestures", s.showPlayerGestures)
            .putString("stream_format", s.streamFormat.name)
            .putString("buffer_profile", s.bufferProfile.name)
            .putBoolean("auto_reconnect_live", s.autoReconnectLive)
            .putString("auto_subtitle_lang", s.autoSubtitleLang.name)
            .putString("skip_intro_mode", s.skipIntroMode.name)
            .putInt("skip_intro_seconds", s.skipIntroSeconds)
            .putBoolean("auto_next_episode", s.autoNextEpisode)
            .putString("pin_code", s.pinCode)
            .putBoolean("use_24h", s.use24HourClock)
            .putBoolean("prefer_external", s.preferExternalPlayer)
            .putBoolean("enable_doh", s.enableDoh)
            .apply()
    }

    // ---- Anti-Bruteforce Login Lockout (5 salah -> kunci 5 menit, v2.5.0) ----
    fun getLoginFailedAttempts(): Int = prefs.getInt("login_failed_attempts", 0)

    fun getLoginLockoutUntilMs(): Long = prefs.getLong("login_lockout_until_ms", 0L)

    fun recordLoginFailure(): Long {
        val nextAttempts = getLoginFailedAttempts() + 1
        val editor = prefs.edit().putInt("login_failed_attempts", nextAttempts)
        val lockoutUntil = if (nextAttempts >= MAX_LOGIN_ATTEMPTS) {
            System.currentTimeMillis() + LOCKOUT_DURATION_MS
        } else {
            0L
        }
        if (lockoutUntil > 0L) {
            editor.putLong("login_lockout_until_ms", lockoutUntil)
            editor.putInt("login_failed_attempts", 0)
        }
        editor.apply()
        return lockoutUntil
    }

    fun clearLoginFailures() {
        prefs.edit()
            .putInt("login_failed_attempts", 0)
            .putLong("login_lockout_until_ms", 0L)
            .apply()
    }


    /**
     * v2.5.3 Fix: Bersihkan key SharedPreferences lama yang tidak terpakai
     * (ui_sounds_enabled & intro_sec_* agar Skip Intro custom tidak pernah terkunci data lama).
     */
    fun cleanLegacyPrefs() {
        val editor = prefs.edit()
        var changed = false
        for (key in prefs.all.keys) {
            if (key == "ui_sounds_enabled" || key.startsWith("intro_sec_")) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    // ---- Per-Series Subtitle Offset ----
    private fun normalizeSeriesKey(raw: String): String {
        return raw.lowercase(Locale.US).replace(Regex("""[^a-z0-9]+"""), "_").trim('_').take(48)
    }

    fun getSeriesSubtitleOffsetMs(seriesKey: String): Long {
        val k = normalizeSeriesKey(seriesKey)
        if (k.isEmpty()) return 0L
        return prefs.getLong("sub_offset_$k", 0L)
    }

    fun saveSeriesSubtitleOffsetMs(seriesKey: String, offsetMs: Long) {
        val k = normalizeSeriesKey(seriesKey)
        if (k.isEmpty()) return
        prefs.edit().putLong("sub_offset_$k", offsetMs).apply()
    }

    fun getSeriesIntroSeconds(seriesKey: String, fallback: Int): Int {
        val k = normalizeSeriesKey(seriesKey)
        if (k.isEmpty()) return fallback
        val stored = prefs.getInt("intro_sec_$k", -1)
        return if (stored in 5..600) stored else fallback
    }

    fun saveSeriesIntroSeconds(seriesKey: String, seconds: Int) {
        val k = normalizeSeriesKey(seriesKey)
        if (k.isEmpty()) return
        prefs.edit().putInt("intro_sec_$k", seconds.coerceIn(5, 600)).apply()
    }

    // ---- Last Active Playlist Memory ----
    fun getLastActivePlaylistId(): Long = prefs.getLong("last_active_playlist_id", -1L)

    fun setLastActivePlaylistId(playlistId: Long) {
        prefs.edit().putLong("last_active_playlist_id", playlistId).apply()
    }

    // ---- Dual Auth Session (User Biasa PIN vs Full Admin Mode) ----
    fun isAdminLoggedIn(): Boolean = prefs.getBoolean("admin_logged_in", false)

    fun getAdminPin(): String = prefs.getString("admin_pin_token", "") ?: ""

    fun setAdminSession(loggedIn: Boolean, pin: String = "") {
        prefs.edit()
            .putBoolean("admin_logged_in", loggedIn)
            .putString("admin_pin_token", if (loggedIn) pin else "")
            .apply()
    }

    fun isFullAdminMode(): Boolean = prefs.getBoolean("full_admin_mode", false)

    fun setFullAdminMode(enabled: Boolean) {
        prefs.edit().putBoolean("full_admin_mode", enabled).apply()
    }

    fun hasPassedLoginGate(): Boolean = prefs.getBoolean("passed_login_gate", false)

    fun setPassedLoginGate(passed: Boolean) {
        prefs.edit().putBoolean("passed_login_gate", passed).apply()
    }

    fun getAdminPlaylistIds(): Set<Long> {
        return prefs.getStringSet("admin_playlist_ids", emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    fun setAdminPlaylistIds(ids: Set<Long>) {
        prefs.edit()
            .putStringSet("admin_playlist_ids", ids.map { it.toString() }.toSet())
            .apply()
    }

    // ---- Category PIN Locks ----
    fun getLockedCategories(playlistId: Long): Set<String> {
        return prefs.getStringSet("locked_cats_$playlistId", emptySet())?.toSet() ?: emptySet()
    }

    fun setLockedCategories(playlistId: Long, categories: Set<String>) {
        prefs.edit().putStringSet("locked_cats_$playlistId", categories).apply()
    }

    fun clearAllLockedCategories(playlistIds: List<Long>) {
        val editor = prefs.edit()
        for (id in playlistIds) {
            editor.remove("locked_cats_$id")
        }
        editor.apply()
    }

    fun getAllLockedMap(playlistIds: List<Long>): Map<Long, Set<String>> {
        val map = HashMap<Long, Set<String>>()
        for (id in playlistIds) {
            val set = getLockedCategories(id)
            if (set.isNotEmpty()) map[id] = set
        }
        return map
    }

    companion object {
        const val MAX_LOGIN_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 5L * 60L * 1000L
    }
}
