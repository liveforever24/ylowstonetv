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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.PlaylistProfile
import com.ylowstone.tv.model.PlaylistType
import com.ylowstone.tv.model.LoadingAnimStyle
import com.ylowstone.tv.model.StreamFormat
import androidx.compose.foundation.lazy.items
import com.ylowstone.tv.R
import com.ylowstone.tv.ui.components.BoomerangLoadingIndicator
import com.ylowstone.tv.ui.components.CinematicCollapsingListScreen
import com.ylowstone.tv.ui.components.CinematicNoirTokens
import com.ylowstone.tv.ui.components.QuickChipSpec
import com.ylowstone.tv.ui.components.YlowInterDisplay
import androidx.compose.ui.text.TextStyle
import com.ylowstone.tv.ui.components.ProfileYBadgeButton
import com.ylowstone.tv.ui.components.YlowstoneLogoBadge
import com.ylowstone.tv.ui.components.glossyPressPill
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
    streamFormat: StreamFormat,
    strings: AppStrings,
    isTvMode: Boolean,
    isLoading: Boolean,
    loadingMessage: String,
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
            .background(CinematicNoirTokens.BgAmoled)
            .systemBarsPadding()
    ) {
        CinematicCollapsingListScreen(
            title = strings.choosePlaylist,
            subtitleInfo = "ylowstone • ${filteredPlaylists.size} playlist",
            coverRes = R.drawable.cover_yellowstone_tv,
            headerGradientColor = CinematicNoirTokens.GradNeutral,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            searchPlaceholder = if (isIndonesian) "Cari di daftar putar..." else "Search in playlist...",
            isTvMode = isTvMode,
            sortLabel = if (isIndonesian) "Urut" else "Sort",
            sortActive = sortAz,
            onSortClick = { sortAz = !sortAz },
            quickChips = buildList {
                add(QuickChipSpec("add_pl", "+ ${strings.addPlaylist}", false) {
                    editingProfile = null; showEditor = true
                })
                if (!isLoggedIn) {
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
                                .background(CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = CinematicNoirTokens.ChampagnePlay, modifier = Modifier.size(34.dp))
                        }
                        Text(strings.noPlaylistYet, color = CinematicNoirTokens.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(strings.noPlaylistDesc, color = CinematicNoirTokens.TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onOpenLogin,
                            colors = ButtonDefaults.buttonColors(containerColor = CinematicNoirTokens.ChampagnePlay, contentColor = Color(0xFF14110D)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Rounded.Login, contentDescription = null, tint = Color(0xFF14110D))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.adminMenuLogin, color = Color(0xFF14110D), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { editingProfile = null; showEditor = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = CinematicNoirTokens.ChampagneHighlight)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.addPlaylist, color = CinematicNoirTokens.TextPrimary, fontWeight = FontWeight.SemiBold)
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
                                .border(0.8.dp, CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.30f), RoundedCornerShape(11.dp)),
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
                                    color = CinematicNoirTokens.TextPrimary
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
                                    color = CinematicNoirTokens.ChampagneAccent
                                ),
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = { actionTarget = profile },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Options",
                                tint = CinematicNoirTokens.TextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                item(key = "pl_hint") {
                    Text(
                        text = strings.longPressHint,
                        color = CinematicNoirTokens.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // Loading overlay (v2.3.3: Bumerang melayang bersih tanpa kotak hitam)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.76f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BoomerangLoadingIndicator(style = loadingAnimStyle, size = 76.dp)
                    Text(
                        text = loadingMessage.ifBlank { strings.loadingPlaylist },
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // 3-dot / Long-press action menu dialog (Edit, Tampilkan Link, Perbarui, Hapus)
    actionTarget?.let { target ->
        val isAdminProtected = adminPlaylistIds.contains(target.id)
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            containerColor = c.surface,
            title = { Text(target.name, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isAdminProtected) {
                        Text(
                            text = strings.adminProtectedPlaylist,
                            color = c.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else {
                        ActionRow(Icons.Rounded.Edit, strings.editPlaylist) {
                            actionTarget = null
                            editingProfile = target
                            showEditor = true
                        }
                        ActionRow(Icons.Rounded.Link, strings.showLink) {
                            actionTarget = null
                            inspectLinkTarget = target
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

    // Show & Copy Playlist Link dialog (with password reveal toggle)
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
