package com.ylowstone.tv.data

import android.content.Context
import android.net.Uri
import com.ylowstone.tv.model.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup & Restore 1-File JSON untuk Pengaturan & Saluran Favorit (v2.4.0).
 */
object BackupManager {

    fun exportToUri(context: Context, uri: Uri, prefs: AppPrefs, db: DatabaseHelper): Result<Int> = runCatching {
        val s = prefs.loadSettings()
        val favUrls = db.getAllFavoriteStreamUrls()

        val root = JSONObject().apply {
            put("app", "YlowstoneTV")
            put("version", "2.4.2")
            put("exportedAt", System.currentTimeMillis())

            put("settings", JSONObject().apply {
                put("deviceMode", s.deviceMode.name)
                put("themeMode", s.themeMode.name)
                put("accentPreset", s.accentPreset.key)
                put("loadingAnimStyle", s.loadingAnimStyle.key)
                put("language", s.language.name)
                put("favoriteViewMode", s.favoriteViewMode.name)
                put("showPlayerGestures", s.showPlayerGestures)
                put("streamFormat", s.streamFormat.name)
                put("bufferProfile", s.bufferProfile.name)
                put("autoReconnectLive", s.autoReconnectLive)
                put("autoSubtitleLang", s.autoSubtitleLang.name)
                put("skipIntroMode", s.skipIntroMode.name)
                put("skipIntroSeconds", s.skipIntroSeconds)
                put("autoNextEpisode", s.autoNextEpisode)
                put("pinCode", s.pinCode)
                put("preferExternalPlayer", s.preferExternalPlayer)
            })

            val favArr = JSONArray()
            favUrls.forEach { favArr.put(it) }
            put("favoriteStreamUrls", favArr)
        }

        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(root.toString(2))
        } ?: throw IllegalStateException("Gagal menulis file backup")

        favUrls.size
    }

    fun importFromUri(context: Context, uri: Uri, prefs: AppPrefs, db: DatabaseHelper): Result<AppSettings> = runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("Gagal membaca file backup")
        val root = JSONObject(raw)
        val current = prefs.loadSettings()

        val sObj = root.optJSONObject("settings")
        val restoredSettings = if (sObj != null) {
            current.copy(
                deviceMode = runCatching { DeviceMode.valueOf(sObj.optString("deviceMode", current.deviceMode.name)) }.getOrDefault(current.deviceMode),
                themeMode = runCatching { ThemeMode.valueOf(sObj.optString("themeMode", current.themeMode.name)) }.getOrDefault(current.themeMode),
                accentPreset = AccentPreset.fromKey(sObj.optString("accentPreset", current.accentPreset.key)),
                loadingAnimStyle = LoadingAnimStyle.fromKey(sObj.optString("loadingAnimStyle", current.loadingAnimStyle.key)),
                language = runCatching { AppLanguage.valueOf(sObj.optString("language", current.language.name)) }.getOrDefault(current.language),
                favoriteViewMode = runCatching { FavoriteViewMode.valueOf(sObj.optString("favoriteViewMode", current.favoriteViewMode.name)) }.getOrDefault(current.favoriteViewMode),
                showPlayerGestures = sObj.optBoolean("showPlayerGestures", current.showPlayerGestures),
                streamFormat = runCatching { StreamFormat.valueOf(sObj.optString("streamFormat", current.streamFormat.name)) }.getOrDefault(current.streamFormat),
                bufferProfile = runCatching { BufferProfile.valueOf(sObj.optString("bufferProfile", current.bufferProfile.name)) }.getOrDefault(current.bufferProfile),
                autoReconnectLive = sObj.optBoolean("autoReconnectLive", current.autoReconnectLive),
                autoSubtitleLang = runCatching { AutoSubtitleLang.valueOf(sObj.optString("autoSubtitleLang", current.autoSubtitleLang.name)) }.getOrDefault(current.autoSubtitleLang),
                skipIntroMode = runCatching { SkipIntroMode.valueOf(sObj.optString("skipIntroMode", current.skipIntroMode.name)) }.getOrDefault(current.skipIntroMode),
                skipIntroSeconds = sObj.optInt("skipIntroSeconds", current.skipIntroSeconds).coerceIn(5, 600),
                autoNextEpisode = sObj.optBoolean("autoNextEpisode", current.autoNextEpisode),
                pinCode = sObj.optString("pinCode", current.pinCode).takeIf { it.length == 4 && it.all(Char::isDigit) } ?: current.pinCode,
                preferExternalPlayer = sObj.optBoolean("preferExternalPlayer", current.preferExternalPlayer)
            )
        } else current
        prefs.saveSettings(restoredSettings)

        val favArr = root.optJSONArray("favoriteStreamUrls")
        if (favArr != null) {
            val urls = HashSet<String>()
            for (i in 0 until favArr.length()) {
                val u = favArr.optString(i, "").trim()
                if (u.isNotEmpty()) urls.add(u)
            }
            if (urls.isNotEmpty()) db.restoreFavoriteStreamUrls(urls)
        }

        restoredSettings
    }
}
