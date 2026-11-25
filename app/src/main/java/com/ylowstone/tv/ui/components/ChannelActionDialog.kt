package com.ylowstone.tv.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.data.DatabaseHelper
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class LinkHealthState { IDLE, CHECKING, ACTIVE, DELAY, DEAD }

data class LinkCheckOutcome(
    val state: LinkHealthState,
    val latencyMs: Long = 0L,
    val detail: String = ""
)

object CustomWatchGroupStore {
    val DEFAULT_GROUPS = listOf("Sports", "Event Pertandingan", "Daftar Tontonan")

    private fun ensureTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_watch_groups (
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
                PRIMARY KEY (group_name, stream_url)
            )
            """.trimIndent()
        )
    }

    fun getAllGroupNames(dbHelper: DatabaseHelper): List<String> {
        val db = dbHelper.writableDatabase
        ensureTable(db)
        val result = LinkedHashSet<String>(DEFAULT_GROUPS)
        db.rawQuery("SELECT DISTINCT group_name FROM custom_watch_groups ORDER BY added_at DESC", null).use { c ->
            while (c.moveToNext()) {
                val g = c.getString(0)?.trim().orEmpty()
                if (g.isNotEmpty()) result.add(g)
            }
        }
        return result.toList()
    }

    fun getGroupsForStream(dbHelper: DatabaseHelper, streamUrl: String): Set<String> {
        if (streamUrl.isBlank()) return emptySet()
        val db = dbHelper.writableDatabase
        ensureTable(db)
        val set = HashSet<String>()
        db.rawQuery(
            "SELECT group_name FROM custom_watch_groups WHERE stream_url = ?",
            arrayOf(streamUrl)
        ).use { c ->
            while (c.moveToNext()) {
                c.getString(0)?.trim()?.takeIf { it.isNotEmpty() }?.let { set.add(it) }
            }
        }
        return set
    }

    fun toggleChannelInGroup(
        dbHelper: DatabaseHelper,
        groupName: String,
        ch: ChannelItem,
        playlistName: String
    ): Boolean {
        val cleanGroup = groupName.trim()
        if (cleanGroup.isEmpty() || ch.streamUrl.isBlank()) return false
        val db = dbHelper.writableDatabase
        ensureTable(db)
        val existing = getGroupsForStream(dbHelper, ch.streamUrl)
        return if (existing.contains(cleanGroup)) {
            db.delete(
                "custom_watch_groups",
                "group_name = ? AND stream_url = ?",
                arrayOf(cleanGroup, ch.streamUrl)
            )
            false
        } else {
            val sql = """
                INSERT OR REPLACE INTO custom_watch_groups
                (group_name, stream_url, playlist_id, playlist_name, channel_id, stream_id, name, category, logo_url, media_type, added_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            db.execSQL(
                sql,
                arrayOf(
                    cleanGroup,
                    ch.streamUrl,
                    ch.playlistId,
                    playlistName,
                    ch.id,
                    ch.streamId,
                    ch.name,
                    ch.category,
                    ch.logoUrl,
                    ch.mediaType.name,
                    System.currentTimeMillis()
                )
            )
            true
        }
    }

    fun getGroupedChannels(dbHelper: DatabaseHelper): Map<String, List<TaggedChannel>> {
        val db = dbHelper.writableDatabase
        ensureTable(db)
        val map = LinkedHashMap<String, MutableList<TaggedChannel>>()
        db.rawQuery(
            """
            SELECT group_name, stream_url, playlist_id, playlist_name, channel_id, stream_id, name, category, logo_url, media_type
            FROM custom_watch_groups
            ORDER BY added_at DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val grp = c.getString(0) ?: continue
                val url = c.getString(1) ?: continue
                val chName = c.getString(6) ?: ""
                val cat = c.getString(7) ?: ""
                if (isAdultCategoryOrTitle(cat, chName)) continue
                val item = ChannelItem(
                    id = c.getLong(4),
                    playlistId = c.getLong(2),
                    streamId = c.getString(5) ?: "",
                    name = chName,
                    category = cat,
                    logoUrl = c.getString(8) ?: "",
                    streamUrl = url,
                    mediaType = runCatching { MediaType.valueOf(c.getString(9)) }.getOrDefault(MediaType.LIVE)
                )
                map.getOrPut(grp) { mutableListOf() }.add(TaggedChannel(item, c.getString(3) ?: "YlowstoneTV"))
            }
        }
        return map
    }
}

suspend fun checkStreamLinkHealth(repo: IptvRepository, streamUrl: String): LinkCheckOutcome =
    withContext(Dispatchers.IO) {
        if (streamUrl.isBlank()) {
            return@withContext LinkCheckOutcome(LinkHealthState.DEAD, 0L, "URL kosong")
        }
        val pingClient = repo.httpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val startNs = System.nanoTime()
        try {
            val req = Request.Builder()
                .url(streamUrl.trim())
                .header("User-Agent", "YlowstoneTV/2.3.0")
                .header("Range", "bytes=0-1024")
                .get()
                .build()
            pingClient.newCall(req).execute().use { resp ->
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                val code = resp.code
                if (code in 200..299 || code == 206 || code in 300..399) {
                    if (elapsedMs <= 1600L) {
                        LinkCheckOutcome(LinkHealthState.ACTIVE, elapsedMs, "Aktif (${elapsedMs} ms)")
                    } else {
                        LinkCheckOutcome(LinkHealthState.DELAY, elapsedMs, "Delay (${elapsedMs} ms)")
                    }
                } else {
                    LinkCheckOutcome(LinkHealthState.DEAD, elapsedMs, "Mati (HTTP $code)")
                }
            }
        } catch (e: Exception) {
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            LinkCheckOutcome(LinkHealthState.DEAD, elapsedMs, "Mati (Timeout / Error)")
        }
    }

@Composable
fun ChannelLongPressActionDialog(
    channel: ChannelItem,
    playlistName: String,
    repo: IptvRepository,
    onDismiss: () -> Unit,
    onGroupsUpdated: () -> Unit = {}
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var linkStatus by remember(channel.streamUrl) {
        mutableStateOf(LinkCheckOutcome(LinkHealthState.IDLE))
    }
    var showGroupPicker by remember { mutableStateOf(false) }
    var availableGroups by remember { mutableStateOf(CustomWatchGroupStore.getAllGroupNames(repo.db)) }
    var assignedGroups by remember(channel.streamUrl) {
        mutableStateOf(CustomWatchGroupStore.getGroupsForStream(repo.db, channel.streamUrl))
    }
    var newGroupName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChannelLogoBox(channel.logoUrl, 36.dp, corner = 8.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = c.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${channel.mediaType.name} • ${channel.category}",
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Pilihan 1: Salin Link Tunggal ke Clipboard
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surfaceElevated)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("YlowstoneTV Link", channel.streamUrl))
                            Toast.makeText(context, "Link disalin ke papan klip", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("1. Salin Link", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = channel.streamUrl,
                            color = c.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Pilihan 2: Cek Status Link (Aktif / Delay / Mati)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surfaceElevated)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                        .clickable(enabled = linkStatus.state != LinkHealthState.CHECKING) {
                            linkStatus = LinkCheckOutcome(LinkHealthState.CHECKING)
                            scope.launch {
                                linkStatus = checkStreamLinkHealth(repo, channel.streamUrl)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.NetworkCheck, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                        Column {
                            Text("2. Cek Status Link", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = when (linkStatus.state) {
                                    LinkHealthState.IDLE -> "Ketuk untuk cek Aktif / Delay / Mati"
                                    LinkHealthState.CHECKING -> "Memeriksa respon server..."
                                    else -> linkStatus.detail
                                },
                                color = c.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    when (linkStatus.state) {
                        LinkHealthState.CHECKING -> {
                            CircularProgressIndicator(
                                color = c.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        LinkHealthState.ACTIVE -> {
                            StatusBadgePill("AKTIF", Color(0xFF22C55E))
                        }
                        LinkHealthState.DELAY -> {
                            StatusBadgePill("DELAY", Color(0xFFF59E0B))
                        }
                        LinkHealthState.DEAD -> {
                            StatusBadgePill("MATI", Color(0xFFEF4444))
                        }
                        LinkHealthState.IDLE -> {}
                    }
                }

                // Pilihan 3: Kelompokkan Kategori Tontonan (Sports, Event Pertandingan, Daftar Tontonan, dll)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surfaceElevated)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                        .clickable { showGroupPicker = !showGroupPicker }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Bookmarks, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                            Column {
                                Text("3. Kategori Tontonan", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (assignedGroups.isEmpty()) "Sports, Event Pertandingan, Daftar Tontonan..."
                                    else "Masuk grup: ${assignedGroups.joinToString(", ")}",
                                    color = if (assignedGroups.isEmpty()) c.textSecondary else c.accent,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            if (showGroupPicker) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = c.textSecondary
                        )
                    }

                    if (showGroupPicker) {
                        HorizontalDivider(color = c.glassBorder)
                        availableGroups.forEach { grp ->
                            val checked = assignedGroups.contains(grp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (checked) c.accentContainer else Color.Transparent)
                                    .clickable {
                                        val added = CustomWatchGroupStore.toggleChannelInGroup(repo.db, grp, channel, playlistName)
                                        assignedGroups = CustomWatchGroupStore.getGroupsForStream(repo.db, channel.streamUrl)
                                        onGroupsUpdated()
                                        Toast.makeText(
                                            context,
                                            if (added) "Ditambahkan ke $grp" else "Dihapus dari $grp",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = grp,
                                    color = if (checked) c.accent else c.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                                )
                                Icon(
                                    if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (checked) c.accent else c.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = newGroupName,
                                onValueChange = { newGroupName = it },
                                placeholder = { Text("Grup baru...", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val clean = newGroupName.trim()
                                    if (clean.isNotEmpty()) {
                                        CustomWatchGroupStore.toggleChannelInGroup(repo.db, clean, channel, playlistName)
                                        availableGroups = CustomWatchGroupStore.getAllGroupNames(repo.db)
                                        assignedGroups = CustomWatchGroupStore.getGroupsForStream(repo.db, channel.streamUrl)
                                        newGroupName = ""
                                        onGroupsUpdated()
                                        Toast.makeText(context, "Ditambahkan ke $clean", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(c.accent)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add", tint = c.onAccent)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = c.accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun StatusBadgePill(label: String, badgeColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeColor.copy(alpha = 0.18f))
            .border(1.dp, badgeColor.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = badgeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}
