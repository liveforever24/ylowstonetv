package com.ylowstone.tv.data

import android.content.Context
import com.ylowstone.tv.model.*

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ylowstone_tv_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        return AppSettings(
            hasSelectedMode = prefs.getBoolean("has_selected_mode", false),
            deviceMode = runCatching { DeviceMode.valueOf(prefs.getString("device_mode", "SMARTPHONE")!!) }.getOrDefault(DeviceMode.SMARTPHONE),
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", "AMOLED")!!) }.getOrDefault(ThemeMode.AMOLED),
            accent = runCatching { AccentTheme.valueOf(prefs.getString("accent", "APPLE_ROSE")!!) }.getOrDefault(AccentTheme.APPLE_ROSE),
            language = runCatching { AppLanguage.valueOf(prefs.getString("language", "ID")!!) }.getOrDefault(AppLanguage.ID),
            showPlayerGestures = prefs.getBoolean("show_gestures", true),
            streamFormat = runCatching { StreamFormat.valueOf(prefs.getString("stream_format", "HLS")!!) }.getOrDefault(StreamFormat.HLS),
            pinCode = prefs.getString("pin_code", "1234") ?: "1234",
            use24HourClock = prefs.getBoolean("use_24h", true),
            preferExternalPlayer = prefs.getBoolean("prefer_external", false)
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putBoolean("has_selected_mode", s.hasSelectedMode)
            .putString("device_mode", s.deviceMode.name)
            .putString("theme_mode", s.themeMode.name)
            .putString("accent", s.accent.name)
            .putString("language", s.language.name)
            .putBoolean("show_gestures", s.showPlayerGestures)
            .putString("stream_format", s.streamFormat.name)
            .putString("pin_code", s.pinCode)
            .putBoolean("use_24h", s.use24HourClock)
            .putBoolean("prefer_external", s.preferExternalPlayer)
            .apply()
    }

    fun getLockedCategories(playlistId: Long): Set<String> {
        return prefs.getStringSet("locked_cats_$playlistId", emptySet())?.toSet() ?: emptySet()
    }

    fun setLockedCategories(playlistId: Long, categories: Set<String>) {
        prefs.edit().putStringSet("locked_cats_$playlistId", categories).apply()
    }
}
