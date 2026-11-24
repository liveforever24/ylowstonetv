package com.ylowstone.tv.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ylowstone.tv.model.*

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "ylowstone_tv.db", null, 3) {

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

        db.execSQL("CREATE INDEX idx_channels_main ON channels(playlist_id, media_type, category)")
        db.execSQL("CREATE INDEX idx_channels_name ON channels(playlist_id, name)")
        db.execSQL("CREATE INDEX idx_channels_fav ON channels(playlist_id, media_type, is_favorite)")
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
    }

    fun getAllPlaylists(): List<PlaylistProfile> {
        val list = mutableListOf<PlaylistProfile>()
        readableDatabase.rawQuery("SELECT * FROM playlists ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    PlaylistProfile(
                        id = c.getLong(0),
                        name = c.getString(1),
                        type = runCatching { PlaylistType.valueOf(c.getString(2)) }.getOrDefault(PlaylistType.M3U),
                        url = c.getString(3),
                        username = c.getString(4),
                        password = c.getString(5),
                        userAgent = c.getString(6),
                        colorIndex = c.getInt(7),
                        lastUpdated = c.getLong(8)
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
            put("url", p.url)
            put("username", p.username)
            put("password", p.password)
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
            writableDatabase.delete("playlists", "id = ?", arrayOf(playlistId.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun replaceChannels(playlistId: Long, items: List<ChannelItem>) {
        val db = writableDatabase
        val favUrls = HashSet<String>()
        db.rawQuery(
            "SELECT stream_url FROM channels WHERE playlist_id = ? AND is_favorite = 1",
            arrayOf(playlistId.toString())
        ).use { c ->
            while (c.moveToNext()) favUrls.add(c.getString(0))
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
                stmt.bindString(6, item.streamUrl)
                stmt.bindString(7, item.mediaType.name)
                stmt.bindString(8, item.containerExtension)
                stmt.bindString(9, item.rating)
                stmt.bindLong(10, if (favUrls.contains(item.streamUrl)) 1L else 0L)
                stmt.bindLong(11, 0L)
                stmt.executeInsert()
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
                val type = runCatching { MediaType.valueOf(c.getString(0)) }.getOrNull()
                if (type != null) result[type] = c.getInt(1)
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
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM channels WHERE playlist_id = ? AND media_type = ? AND last_watched > 0",
            arrayOf(playlistId.toString(), mediaType.name)
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun queryChannels(
        playlistId: Long,
        mediaType: MediaType?,
        specialFilter: String,
        searchQuery: String,
        lockedCategories: Set<String>,
        allowLockedCategory: Boolean = false,
        limit: Int = 500
    ): List<ChannelItem> {
        val args = mutableListOf<String>()
        val where = StringBuilder("playlist_id = ?")
        args.add(playlistId.toString())

        if (mediaType != null) {
            where.append(" AND media_type = ?")
            args.add(mediaType.name)
        }

        val trimmedSearch = searchQuery.trim()
        when (specialFilter) {
            "ALL" -> {}
            "FAV" -> where.append(" AND is_favorite = 1")
            "HIST" -> where.append(" AND last_watched > 0")
            else -> {
                where.append(" AND category = ?")
                args.add(specialFilter)
            }
        }

        if (trimmedSearch.isNotEmpty()) {
            where.append(" AND (name LIKE ? OR category LIKE ?)")
            args.add("%$trimmedSearch%")
            args.add("%$trimmedSearch%")
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
                list.add(readChannel(c))
            }
        }
        return list
    }

    // ---- Cross-Playlist Home & Search Queries (Respecting PIN-locked categories!) ----
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
        readableDatabase.rawQuery(sql, arrayOf((limit * 2).toString())).use { c ->
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

    fun getRecentHistoryAcrossPlaylists(
        lockedByPlaylist: Map<Long, Set<String>>,
        limit: Int = 40
    ): List<TaggedChannel> {
        val list = mutableListOf<TaggedChannel>()
        val sql = """
            SELECT c.*, p.name AS pl_name
            FROM channels c
            JOIN playlists p ON p.id = c.playlist_id
            WHERE c.last_watched > 0
            ORDER BY c.last_watched DESC
            LIMIT ?
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf((limit * 2).toString())).use { c ->
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

    fun searchAcrossPlaylists(
        query: String,
        playlistIdFilter: Long?, // null = all playlists
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
        streamUrl = c.getString(6),
        mediaType = runCatching { MediaType.valueOf(c.getString(7)) }.getOrDefault(MediaType.LIVE),
        containerExtension = c.getString(8),
        rating = c.getString(9),
        isFavorite = c.getInt(10) == 1,
        lastWatchedAt = c.getLong(11)
    )

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
