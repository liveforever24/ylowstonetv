package com.ylowstone.tv.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class SubtitleCandidate(
    val id: String,
    val releaseName: String,
    val author: String,
    val fullSeason: Boolean,
    val zipUrl: String
)

object SubtitleFetcher {
    private const val BASE_API = "https://ylowstonetv.vercel.app/api/subtitles"
    private val SRT_TIME_REGEX = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")

    suspend fun searchIndoSubtitles(
        rawTitle: String,
        isSeries: Boolean,
        season: Int?,
        episode: Int?
    ): Result<List<SubtitleCandidate>> = withContext(Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder(BASE_API)
            sb.append("?title=").append(URLEncoder.encode(rawTitle, "UTF-8"))
            sb.append("&type=").append(if (isSeries) "tv" else "movie")
            if (isSeries && season != null && season > 0) sb.append("&season=").append(season)
            if (isSeries && episode != null && episode > 0) sb.append("&episode=").append(episode)

            val conn = (URL(sb.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "YlowstoneTV/2.2.5")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                throw IllegalStateException(json.optString("error", "Gagal mencari subtitle"))
            }
            val arr = json.optJSONArray("results") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SubtitleCandidate(
                            id = o.optString("id", i.toString()),
                            releaseName = o.optString("releaseName", "Subtitle Indonesia"),
                            author = o.optString("author", ""),
                            fullSeason = o.optBoolean("fullSeason", false),
                            zipUrl = o.optString("zipUrl", "")
                        )
                    )
                }
            }
        }
    }

    suspend fun downloadSubtitleToCache(
        context: Context,
        zipUrl: String,
        season: Int?,
        episode: Int?
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder(BASE_API)
            sb.append("?zip=").append(URLEncoder.encode(zipUrl, "UTF-8"))
            if (season != null && season > 0) sb.append("&season=").append(season)
            if (episode != null && episode > 0) sb.append("&episode=").append(episode)

            val conn = (URL(sb.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                setRequestProperty("User-Agent", "YlowstoneTV/2.2.5")
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                throw IllegalStateException("Server mengembalikan kode ${conn.responseCode}")
            }
            val srtText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()

            val rawFile = File(context.cacheDir, "ylowstone_sub_raw.srt")
            rawFile.writeText(srtText, Charsets.UTF_8)
            rawFile
        }
    }

    suspend fun copyLocalSubtitleToCache(context: Context, uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?.replace("\uFEFF", "")
                ?.replace(Regex("""^WEBVTT[^\n]*\n+""", RegexOption.IGNORE_CASE), "")
                ?: throw IllegalStateException("Gagal membaca file subtitle lokal")

            val rawFile = File(context.cacheDir, "ylowstone_sub_raw.srt")
            rawFile.writeText(text, Charsets.UTF_8)
            rawFile
        }
    }

    fun buildShiftedSubtitleUri(context: Context, rawFile: File, offsetMs: Long): Uri {
        val rawText = rawFile.readText(Charsets.UTF_8)
        val shiftedText = if (offsetMs == 0L) {
            rawText
        } else {
            SRT_TIME_REGEX.replace(rawText) { m ->
                val startMs = toMillis(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4]) + offsetMs
                val endMs = toMillis(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8]) + offsetMs
                "${formatMillis(startMs.coerceAtLeast(0L))} --> ${formatMillis(endMs.coerceAtLeast(0L))}"
            }
        }
        context.cacheDir.listFiles { f -> f.name.startsWith("ylowstone_sub_active_") }?.forEach { it.delete() }
        val outFile = File(context.cacheDir, "ylowstone_sub_active_${System.currentTimeMillis()}.srt")
        outFile.writeText(shiftedText, Charsets.UTF_8)
        return Uri.fromFile(outFile)
    }

    private fun toMillis(h: String, m: String, s: String, ms: String): Long {
        return h.toLong() * 3_600_000L + m.toLong() * 60_000L + s.toLong() * 1_000L + ms.toLong()
    }

    private fun formatMillis(totalMs: Long): String {
        val h = totalMs / 3_600_000L
        val m = (totalMs % 3_600_000L) / 60_000L
        val s = (totalMs % 60_000L) / 1_000L
        val ms = totalMs % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms)
    }
}
