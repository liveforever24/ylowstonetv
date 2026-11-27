package com.ylowstone.tv.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ylowstone.tv.model.*
import com.ylowstone.tv.util.SecurityCrypto

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "ylowstone_tv.db", null, 5) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                url TEXT NOT NULL,
                username TEXT NOT NULL,
                password TEXT NOT NULL,
                user_agent TEXT NOT NULL,
                live_categories TEXT NOT NULL DEFAULT '',
                movie_categories TEXT NOT NULL DEFAULT '',
                series_categories TEXT NOT NULL DEFAULT '',
                color_index INTEGER NOT NULL DEFAULT 0,
                last_updated INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE channels (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                playlist_id INTEGER NOT NULL,
                stream_id TEXT NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                logo_url TEXT NOT NULL,
                stream_url TEXT NOT NULL,
                media_type TEXT NOT NULL,
                container_ext TEXT NOT NULL,
                rating TEXT NOT NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                last_watched INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        createWatchHistoryTable(db)
        createWatchGroupsTable(db)

        db.execSQL("CREATE INDEX idx_channels_main ON channels(playlist_id, media_type, category)")
        db.execSQL("CREATE INDEX idx_channels_name ON channels(playlist_id, name)")
        db.execSQL("CREATE INDEX idx_channels_fav ON channels(playlist_id, media_type, is_favorite)")
    }

    private fun createWatchHistoryTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS watch_history (
                stream_url TEXT PRIMARY KEY,
                playlist_id INTEGER NOT NULL,
                playlist_name TEXT NOT NULL,
                channel_id INTEGER NOT NULL DEFAULT 0,
                stream_id TEXT NOT NULL DEFAULT '',
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                logo_url TEXT NOT NULL DEFAULT '',
                media_type TEXT NOT NULL,
                position_ms INTEGER NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                last_watched INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_watch_history_time ON watch_history(last_watched DESC)")
    }

    private fun createWatchGroupsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS watch_groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_name TEXT NOT NULL,
                stream_url TEXT NOT NULL,
                playlist_id INTEGER NOT NULL,
                playlist_name TEXT NOT NULL,
                channel_id INTEGER NOT NULL DEFAULT 0,
                stream_id TEXT NOT NULL DEFAULT '',
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                logo_url TEXT NOT NULL DEFAULT '',
                media_type TEXT NOT NULL,
                added_at INTEGER NOT NULL DEFAULT 0,
                UNIQUE(group_name, stream_url)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_watch_groups_name ON watch_groups(group_name, added_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching {
                db.execSQL("ALTER TABLE playlists ADD COLUMN live_categories TEXT NOT NULL DEFAULT ''")
            }
        }
        if (oldVersion < 3) {
            runCatching {
                db.execSQL("ALTER TABLE playlists ADD COLUMN movie_categories TEXT NOT NULL DEFAULT ''")
            }
            runCatching {
                db.execSQL("ALTER TABLE playlists ADD COLUMN series_categories TEXT NOT NULL DEFAULT ''")
            }
        }
        if (oldVersion < 4) {
            runCatching { createWatchHistoryTable(db) }
        }
        if (oldVersion < 5) {
            runCatching { createWatchGroupsTable(db) }
        }
    }

    fun getAllPlaylists(): List<PlaylistProfile> {
        val list = mutableListOf<PlaylistProfile>()
        readableDatabase.rawQuery("SELECT * FROM playlists ORDER BY id DESC", null).use { c ->
            val idxId = c.getColumnIndex("id")
            val idxName = c.getColumnIndex("name")
            val idxType = c.getColumnIndex("type")
            val idxUrl = c.getColumnIndex("url")
            val idxUser = c.getColumnIndex("username")
            val idxPass = c.getColumnIndex("password")
            val idxUa = c.getColumnIndex("user_agent")
            val idxLive = c.getColumnIndex("live_categories")
            val idxMovie = c.getColumnIndex("movie_categories")
            val idxSeries = c.getColumnIndex("series_categories")
            val idxColor = c.getColumnIndex("color_index")
            val idxUpdated = c.getColumnIndex("last_updated")

            while (c.moveToNext()) {
                val rawUrl = if (idxUrl >= 0) c.getString(idxUrl) ?: "" else ""
                val rawUser = if (idxUser >= 0) c.getString(idxUser) ?: "" else ""
                val rawPass = if (idxPass >= 0) c.getString(idxPass) ?: "" else ""
                list.add(
                    PlaylistProfile(
                        id = if (idxId >= 0) c.getLong(idxId) else 0L,
                        name = if (idxName >= 0) c.getString(idxName) ?: "" else "",
                        type = runCatching {
                            PlaylistType.valueOf(if (idxType >= 0) c.getString(idxType) ?: "M3U" else "M3U")
                        }.getOrDefault(PlaylistType.M3U),
                        url = SecurityCrypto.decryptLocalField(rawUrl),
                        username = SecurityCrypto.decryptLocalField(rawUser),
                        password = SecurityCrypto.decryptLocalField(rawPass),
                        userAgent = if (idxUa >= 0) c.getString(idxUa) ?: "" else "",
                        liveCategories = if (idxLive >= 0) parseCategoryList(c.getString(idxLive)) else emptyList(),
                        movieCategories = if (idxMovie >= 0) parseCategoryList(c.getString(idxMovie)) else emptyList(),
                        seriesCategories = if (idxSeries >= 0) parseCategoryList(c.getString(idxSeries)) else emptyList(),
                        colorIndex = if (idxColor >= 0) c.getInt(idxColor) else 0,
                        lastUpdated = if (idxUpdated >= 0) c.getLong(idxUpdated) else 0L
                    )
                )
            }
        }
        return list
    }

    fun upsertPlaylist(p: PlaylistProfile): Long {
        val cv = ContentValues().apply {
            put("name", p.name)
            put("type", p.type.name)
            put("url", SecurityCrypto.encryptLocalField(p.url))
            put("username", SecurityCrypto.encryptLocalField(p.username))
            put("password", SecurityCrypto.encryptLocalField(p.password))
            put("user_agent", p.userAgent)
            put("live_categories", serializeCategoryList(p.liveCategories))
            put("movie_categories", serializeCategoryList(p.movieCategories))
            put("series_categories", serializeCategoryList(p.seriesCategories))
            put("color_index", p.colorIndex)
            put("last_updated", p.lastUpdated)
        }
        return if (p.id == 0L) {
            writableDatabase.insert("playlists", null, cv)
        } else {
            writableDatabase.update("playlists", cv, "id = ?", arrayOf(p.id.toString()))
            p.id
        }
    }

    fun updatePlaylistTimestamp(playlistId: Long, ts: Long) {
        val cv = ContentValues().apply { put("last_updated", ts) }
        writableDatabase.update("playlists", cv, "id = ?", arrayOf(playlistId.toString()))
    }

    fun deletePlaylist(playlistId: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("channels", "playlist_id = ?", arrayOf(playlistId.toString()))
            writableDatabase.delete("watch_history", "playlist_id = ?", arrayOf(playlistId.toString()))
            writableDatabase.delete("watch_groups", "playlist_id = ?", arrayOf(playlistId.toString()))
            writableDatabase.delete("playlists", "id = ?", arrayOf(playlistId.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun replaceChannels(playlistId: Long, items: List<ChannelItem>) {
        val db = writableDatabase
        val favUrls = HashSet<String>()
        val watchedMap = HashMap<String, Long>()
        db.rawQuery(
            "SELECT stream_url, is_favorite, last_watched FROM channels WHERE playlist_id = ? AND (is_favorite = 1 OR last_watched > 0)",
            arrayOf(playlistId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val plainUrl = SecurityCrypto.decryptStreamUrl(c.getString(0) ?: continue)
                if (c.getInt(1) == 1) favUrls.add(plainUrl)
                val lw = c.getLong(2)
                if (lw > 0L) watchedMap[plainUrl] = lw
            }
        }

        db.beginTransaction()
        try {
            db.delete("channels", "playlist_id = ?", arrayOf(playlistId.toString()))
            val stmt = db.compileStatement(
                "INSERT INTO channels (playlist_id, stream_id, name, category, logo_url, stream_url, media_type, container_ext, rating, is_favorite, last_watched) VALUES (?,?,?,?,?,?,?,?,?,?,?)"
            )
            for (item in items) {
                stmt.clearBindings()
                stmt.bindLong(1, playlistId)
                stmt.bindString(2, item.streamId)
                stmt.bindString(3, item.name)
                stmt.bindString(4, item.category)
                stmt.bindString(5, item.logoUrl)
                stmt.bindString(6, SecurityCrypto.encryptStreamUrl(item.streamUrl))
                stmt.bindString(7, item.mediaType.name)
                stmt.bindString(8, item.containerExtension)
                stmt.bindString(9, item.rating)
                stmt.bindLong(10, if (favUrls.contains(item.streamUrl)) 1L else 0L)
                stmt.bindLong(11, watchedMap[item.streamUrl] ?: 0L)
                stmt.executeInsert()
            }
            stmt.close()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    data class SyncSession(
        val playlistId: Long,
        val favUrls: Set<String>,
        val watchedMap: Map<String, Long>,
        var insertedCount: Int = 0
    )

    fun beginChannelSync(playlistId: Long): SyncSession {
        val db = writableDatabase
        val favUrls = HashSet<String>()
        val watchedMap = HashMap<String, Long>()
        db.rawQuery(
            "SELECT stream_url, is_favorite, last_watched FROM channels WHERE playlist_id = ? AND (is_favorite = 1 OR last_watched > 0)",
            arrayOf(playlistId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val plainUrl = SecurityCrypto.decryptStreamUrl(c.getString(0) ?: continue)
                if (c.getInt(1) == 1) favUrls.add(plainUrl)
                val lw = c.getLong(2)
                if (lw > 0L) watchedMap[plainUrl] = lw
            }
        }
        db.beginTransaction()
        try {
            db.delete("channels", "playlist_id = ?", arrayOf(playlistId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return SyncSession(playlistId, favUrls, watchedMap)
    }

    fun appendChannelBatch(session: SyncSession, batch: List<ChannelItem>) {
        if (batch.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                "INSERT INTO channels (playlist_id, stream_id, name, category, logo_url, stream_url, media_type, container_ext, rating, is_favorite, last_watched) VALUES (?,?,?,?,?,?,?,?,?,?,?)"
            )
            for (item in batch) {
                stmt.clearBindings()
                stmt.bindLong(1, session.playlistId)
                stmt.bindString(2, item.streamId)
                stmt.bindString(3, item.name)
                stmt.bindString(4, item.category)
                stmt.bindString(5, item.logoUrl)
                stmt.bindString(6, SecurityCrypto.encryptStreamUrl(item.streamUrl))
                stmt.bindString(7, item.mediaType.name)
                stmt.bindString(8, item.containerExtension)
                stmt.bindString(9, item.rating)
                stmt.bindLong(10, if (session.favUrls.contains(item.streamUrl)) 1L else 0L)
                stmt.bindLong(11, session.watchedMap[item.streamUrl] ?: 0L)
                stmt.executeInsert()
                session.insertedCount++
            }
            stmt.close()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getCountsByMediaType(playlistId: Long): Map<MediaType, Int> {
        val result = mutableMapOf(MediaType.LIVE to 0, MediaType.MOVIE to 0, MediaType.SERIES to 0)
        readableDatabase.rawQuery(
            "SELECT media_type, COUNT(*) FROM channels WHERE playlist_id = ? GROUP BY media_type",
            arrayOf(playlistId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val mt = runCatching { MediaType.valueOf(c.getString(0)) }.getOrNull() ?: continue
                result[mt] = c.getInt(1)
            }
        }
        return result
    }

    fun getCategories(playlistId: Long, mediaType: MediaType): List<CategoryInfo> {
        val list = mutableListOf<CategoryInfo>()
        readableDatabase.rawQuery(
            "SELECT category, COUNT(*) FROM channels WHERE playlist_id = ? AND media_type = ? GROUP BY category ORDER BY category ASC",
            arrayOf(playlistId.toString(), mediaType.name)
        ).use { c ->
            while (c.moveToNext()) {
                list.add(CategoryInfo(c.getString(0), c.getInt(1)))
            }
        }
        return list
    }

    fun getFavoriteCount(playlistId: Long, mediaType: MediaType): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM channels WHERE playlist_id = ? AND media_type = ? AND is_favorite = 1",
            arrayOf(playlistId.toString(), mediaType.name)
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getHistoryCount(playlistId: Long, mediaType: MediaType): Int {
        if (mediaType == MediaType.LIVE) return 0
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM channels WHERE playlist_id = ? AND media_type = ? AND last_watched > 0",
            arrayOf(playlistId.toString(), mediaType.name)
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun queryChannels(
        playlistId: Long,
        mediaType: MediaType,
        specialFilter: String,
        searchQuery: String = "",
        lockedCategories: Set<String> = emptySet(),
        allowLockedCategory: Boolean = false,
        limit: Int = 500
    ): List<ChannelItem> {
        val args = mutableListOf<String>()
        val where = StringBuilder("playlist_id = ? AND media_type = ?")
        args.add(playlistId.toString())
        args.add(mediaType.name)

        when (specialFilter) {
            "ALL" -> {}
            "FAV" -> where.append(" AND is_favorite = 1")
            "HIST" -> where.append(" AND last_watched > 0")
            else -> {
                where.append(" AND category = ?")
                args.add(specialFilter)
            }
        }

        if (searchQuery.isNotBlank()) {
            where.append(" AND (name LIKE ? OR category LIKE ?)")
            val like = "%${searchQuery.trim()}%"
            args.add(like)
            args.add(like)
        }

        if (!allowLockedCategory && lockedCategories.isNotEmpty()) {
            val placeholders = lockedCategories.joinToString(",") { "?" }
            where.append(" AND category NOT IN ($placeholders)")
            args.addAll(lockedCategories)
        }

        val orderBy = if (specialFilter == "HIST") "last_watched DESC" else "id ASC"
        val sql = "SELECT * FROM channels WHERE $where ORDER BY $orderBy LIMIT $limit"
        val list = mutableListOf<ChannelItem>()
        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            while (c.moveToNext()) {
                val ch = readChannel(c)
                if (specialFilter == "HIST" && ch.isAdultContent()) continue
                list.add(ch)
            }
        }
        return list
    }

    fun getAllFavoritesAcrossPlaylists(
        lockedByPlaylist: Map<Long, Set<String>>,
        limit: Int = 80
    ): List<TaggedChannel> {
        val list = mutableListOf<TaggedChannel>()
        val sql = """
            SELECT c.*, p.name AS pl_name
            FROM channels c
            JOIN playlists p ON p.id = c.playlist_id
            WHERE c.is_favorite = 1
            ORDER BY c.last_watched DESC, c.id DESC
            LIMIT ?
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf((limit * 4).toString())).use { c ->
            while (c.moveToNext() && list.size < limit) {
                val ch = readChannel(c)
                val plName = c.getString(12) ?: ""
                if (ch.isAdultContent()) continue
                val locked = lockedByPlaylist[ch.playlistId]
                if (locked != null && locked.contains(ch.category)) continue
                list.add(TaggedChannel(ch, plName))
            }
        }
        return list
    }

    /**
     * v2.5.0: Hanya mencatat Lanjutkan Menonton untuk FILM & SERI (Live TV dikecualikan total),
     * dan menyimpan stream_url dalam bentuk terenkripsi AES-256-GCM deterministik.
     */
    fun recordWatchHistory(
        ch: ChannelItem,
        playlistName: String,
        positionMs: Long = -1L,
        durationMs: Long = -1L
    ) {
        if (ch.streamUrl.isBlank() || ch.isAdultContent()) return
        if (ch.mediaType == MediaType.LIVE) return

        val db = writableDatabase
        val encUrl = SecurityCrypto.encryptStreamUrl(ch.streamUrl)
        val now = System.currentTimeMillis()
        var finalPos = if (positionMs >= 0L) positionMs else 0L
        var finalDur = if (durationMs >= 0L) durationMs else 0L

        if (positionMs < 0L || durationMs < 0L) {
            db.rawQuery(
                "SELECT position_ms, duration_ms FROM watch_history WHERE stream_url = ? OR stream_url = ?",
                arrayOf(encUrl, ch.streamUrl)
            ).use { c ->
                if (c.moveToFirst()) {
                    if (positionMs < 0L) finalPos = c.getLong(0)
                    if (durationMs < 0L) finalDur = c.getLong(1)
                }
            }
        }

        // Hapus baris legacy plain-text jika ada agar tidak duplikat setelah enkripsi
        if (encUrl != ch.streamUrl) {
            db.delete("watch_history", "stream_url = ?", arrayOf(ch.streamUrl))
        }

        val cv = ContentValues().apply {
            put("stream_url", encUrl)
            put("playlist_id", ch.playlistId)
            put("playlist_name", playlistName)
            put("channel_id", ch.id)
            put("stream_id", ch.streamId)
            put("name", ch.name)
            put("category", ch.category)
            put("logo_url", ch.logoUrl)
            put("media_type", ch.mediaType.name)
            put("position_ms", finalPos)
            put("duration_ms", finalDur)
            put("last_watched", now)
        }
        db.insertWithOnConflict("watch_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

        if (ch.id > 0L) {
            markWatched(ch.id)
        }
    }

    fun saveWatchProgress(streamUrl: String, positionMs: Long, durationMs: Long) {
        if (streamUrl.isBlank()) return
        val encUrl = SecurityCrypto.encryptStreamUrl(streamUrl)
        val cv = ContentValues().apply {
            put("position_ms", positionMs.coerceAtLeast(0L))
            if (durationMs > 0L) put("duration_ms", durationMs)
            put("last_watched", System.currentTimeMillis())
        }
        val updated = writableDatabase.update("watch_history", cv, "stream_url = ?", arrayOf(encUrl))
        if (updated == 0 && encUrl != streamUrl) {
            writableDatabase.update("watch_history", cv, "stream_url = ?", arrayOf(streamUrl))
        }
    }

    fun getWatchProgress(streamUrl: String): Pair<Long, Long>? {
        if (streamUrl.isBlank()) return null
        val encUrl = SecurityCrypto.encryptStreamUrl(streamUrl)
        readableDatabase.rawQuery(
            "SELECT position_ms, duration_ms FROM watch_history WHERE stream_url = ? OR stream_url = ?",
            arrayOf(encUrl, streamUrl)
        ).use { c ->
            if (c.moveToFirst()) {
                return c.getLong(0) to c.getLong(1)
            }
        }
        return null
    }

    /**
     * v2.5.0: Daftar Lanjutkan Menonton KHUSUS Film & Seri (Live TV dikunci/tidak pernah tampil).
     */
    fun getRecentHistoryAcrossPlaylists(
        lockedByPlaylist: Map<Long, Set<String>>,
        limit: Int = 40
    ): List<TaggedChannel> {
        val list = mutableListOf<TaggedChannel>()
        val sql = """
            SELECT h.stream_url, h.playlist_id, COALESCE(p.name, h.playlist_name) AS pl_name,
                   h.channel_id, h.stream_id, h.name, h.category, h.logo_url, h.media_type,
                   h.position_ms, h.duration_ms, h.last_watched
            FROM watch_history h
            JOIN playlists p ON p.id = h.playlist_id
            WHERE h.last_watched > 0 AND h.media_type IN ('MOVIE', 'SERIES')
            ORDER BY h.last_watched DESC
            LIMIT ?
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf((limit * 4).toString())).use { c ->
            while (c.moveToNext() && list.size < limit) {
                val rawUrl = c.getString(0) ?: continue
                val streamUrl = SecurityCrypto.decryptStreamUrl(rawUrl)
                if (streamUrl.isBlank()) continue
                val plId = c.getLong(1)
                val plName = c.getString(2) ?: ""
                val chName = c.getString(5) ?: ""
                val category = c.getString(6) ?: ""
                if (isAdultCategoryOrTitle(category, chName)) continue
                val locked = lockedByPlaylist[plId]
                if (locked != null && locked.contains(category)) continue
                val mt = runCatching { MediaType.valueOf(c.getString(8)) }.getOrDefault(MediaType.MOVIE)
                if (mt == MediaType.LIVE) continue
                val ch = ChannelItem(
                    id = c.getLong(3),
                    playlistId = plId,
                    streamId = c.getString(4) ?: "",
                    name = chName,
                    category = category,
                    logoUrl = c.getString(7) ?: "",
                    streamUrl = streamUrl,
                    mediaType = mt,
                    lastWatchedAt = c.getLong(11)
                )
                list.add(
                    TaggedChannel(
                        channel = ch,
                        playlistName = plName,
                        positionMs = c.getLong(9),
                        durationMs = c.getLong(10)
                    )
                )
            }
        }
        return list
    }

    fun searchAcrossPlaylists(
        query: String,
        playlistIdFilter: Long?,
        mediaType: MediaType?,
        lockedByPlaylist: Map<Long, Set<String>>,
        limit: Int = 120
    ): List<TaggedChannel> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val args = mutableListOf<String>()
        val where = StringBuilder("(c.name LIKE ? OR c.category LIKE ?)")
        args.add("%$q%")
        args.add("%$q%")

        if (playlistIdFilter != null) {
            where.append(" AND c.playlist_id = ?")
            args.add(playlistIdFilter.toString())
        }
        if (mediaType != null) {
            where.append(" AND c.media_type = ?")
            args.add(mediaType.name)
        }

        val sql = """
            SELECT c.*, p.name AS pl_name
            FROM channels c
            JOIN playlists p ON p.id = c.playlist_id
            WHERE $where
            ORDER BY c.is_favorite DESC, c.last_watched DESC, c.id ASC
            LIMIT ${limit * 2}
        """.trimIndent()

        val list = mutableListOf<TaggedChannel>()
        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            while (c.moveToNext() && list.size < limit) {
                val ch = readChannel(c)
                val plName = c.getString(12) ?: ""
                val locked = lockedByPlaylist[ch.playlistId]
                if (locked != null && locked.contains(ch.category)) continue
                list.add(TaggedChannel(ch, plName))
            }
        }
        return list
    }

    private fun readChannel(c: android.database.Cursor): ChannelItem = ChannelItem(
        id = c.getLong(0),
        playlistId = c.getLong(1),
        streamId = c.getString(2),
        name = c.getString(3),
        category = c.getString(4),
        logoUrl = c.getString(5),
        streamUrl = SecurityCrypto.decryptStreamUrl(c.getString(6) ?: ""),
        mediaType = runCatching { MediaType.valueOf(c.getString(7)) }.getOrDefault(MediaType.LIVE),
        containerExtension = c.getString(8),
        rating = c.getString(9),
        isFavorite = c.getInt(10) == 1,
        lastWatchedAt = c.getLong(11)
    )

    fun getAllFavoriteStreamUrls(): List<String> {
        val list = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT DISTINCT stream_url FROM channels WHERE is_favorite = 1",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val u = SecurityCrypto.decryptStreamUrl(c.getString(0)?.trim() ?: "")
                if (u.isNotEmpty()) list.add(u)
            }
        }
        return list
    }

    fun restoreFavoriteStreamUrls(urls: Set<String>) {
        if (urls.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply { put("is_favorite", 1) }
            for (u in urls) {
                val enc = SecurityCrypto.encryptStreamUrl(u)
                db.update("channels", cv, "stream_url = ? OR stream_url = ?", arrayOf(enc, u))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun toggleFavorite(channelId: Long, newFav: Boolean) {
        val cv = ContentValues().apply { put("is_favorite", if (newFav) 1 else 0) }
        writableDatabase.update("channels", cv, "id = ?", arrayOf(channelId.toString()))
    }

    fun markWatched(channelId: Long) {
        val cv = ContentValues().apply { put("last_watched", System.currentTimeMillis()) }
        writableDatabase.update("channels", cv, "id = ?", arrayOf(channelId.toString()))
    }

    private fun serializeCategoryList(cats: List<String>): String {
        return cats.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
    }

    private fun parseCategoryList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}
