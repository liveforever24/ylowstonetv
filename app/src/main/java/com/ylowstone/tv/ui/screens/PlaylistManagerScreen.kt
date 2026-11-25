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
import com.ylowstone.tv.ui.components.BoomerangLoadingIndicator
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
    loadingAnimStyle: LoadingAnimStyle = LoadingAnimStyle.MOTOROLA_BOOMERANG,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Minimalist clean top header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    YlowstoneLogoBadge(size = 40.dp)
                    Column {
                        Text("YlowstoneTV", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text(strings.choosePlaylist, color = c.textSecondary, fontSize = 12.sp)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isLoggedIn) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(c.accent.copy(alpha = 0.16f))
                                .border(1.dp, c.accent.copy(alpha = 0.45f), CircleShape)
                                .glossyPressPill(18f)
                                .clickable(onClick = onOpenLogin)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Rounded.Login, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                            Text(strings.adminMenuLogin, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    ProfileYBadgeButton(
                        isTvMode = isTvMode,
                        onClick = onOpenSettings
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(c.accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = c.accent, modifier = Modifier.size(38.dp))
                        }
                        Text(strings.noPlaylistYet, color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(strings.noPlaylistDesc, color = c.textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onOpenLogin,
                                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Login, contentDescription = null, tint = c.onAccent)
                                Spacer(Modifier.width(8.dp))
                                Text(strings.adminMenuLogin, color = c.onAccent, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { editingProfile = null; showEditor = true },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = c.textPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(strings.addPlaylist, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (isTvMode) 170.dp else 150.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(playlists, key = { it.id }) { profile ->
                        val tint = avatarColors[profile.colorIndex % avatarColors.size]
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(c.surface)
                                .border(1.dp, c.glassBorder, RoundedCornerShape(22.dp))
                                .tvFocusBorder(isTvMode, RoundedCornerShape(22.dp))
                                .glossyPressPill(22f)
                                .combinedClickable(
                                    onClick = { onOpenPlaylist(profile) },
                                    onLongClick = { actionTarget = profile }
                                )
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // v2.3.1: Ikon refresh di kartu dihapus; aksi Perbarui murni di menu titik tiga (⋮)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .align(Alignment.Center)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.55f)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = profile.name.take(2).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                IconButton(
                                    onClick = { actionTarget = profile },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                ) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = profile.name,
                                color = c.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = when (profile.type) {
                                    PlaylistType.XTREAM -> strings.xtreamLoader
                                    PlaylistType.M3U -> strings.m3uLoader
                                    PlaylistType.DIRECT -> strings.directLoader
                                },
                                color = tint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Add Playlist tile
                    item {
                        Column(
                            modifier = Modifier
                                .height(136.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(c.accent.copy(alpha = 0.10f))
                                .border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                                .tvFocusBorder(isTvMode, RoundedCornerShape(22.dp))
                                .glossyPressPill(22f)
                                .clickable { editingProfile = null; showEditor = true }
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(c.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = strings.addPlaylist, tint = c.onAccent, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(strings.addPlaylist, color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.longPressHint,
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.surface)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BoomerangLoadingIndicator(style = loadingAnimStyle, size = 62.dp)
                    Text(
                        text = loadingMessage.ifBlank { strings.loadingPlaylist },
                        color = c.textPrimary,
                        fontSize = 13.sp
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
