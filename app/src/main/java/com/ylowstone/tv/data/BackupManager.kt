package com.ylowstone.tv.data

import android.content.Context
import android.net.Uri
import com.ylowstone.tv.model.*
import com.ylowstone.tv.util.SecurityCrypto
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup & Restore 1-File Terenkripsi AES-256-GCM (v2.4.7):
 * - Membawa pengaturan, SELURUH daftar playlist (termasuk Xtream/M3U/Direct), & favorit.
 * - Payload terkunci penuh dengan AES-256-GCM agar tidak bisa dibaca plain-text oleh pihak luar.
 * - Tetap kompatibel mundur (backward-compatible) untuk file backup .json lama yang belum terenkripsi.
 */
object BackupManager {

    data class RestoreResult(
        val settings: AppSettings,
        val playlistsRestored: Int,
        val favoritesRestored: Int
    )

    fun exportToUri(context: Context, uri: Uri, prefs: AppPrefs, db: DatabaseHelper): Result<Int> = runCatching {
        val s = prefs.loadSettings()
        val allPlaylists = db.getAllPlaylists()
        val favUrls = db.getAllFavoriteStreamUrls()

        val innerPayload = JSONObject().apply {
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

            val plArr = JSONArray()
            allPlaylists.forEach { p ->
                plArr.put(JSONObject().apply {
                    put("name", p.name)
                    put("type", p.type.name)
                    put("url", p.url)
                    put("username", p.username)
                    put("password", p.password)
                    put("userAgent", p.userAgent)
                    put("colorIndex", p.colorIndex)
                    put("lastUpdated", p.lastUpdated)
                    put("liveCategories", JSONArray(p.liveCategories))
                    put("movieCategories", JSONArray(p.movieCategories))
                    put("seriesCategories", JSONArray(p.seriesCategories))
                })
            }
            put("playlists", plArr)

            val favArr = JSONArray()
            favUrls.forEach { favArr.put(it) }
            put("favoriteStreamUrls", favArr)
        }

        val (ivB64, cipherB64) = SecurityCrypto.encryptBackupPayload(innerPayload.toString())

        val envelope = JSONObject().apply {
            put("app", "YlowstoneTV")
            put("enc", "AES-256-GCM")
            put("version", "2.4.7")
            put("exportedAt", System.currentTimeMillis())
            put("iv", ivB64)
            put("payload", cipherB64)
        }

        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(envelope.toString(2))
        } ?: throw IllegalStateException("Gagal menulis file backup")

        allPlaylists.size
    }

    fun importFromUri(context: Context, uri: Uri, prefs: AppPrefs, db: DatabaseHelper): Result<RestoreResult> = runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("Gagal membaca file backup")
        val outer = JSONObject(raw)

        val root = if (outer.optString("enc") == "AES-256-GCM" && outer.has("iv") && outer.has("payload")) {
            val decrypted = SecurityCrypto.decryptBackupPayload(
                outer.getString("iv"),
                outer.getString("payload")
            )
            JSONObject(decrypted)
        } else {
            outer
        }

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

        var playlistsRestored = 0
        val plArr = root.optJSONArray("playlists")
        if (plArr != null && plArr.length() > 0) {
            val existing = db.getAllPlaylists().toMutableList()
            for (i in 0 until plArr.length()) {
                val obj = plArr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                val url = obj.optString("url", "").trim()
                if (name.isEmpty() || url.isEmpty()) continue
                val type = runCatching {
                    PlaylistType.valueOf(obj.optString("type", "M3U"))
                }.getOrDefault(PlaylistType.M3U)
                val username = obj.optString("username", "")
                val password = obj.optString("password", "")
                val userAgent = obj.optString("userAgent", "")
                val colorIndex = obj.optInt("colorIndex", i % 6)
                val lastUpdated = obj.optLong("lastUpdated", 0L)

                val match = existing.firstOrNull {
                    it.type == type &&
                        it.url.equals(url, ignoreCase = true) &&
                        it.username == username
                } ?: existing.firstOrNull {
                    it.name.equals(name, ignoreCase = true) && it.type == type
                }

                val profile = PlaylistProfile(
                    id = match?.id ?: 0L,
                    name = name,
                    type = type,
                    url = url,
                    username = username,
                    password = password,
                    userAgent = userAgent,
                    liveCategories = jsonArrayToStringList(obj.optJSONArray("liveCategories")),
                    movieCategories = jsonArrayToStringList(obj.optJSONArray("movieCategories")),
                    seriesCategories = jsonArrayToStringList(obj.optJSONArray("seriesCategories")),
                    colorIndex = colorIndex,
                    lastUpdated = lastUpdated
                )
                val savedId = db.upsertPlaylist(profile)
                if (match == null) {
                    existing.add(profile.copy(id = savedId))
                }
                playlistsRestored++
            }
        }

        var favoritesRestored = 0
        val favArr = root.optJSONArray("favoriteStreamUrls")
        if (favArr != null) {
            val urls = HashSet<String>()
            for (i in 0 until favArr.length()) {
                val u = favArr.optString(i, "").trim()
                if (u.isNotEmpty()) urls.add(u)
            }
            if (urls.isNotEmpty()) {
                db.restoreFavoriteStreamUrls(urls)
                favoritesRestored = urls.size
            }
        }

        RestoreResult(
            settings = restoredSettings,
            playlistsRestored = playlistsRestored,
            favoritesRestored = favoritesRestored
        )
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }
}
