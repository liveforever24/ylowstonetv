package com.ylowstone.tv.data

import android.content.Context
import android.net.Uri
import com.ylowstone.tv.BuildConfig
import com.ylowstone.tv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class IptvRepository(private val context: Context, val db: DatabaseHelper) {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun syncPlaylist(
        profile: PlaylistProfile,
        streamFormat: StreamFormat,
        localUri: Uri? = null,
        onProgress: (String) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val items = when (profile.type) {
                PlaylistType.DIRECT -> {
                    listOf(
                        ChannelItem(
                            playlistId = profile.id,
                            streamId = "direct_1",
                            name = profile.name.ifBlank { "Direct Stream" },
                            category = "Direct Stream",
                            logoUrl = "",
                            streamUrl = profile.url.trim(),
                            mediaType = MediaType.LIVE
                        )
                    )
                }

                PlaylistType.M3U -> {
                    onProgress("Downloading M3U...")
                    if (localUri != null) {
                        context.contentResolver.openInputStream(localUri)?.use { stream ->
                            parseM3uStream(profile.id, stream)
                        } ?: emptyList()
                    } else if (profile.url.startsWith("content://")) {
                        context.contentResolver.openInputStream(Uri.parse(profile.url))?.use { stream ->
                            parseM3uStream(profile.id, stream)
                        } ?: emptyList()
                    } else {
                        val req = Request.Builder()
                            .url(profile.url.trim())
                            .header("User-Agent", profile.userAgent.ifBlank { "YlowstoneTV/1.0" })
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                            val bodyStream = resp.body?.byteStream() ?: throw Exception("Empty response")
                            parseM3uStream(profile.id, bodyStream)
                        }
                    }
                }

                PlaylistType.XTREAM -> {
                    fetchXtreamAll(profile, streamFormat, onProgress)
                }
            }

            onProgress("Indexing ${items.size} items...")
            db.replaceChannels(profile.id, items)
            db.updatePlaylistTimestamp(profile.id, System.currentTimeMillis())
            items.size
        }
    }

    private fun parseM3uStream(playlistId: Long, inputStream: InputStream): List<ChannelItem> {
        val result = ArrayList<ChannelItem>(2048)
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 32768)
        var currentName = ""
        var currentCategory = "Uncategorized"
        var currentLogo = ""
        var currentId = ""
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                currentLogo = extractAttr(trimmed, "tvg-logo")
                currentCategory = extractAttr(trimmed, "group-title").ifBlank { "General" }
                currentId = extractAttr(trimmed, "tvg-id")
                val commaIdx = trimmed.lastIndexOf(',')
                currentName = if (commaIdx != -1 && commaIdx + 1 < trimmed.length) {
                    trimmed.substring(commaIdx + 1).trim()
                } else {
                    "Channel ${result.size + 1}"
                }
            } else if (!trimmed.startsWith("#")) {
                val lowerUrl = trimmed.lowercase()
                val mediaType = when {
                    lowerUrl.contains("/series/") -> MediaType.SERIES
                    lowerUrl.contains("/movie/") || lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".mp4") -> MediaType.MOVIE
                    else -> MediaType.LIVE
                }
                result.add(
                    ChannelItem(
                        playlistId = playlistId,
                        streamId = currentId.ifBlank { result.size.toString() },
                        name = currentName.ifBlank { "Channel ${result.size + 1}" },
                        category = currentCategory.ifBlank { "General" },
                        logoUrl = currentLogo,
                        streamUrl = trimmed,
                        mediaType = mediaType
                    )
                )
                currentName = ""
                currentLogo = ""
                currentCategory = "General"
                currentId = ""
            }
        }
        return result
    }

    private fun extractAttr(line: String, attr: String): String {
        val key = "$attr=\""
        val start = line.indexOf(key, ignoreCase = true)
        if (start == -1) return ""
        val valStart = start + key.length
        val end = line.indexOf('"', valStart)
        if (end == -1) return ""
        return line.substring(valStart, end).trim()
    }

    private fun fetchJsonString(url: String, ua: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", ua.ifBlank { "YlowstoneTV/1.0" })
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }

    private fun fetchCategoryMap(apiBase: String, action: String, ua: String): Map<String, String> {
        val map = HashMap<String, String>()
        runCatching {
            val body = fetchJsonString("$apiBase&action=$action", ua)
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("category_id")
                val name = obj.optString("category_name", "General")
                if (id.isNotEmpty()) map[id] = name
            }
        }
        return map
    }

    private fun fetchXtreamAll(
        profile: PlaylistProfile,
        streamFormat: StreamFormat,
        onProgress: (String) -> Unit
    ): List<ChannelItem> {
        val base = profile.url.trim().trimEnd('/')
        val u = Uri.encode(profile.username.trim())
        val p = Uri.encode(profile.password.trim())
        val apiBase = "$base/player_api.php?username=$u&password=$p"
        val ua = profile.userAgent
        val all = ArrayList<ChannelItem>(4096)

        // 1. Live TV
        onProgress("Loading Live TV...")
        val liveCats = fetchCategoryMap(apiBase, "get_live_categories", ua)
        runCatching {
            val liveArr = JSONArray(fetchJsonString("$apiBase&action=get_live_streams", ua))
            val ext = streamFormat.ext
            for (i in 0 until liveArr.length()) {
                val o = liveArr.optJSONObject(i) ?: continue
                val sid = o.optString("stream_id")
                if (sid.isEmpty()) continue
                val catName = liveCats[o.optString("category_id")] ?: "Live TV"
                val streamUrl = "$base/live/$u/$p/$sid.$ext"
                all.add(
                    ChannelItem(
                        playlistId = profile.id,
                        streamId = sid,
                        name = o.optString("name", "Live $sid"),
                        category = catName,
                        logoUrl = o.optString("stream_icon", ""),
                        streamUrl = streamUrl,
                        mediaType = MediaType.LIVE,
                        containerExtension = ext
                    )
                )
            }
        }

        // 2. Movies (VOD)
        onProgress("Loading Movies...")
        val vodCats = fetchCategoryMap(apiBase, "get_vod_categories", ua)
        runCatching {
            val vodArr = JSONArray(fetchJsonString("$apiBase&action=get_vod_streams", ua))
            for (i in 0 until vodArr.length()) {
                val o = vodArr.optJSONObject(i) ?: continue
                val sid = o.optString("stream_id")
                if (sid.isEmpty()) continue
                val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
                val catName = vodCats[o.optString("category_id")] ?: "Movies"
                val streamUrl = "$base/movie/$u/$p/$sid.$ext"
                all.add(
                    ChannelItem(
                        playlistId = profile.id,
                        streamId = sid,
                        name = o.optString("name", "Movie $sid"),
                        category = catName,
                        logoUrl = o.optString("stream_icon", ""),
                        streamUrl = streamUrl,
                        mediaType = MediaType.MOVIE,
                        containerExtension = ext,
                        rating = o.optString("rating", "")
                    )
                )
            }
        }

        // 3. Series
        onProgress("Loading Series...")
        val seriesCats = fetchCategoryMap(apiBase, "get_series_categories", ua)
        runCatching {
            val serArr = JSONArray(fetchJsonString("$apiBase&action=get_series", ua))
            for (i in 0 until serArr.length()) {
                val o = serArr.optJSONObject(i) ?: continue
                val sid = o.optString("series_id")
                if (sid.isEmpty()) continue
                val catName = seriesCats[o.optString("category_id")] ?: "Series"
                all.add(
                    ChannelItem(
                        playlistId = profile.id,
                        streamId = sid,
                        name = o.optString("name", "Series $sid"),
                        category = catName,
                        logoUrl = o.optString("cover", ""),
                        streamUrl = "$apiBase&action=get_series_info&series_id=$sid",
                        mediaType = MediaType.SERIES,
                        rating = o.optString("rating", "")
                    )
                )
            }
        }

        if (all.isEmpty()) {
            throw Exception("No channels returned. Check URL, username, or password.")
        }
        return all
    }

    suspend fun fetchSeriesEpisodes(profile: PlaylistProfile, seriesId: String): List<EpisodeItem> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<EpisodeItem>()
            runCatching {
                val base = profile.url.trim().trimEnd('/')
                val u = Uri.encode(profile.username.trim())
                val p = Uri.encode(profile.password.trim())
                val body = fetchJsonString(
                    "$base/player_api.php?username=$u&password=$p&action=get_series_info&series_id=$seriesId",
                    profile.userAgent
                )
                val root = JSONObject(body)
                val episodesObj = root.optJSONObject("episodes") ?: return@runCatching
                val keys = episodesObj.keys()
                while (keys.hasNext()) {
                    val seasonKey = keys.next()
                    val seasonNum = seasonKey.toIntOrNull() ?: 1
                    val epArr = episodesObj.optJSONArray(seasonKey) ?: continue
                    for (i in 0 until epArr.length()) {
                        val ep = epArr.optJSONObject(i) ?: continue
                        val eid = ep.optString("id")
                        val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
                        val epNum = ep.optInt("episode_num", i + 1)
                        val title = ep.optString("title", "S${seasonNum}E${epNum}")
                        val infoObj = ep.optJSONObject("info")
                        val plot = infoObj?.optString("plot", "") ?: ""
                        if (eid.isNotEmpty()) {
                            list.add(
                                EpisodeItem(
                                    id = eid,
                                    season = seasonNum,
                                    episodeNum = epNum,
                                    title = title,
                                    streamUrl = "$base/series/$u/$p/$eid.$ext",
                                    plot = plot
                                )
                            )
                        }
                    }
                }
            }
            list.sortedWith(compareBy({ it.season }, { it.episodeNum }))
        }

    // ---- GitHub Releases Auto-Updater ----
    suspend fun checkGitHubUpdate(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val apiUrl = "https://api.github.com/repos/liveforever24/ylowstonetv/releases/latest"
            val req = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "YlowstoneTV-Updater")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.code == 404) {
                    return@runCatching UpdateInfo(
                        latestVersion = BuildConfig.VERSION_NAME,
                        releaseNotes = "",
                        apkDownloadUrl = "",
                        htmlUrl = "https://github.com/liveforever24/ylowstonetv/releases",
                        hasUpdate = false
                    )
                }
                if (!resp.isSuccessful) throw Exception("GitHub HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string() ?: "{}")
                val tag = json.optString("tag_name", BuildConfig.VERSION_NAME).removePrefix("v")
                val notes = json.optString("body", "")
                val htmlUrl = json.optString("html_url", "https://github.com/liveforever24/ylowstonetv/releases")
                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = a.optString("browser_download_url", "")
                            break
                        }
                    }
                }
                val hasNew = compareVersions(tag, BuildConfig.VERSION_NAME) > 0 && apkUrl.isNotEmpty()
                UpdateInfo(
                    latestVersion = tag,
                    releaseNotes = notes,
                    apkDownloadUrl = apkUrl,
                    htmlUrl = htmlUrl,
                    hasUpdate = hasNew
                )
            }
        }
    }

    suspend fun downloadApk(url: String, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).header("User-Agent", "YlowstoneTV-Updater").build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("Download failed HTTP ${resp.code}")
                    val body = resp.body ?: throw Exception("Empty APK body")
                    val total = body.contentLength()
                    val outFile = File(context.cacheDir, "YlowstoneTV-update.apk")
                    body.byteStream().use { input ->
                        outFile.outputStream().use { output ->
                            val buf = ByteArray(16384)
                            var copied = 0L
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                copied += read
                                if (total > 0) onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                    outFile
                }
            }
        }

    private fun compareVersions(v1: String, v2: String): Int {
        val p1 = v1.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val p2 = v2.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val max = maxOf(p1.size, p2.size)
        for (i in 0 until max) {
            val a = p1.getOrElse(i) { 0 }
            val b = p2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    companion object {
        const val FIXED_ADMIN_USERNAME = "YlowstoneTV"
        const val ADMIN_AUTH_URL = "https://ylowstonetv.vercel.app/api/login"
    }

    suspend fun loginAdmin(pin: String): Result<AdminAuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("username", FIXED_ADMIN_USERNAME)
                put("pin", pin.trim())
            }
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(ADMIN_AUTH_URL)
                .header("User-Agent", "YlowstoneTV-Android/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .post(body)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (!resp.isSuccessful || json == null || !json.optBoolean("ok", false)) {
                    val serverMsg = json?.optString("error").orEmpty()
                    throw Exception(
                        serverMsg.ifBlank {
                            if (resp.code == 401) "Username atau PIN salah"
                            else "Server error (HTTP ${resp.code})"
                        }
                    )
                }

                val arr = json.optJSONArray("playlists") ?: JSONArray()
                val parsedPlaylists = mutableListOf<PlaylistProfile>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val pName = item.optString("name", "").trim()
                    val pUrl = item.optString("url", "").trim()
                    if (pName.isBlank() || pUrl.isBlank()) continue
                    val pType = runCatching {
                        PlaylistType.valueOf(item.optString("type", "XTREAM").trim().uppercase())
                    }.getOrDefault(PlaylistType.XTREAM)
                    parsedPlaylists.add(
                        PlaylistProfile(
                            id = 0L,
                            name = pName,
                            type = pType,
                            url = pUrl,
                            username = item.optString("username", "").trim(),
                            password = item.optString("password", "").trim(),
                            userAgent = item.optString("userAgent", "").trim(),
                            colorIndex = i % 6,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }

                AdminAuthResult(
                    username = json.optString("username", FIXED_ADMIN_USERNAME),
                    role = json.optString("role", "admin"),
                    playlists = parsedPlaylists
                )
            }
        }
    }

}
