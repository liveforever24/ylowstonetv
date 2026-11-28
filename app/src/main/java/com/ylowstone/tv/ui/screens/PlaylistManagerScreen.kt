package com.ylowstone.tv.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.R
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.model.PlaylistHealthResult
import com.ylowstone.tv.model.PlaylistHealthState
import kotlinx.coroutines.launch
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.LoadingAnimStyle
import com.ylowstone.tv.model.PlaylistProfile
import com.ylowstone.tv.model.PlaylistType
import com.ylowstone.tv.model.StreamFormat
import com.ylowstone.tv.ui.components.BoomerangLoadingIndicator
import com.ylowstone.tv.ui.components.CinematicCollapsingListScreen
import com.ylowstone.tv.ui.components.ProfileYBadgeButton
import com.ylowstone.tv.ui.components.QuickChipSpec
import com.ylowstone.tv.ui.components.YlowInterDisplay
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors

private val avatarColors = listOf(
    Color(0xFFFA2D48),
    Color(0xFF0A84FF),
    Color(0xFF30D158),
    Color(0xFFFF9F0A),
    Color(0xFFBF5AF2),
    Color(0xFF5AC8FA)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistManagerScreen(
    playlists: List<PlaylistProfile>,
    adminPlaylistIds: Set<Long>,
    isLoggedIn: Boolean,
    isFullAdmin: Boolean = false,
    streamFormat: StreamFormat,
    strings: AppStrings,
    isTvMode: Boolean,
    repo: IptvRepository? = null,
    isLoading: Boolean,
    loadingMessage: String,
    refreshingPlaylistName: String = "",
    loadingAnimStyle: LoadingAnimStyle = LoadingAnimStyle.CHROMA_BOOMERANG,
    onOpenPlaylist: (PlaylistProfile) -> Unit,
    onSavePlaylist: (PlaylistProfile, Uri?) -> Unit,
    onRefreshPlaylist: (PlaylistProfile) -> Unit,
    onDeletePlaylist: (PlaylistProfile) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showEditor by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<PlaylistProfile?>(null) }
    var actionTarget by remember { mutableStateOf<PlaylistProfile?>(null) }
    var inspectLinkTarget by remember { mutableStateOf<PlaylistProfile?>(null) }
    val scope = rememberCoroutineScope()
    var healthTarget by remember { mutableStateOf<PlaylistProfile?>(null) }
    var isCheckingHealth by remember { mutableStateOf(false) }
    var healthResult by remember { mutableStateOf<PlaylistHealthResult?>(null) }

    fun triggerHealthCheck(target: PlaylistProfile) {
        healthTarget = target
        healthResult = null
        isCheckingHealth = true
        scope.launch {
            val res = repo?.checkPlaylistHealth(target)
            healthResult = res
            isCheckingHealth = false
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var sortAz by remember { mutableStateOf(false) }
    val isIndonesian = strings.watchHistory.startsWith("Riwayat", ignoreCase = true)
    val filteredPlaylists = remember(playlists, searchQuery, sortAz) {
        val q = searchQuery.trim()
        val f = if (q.isEmpty()) playlists else playlists.filter { it.name.contains(q, ignoreCase = true) }
        if (sortAz) f.sortedBy { it.name.lowercase() } else f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
    ) {
        CinematicCollapsingListScreen(
            title = strings.choosePlaylist,
            subtitleInfo = "ylowstone • ${filteredPlaylists.size} playlist",
            coverRes = R.drawable.cover_home_tv,
            headerGradientColor = c.headerGradNeutral,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            searchPlaceholder = if (isIndonesian) "Cari di daftar putar..." else "Search in playlist...",
            isTvMode = isTvMode,
            sortLabel = if (isIndonesian) "Urut" else "Sort",
            sortActive = sortAz,
            onSortClick = { sortAz = !sortAz },
            quickChips = buildList {
                if (!isLoggedIn && !isFullAdmin) {
                    add(QuickChipSpec("admin_login", strings.adminMenuLogin, true) {
                        onOpenLogin()
                    })
                }
            },
            playButtonIcon = if (filteredPlaylists.isEmpty()) Icons.Rounded.Add else Icons.Rounded.PlayArrow,
            onPlayAction = {
                val first = filteredPlaylists.firstOrNull()
                if (first != null) onOpenPlaylist(first)
                else { editingProfile = null; showEditor = true }
            },
            topRightAction = {
                ProfileYBadgeButton(isTvMode = isTvMode, onClick = onOpenSettings)
            }
        ) {
            if (filteredPlaylists.isEmpty()) {
                item(key = "empty_pl") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(c.accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = c.accent, modifier = Modifier.size(34.dp))
                        }
                        Text(strings.noPlaylistYet, color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(strings.noPlaylistDesc, color = c.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onOpenLogin,
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Rounded.Login, contentDescription = null, tint = c.onAccent)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.adminMenuLogin, color = c.onAccent, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { editingProfile = null; showEditor = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.accent)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.addPlaylist, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                items(filteredPlaylists, key = { it.id }) { profile ->
                    val tint = avatarColors[profile.colorIndex % avatarColors.size]
                    val typeLabel = when (profile.type) {
                        PlaylistType.XTREAM -> strings.xtreamLoader
                        PlaylistType.M3U -> strings.m3uLoader
                        PlaylistType.DIRECT -> strings.directLoader
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusBorder(isTvMode, RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = { onOpenPlaylist(profile) },
                                onLongClick = { actionTarget = profile }
                            )
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.42f))))
                                .border(0.8.dp, c.accent.copy(alpha = 0.30f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile.name.take(2).uppercase(),
                                style = TextStyle(
                                    fontFamily = YlowInterDisplay,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = TextStyle(
                                    fontFamily = YlowInterDisplay,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = c.textPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = typeLabel,
                                style = TextStyle(
                                    fontFamily = YlowInterDisplay,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 11.sp,
                                    color = c.accent
                                ),
                                maxLines = 1
                            )
                        }
                        if (repo != null) {
                            IconButton(
                                onClick = { triggerHealthCheck(profile) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NetworkCheck,
                                    contentDescription = if (isIndonesian) "Cek Status" else "Check Status",
                                    tint = c.accent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { actionTarget = profile },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Options",
                                tint = c.textSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                item(key = "pl_hint") {
                    Text(
                        text = strings.longPressHint,
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // Tombol Tambah Daftar Putar (+) mengambang di pojok kanan bawah
        FloatingActionButton(
            onClick = { editingProfile = null; showEditor = true },
            shape = CircleShape,
            containerColor = c.accent,
            contentColor = c.onAccent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 22.dp)
                .size(56.dp)
                .tvFocusBorder(isTvMode, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = strings.addPlaylist,
                tint = c.onAccent,
                modifier = Modifier.size(28.dp)
            )
        }

        // Pop-Up Indikator Pembaruan Playlist Terpilih (v2.5.3)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.76f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 250.dp, max = 330.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.surface)
                        .border(1.dp, c.accent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BoomerangLoadingIndicator(style = loadingAnimStyle, size = 66.dp)
                    if (refreshingPlaylistName.isNotBlank()) {
                        Text(
                            text = if (isIndonesian) "Memperbarui $refreshingPlaylistName" else "Updating $refreshingPlaylistName",
                            color = c.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = loadingMessage.ifBlank { strings.loadingPlaylist },
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // 3-dot / Long-press action menu dialog (v2.4.7: Edit & Tampilkan Link hanya untuk Mode Admin Penuh)
    actionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            containerColor = c.surface,
            title = { Text(target.name, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isFullAdmin) {
                        ActionRow(Icons.Rounded.Edit, strings.editPlaylist) {
                            actionTarget = null
                            editingProfile = target
                            showEditor = true
                        }
                        ActionRow(Icons.Rounded.Link, strings.showLink) {
                            actionTarget = null
                            inspectLinkTarget = target
                        }
                    } else {
                        Text(
                            text = strings.adminProtectedPlaylist,
                            color = c.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    if (repo != null) {
                        ActionRow(Icons.Rounded.NetworkCheck, if (isIndonesian) "Cek Status Playlist" else "Check Playlist Status", tint = c.accent) {
                            actionTarget = null
                            triggerHealthCheck(target)
                        }
                    }
                    ActionRow(Icons.Rounded.Refresh, strings.refresh) {
                        actionTarget = null
                        onRefreshPlaylist(target)
                    }
                    ActionRow(Icons.Rounded.Delete, strings.delete, tint = Color(0xFFFF453A)) {
                        actionTarget = null
                        onDeletePlaylist(target)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTarget = null }) {
                    Text(strings.cancel, color = c.textSecondary)
                }
            }
        )
    }

    // Show & Copy Playlist Link dialog — Khusus Mode Admin Penuh
    if (isFullAdmin) {
        inspectLinkTarget?.let { target ->
            var reveal by remember { mutableStateOf(false) }
            val displayLink = if (reveal) target.buildShareableLink(streamFormat) else target.buildMaskedLink(streamFormat)
            AlertDialog(
                onDismissRequest = { inspectLinkTarget = null },
                containerColor = c.surface,
                title = { Text(strings.showLink, color = c.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.surfaceElevated)
                                .padding(12.dp)
                        ) {
                            Text(displayLink, color = c.textPrimary, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { reveal = !reveal },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (reveal) "Hide" else "Show", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    clipboard.setText(AnnotatedString(target.buildShareableLink(streamFormat)))
                                    Toast.makeText(context, strings.copied, Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.copyLink, color = c.onAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { inspectLinkTarget = null }) {
                        Text("OK", color = c.accent)
                    }
                }
            )
        }
    }


    // Kartu Pop-Up Status Playlist Akurat (v2.5.3)
    healthTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isCheckingHealth) healthTarget = null },
            containerColor = c.surface,
            shape = RoundedCornerShape(22.dp),
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = target.name,
                        color = c.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isIndonesian) "Status Playlist" else "Playlist Status",
                        color = c.textSecondary,
                        fontSize = 11.sp
                    )
                }
            },
            text = {
                if (isCheckingHealth) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BoomerangLoadingIndicator(style = loadingAnimStyle, size = 54.dp)
                        Text(
                            text = if (isIndonesian) "Memeriksa status URL & server..." else "Checking URL & server status...",
                            color = c.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    val res = healthResult
                    if (res == null) {
                        Text(
                            text = if (isIndonesian) "Gagal memeriksa status playlist." else "Unable to verify playlist status.",
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp
                        )
                    } else {
                        val (badgeColor, badgeLabel, badgeIcon) = when (res.state) {
                            PlaylistHealthState.ACTIVE -> Triple(Color(0xFF22C55E), if (isIndonesian) "Aktif" else "Active", Icons.Rounded.CheckCircle)
                            PlaylistHealthState.DELAY -> Triple(Color(0xFFF59E0B), if (isIndonesian) "Delay / Lemot" else "Slow / Delay", Icons.Rounded.Schedule)
                            PlaylistHealthState.EXPIRED -> Triple(Color(0xFFFB7185), if (isIndonesian) "Kadaluarsa" else "Expired", Icons.Rounded.EventBusy)
                            PlaylistHealthState.INACTIVE -> Triple(Color(0xFFEF4444), if (isIndonesian) "Tidak Aktif" else "Inactive", Icons.Rounded.Block)
                            PlaylistHealthState.DOWN -> Triple(Color(0xFFEF4444), if (isIndonesian) "Down / Mati" else "Down / Offline", Icons.Rounded.CloudOff)
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Badge Status + Latensi
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(badgeColor.copy(alpha = 0.14f))
                                    .border(1.dp, badgeColor.copy(alpha = 0.50f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
                                    Text(badgeLabel, color = badgeColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                if (res.latencyMs > 0L) {
                                    Text("${res.latencyMs} ms", color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Jumlah Channel TV, Film, Seri
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple(strings.liveTv, res.liveCount, Icons.Rounded.LiveTv),
                                    Triple(strings.movies, res.movieCount, Icons.Rounded.Movie),
                                    Triple(strings.series, res.seriesCount, Icons.Rounded.Tv)
                                ).forEach { (lbl, cnt, ic) ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(c.surfaceElevated)
                                            .border(0.8.dp, c.glassBorder, RoundedCornerShape(12.dp))
                                            .padding(vertical = 10.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(ic, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                                        Text("$cnt", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(lbl, color = c.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }

                            // Info Masa Aktif / Kuota (jika Xtream)
                            if (res.expiryDateText.isNotBlank() || res.maxConnections > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (res.expiryDateText.isNotBlank()) {
                                        Text(
                                            text = (if (isIndonesian) "Aktif s/d: " else "Expires: ") + res.expiryDateText,
                                            color = c.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (res.maxConnections > 0) {
                                        Text(
                                            text = (if (isIndonesian) "Koneksi: " else "Conn: ") + "${res.activeConnections.coerceAtLeast(0)}/${res.maxConnections}",
                                            color = c.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // Saran HANYA jika Delay/Lemot, atau detail singkat jika bermasalah (Jika Aktif/Lancar -> TANPA komentar!)
                            if (res.state == PlaylistHealthState.DELAY) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x1FF59E0B))
                                        .border(0.9.dp, Color(0x66F59E0B), RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (res.detail.isNotBlank()) {
                                        Text(res.detail, color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = if (isIndonesian) {
                                            "Saran: Aktifkan DNS-over-HTTPS (DoH) dan pilih Profil Buffer Anti-Buffer di Pengaturan, atau pindah ke jaringan lebih stabil."
                                        } else {
                                            "Tip: Enable DNS-over-HTTPS (DoH) and switch to the Anti-Buffer profile in Settings, or use a faster network."
                                        },
                                        color = c.textPrimary.copy(alpha = 0.88f),
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            } else if (res.state != PlaylistHealthState.ACTIVE && res.detail.isNotBlank()) {
                                Text(
                                    text = res.detail,
                                    color = Color(0xFFFF8A80),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isCheckingHealth) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { triggerHealthCheck(target) }) {
                            Text(if (isIndonesian) "Cek Ulang" else "Recheck", color = c.textSecondary)
                        }
                        TextButton(onClick = { healthTarget = null }) {
                            Text(strings.closeBtn, color = c.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        )
    }

    if (showEditor) {
        PlaylistEditorDialog(
            existing = editingProfile,
            nextColorIndex = playlists.size % avatarColors.size,
            strings = strings,
            onDismiss = { showEditor = false },
            onSave = { profile, uri ->
                showEditor = false
                onSavePlaylist(profile, uri)
            }
        )
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = LocalYlowColors.current.textPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlaylistEditorDialog(
    existing: PlaylistProfile?,
    nextColorIndex: Int,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSave: (PlaylistProfile, Uri?) -> Unit
) {
    val c = LocalYlowColors.current
    var type by remember { mutableStateOf(existing?.type ?: PlaylistType.XTREAM) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var userAgent by remember { mutableStateOf(existing?.userAgent ?: "") }
    var showPass by remember { mutableStateOf(false) }
    var pickedFileUri by remember { mutableStateOf<Uri?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedFileUri = uri
            url = uri.toString()
            if (name.isBlank()) name = "Local M3U"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Text(
                if (existing == null) strings.addPlaylist else strings.editPlaylist,
                color = c.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        PlaylistType.XTREAM to strings.xtreamLoader,
                        PlaylistType.M3U to strings.m3uLoader,
                        PlaylistType.DIRECT to strings.directLoader
                    ).forEach { (loaderType, title) ->
                        val active = type == loaderType
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) c.accent else c.surfaceElevated)
                                .clickable { type = loaderType }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (active) c.onAccent else c.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.playlistName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                when (type) {
                    PlaylistType.XTREAM -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(strings.serverUrl) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(strings.username) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(strings.password) },
                            singleLine = true,
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPass = !showPass }) {
                                    Icon(
                                        if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    PlaylistType.M3U -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it; pickedFileUri = null },
                            label = { Text(strings.m3uUrlOrFile) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.pickM3uFile)
                        }
                    }

                    PlaylistType.DIRECT -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(strings.directStreamUrl) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text(strings.customUserAgent) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    val cleanUrl = url.trim().let {
                        if (type == PlaylistType.XTREAM && !it.startsWith("http", ignoreCase = true)) "http://$it" else it
                    }
                    onSave(
                        PlaylistProfile(
                            id = existing?.id ?: 0L,
                            name = name.trim(),
                            type = type,
                            url = cleanUrl,
                            username = username.trim(),
                            password = password.trim(),
                            userAgent = userAgent.trim(),
                            colorIndex = existing?.colorIndex ?: nextColorIndex,
                            lastUpdated = existing?.lastUpdated ?: 0L
                        ),
                        pickedFileUri
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)
            ) {
                Text(strings.saveAndLoad, color = c.onAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = c.textSecondary)
            }
        }
    )
}
