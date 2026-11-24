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
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
            appIcon = AppIconOption.fromKey(prefs.getString("app_icon", "obsidian")),
            language = resolvedLang,
            showPlayerGestures = prefs.getBoolean("show_gestures", true),
            streamFormat = runCatching { StreamFormat.valueOf(prefs.getString("stream_format", "HLS")!!) }.getOrDefault(StreamFormat.HLS),
            pinCode = prefs.getString("pin_code", "1234") ?: "1234",
            use24HourClock = prefs.getBoolean("use_24h", true),
            preferExternalPlayer = prefs.getBoolean("prefer_external", false)
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putBoolean("has_seen_welcome", s.hasSeenWelcome)
            .putBoolean("has_selected_mode", s.hasSelectedMode)
            .putString("device_mode", s.deviceMode.name)
            .putString("theme_mode", s.themeMode.name)
            .putString("app_icon", s.appIcon.key)
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
}
