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

data class SrtCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class ActiveSubtitleSession(
    val streamUrl: String = "",
    val candidates: List<SubtitleCandidate> = emptyList(),
    val rawFile: File? = null,
    val cues: List<SrtCue> = emptyList(),
    val label: String = "",
    val offsetMs: Long = 0L
)

object SubtitleFetcher {
    private const val BASE_API = "https://ylowstonetv.vercel.app/api/subtitles"
    private val SRT_TIME_REGEX = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})""")
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")

    /**
     * Memilih kandidat subtitle dengan kecocokan release-tag tertinggi terhadap judul stream (WEB-DL, BluRay, 1080p, SxxExx).
     */
    fun pickBestCandidate(
        candidates: List<SubtitleCandidate>,
        streamTitle: String,
        season: Int?,
        episode: Int?
    ): SubtitleCandidate? {
        if (candidates.isEmpty()) return null
        val target = streamTitle.lowercase(Locale.US)
        val tags = listOf("web-dl", "webrip", "bluray", "bdrip", "1080p", "720p", "2160p", "4k", "hdr", "x265", "hevc", "x264")
        val epTag = if (season != null && episode != null) {
            String.format(Locale.US, "s%02de%02d", season, episode)
        } else null

        return candidates.maxByOrNull { cand ->
            val rel = cand.releaseName.lowercase(Locale.US)
            var score = 0
            if (epTag != null && rel.contains(epTag)) score += 30
            if (!cand.fullSeason && epTag != null) score += 8
            for (tag in tags) {
                if (target.contains(tag) && rel.contains(tag)) score += 10
            }
            if (rel.contains("web-dl") || rel.contains("webrip")) score += 4
            score
        } ?: candidates.first()
    }

    /**
     * Menghitung offset agar cue dialog terdekat sejajar dengan posisi pemutaran saat ini (Quick Tap-Sync).
     */
    fun computeQuickSyncOffsetMs(cues: List<SrtCue>, currentPosMs: Long, currentOffsetMs: Long): Long {
        if (cues.isEmpty()) return currentOffsetMs
        val effectivePos = currentPosMs + currentOffsetMs
        val nearest = cues.minByOrNull { cue ->
            kotlin.math.abs(cue.startMs - effectivePos)
        } ?: return currentOffsetMs
        return nearest.startMs - currentPosMs
    }

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
                setRequestProperty("User-Agent", "YlowstoneTV/2.3.3")
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
                setRequestProperty("User-Agent", "YlowstoneTV/2.3.3")
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

    suspend fun parseSrtCues(rawFile: File): List<SrtCue> = withContext(Dispatchers.IO) {
        runCatching {
            val content = rawFile.readText(Charsets.UTF_8)
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
            val blocks = content.split(Regex("""\n\s*\n+"""))
            val result = ArrayList<SrtCue>(blocks.size)
            for (block in blocks) {
                val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.size < 2) continue
                val timeLineIdx = lines.indexOfFirst { SRT_TIME_REGEX.containsMatchIn(it) }
                if (timeLineIdx < 0 || timeLineIdx + 1 >= lines.size) continue
                val m = SRT_TIME_REGEX.find(lines[timeLineIdx]) ?: continue
                val startMs = toMillis(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
                val endMs = toMillis(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8])
                val body = lines.drop(timeLineIdx + 1)
                    .joinToString("\n")
                    .replace(HTML_TAG_REGEX, "")
                    .trim()
                if (body.isNotEmpty() && endMs >= startMs) {
                    result.add(SrtCue(startMs = startMs, endMs = endMs, text = body))
                }
            }
            result.sortedBy { it.startMs }
        }.getOrDefault(emptyList())
    }

    fun findCueTextAt(cues: List<SrtCue>, timeMs: Long): String {
        if (cues.isEmpty() || timeMs < 0L) return ""
        val active = mutableListOf<String>()
        for (cue in cues) {
            if (cue.startMs > timeMs) break
            if (timeMs in cue.startMs..cue.endMs) {
                active.add(cue.text)
            }
        }
        return active.joinToString("\n")
    }

    private fun toMillis(h: String, m: String, s: String, ms: String): Long {
        return h.toLong() * 3_600_000L + m.toLong() * 60_000L + s.toLong() * 1_000L + ms.toLong()
    }
}
