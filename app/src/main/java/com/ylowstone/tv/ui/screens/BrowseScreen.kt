package com.ylowstone.tv.ui.screens

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.components.PinDialog
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.launch

@kotlin.OptIn(ExperimentalFoundationApi::class)
@OptIn(UnstableApi::class)
@Composable
fun BrowseScreen(
    playlist: PlaylistProfile,
    mediaType: MediaType,
    repo: IptvRepository,
    lockedCategories: Set<String>,
    pinCode: String,
    strings: AppStrings,
    isTvMode: Boolean,
    onToggleLockCategory: (String) -> Unit,
    onLaunchFullscreen: (ChannelItem, List<ChannelItem>) -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCategory by remember(playlist.id, mediaType) { mutableStateOf("ALL") }
    var searchQuery by remember(playlist.id, mediaType) { mutableStateOf("") }
    var unlockedTempCategories by remember(playlist.id) { mutableStateOf<Set<String>>(emptySet()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val categories = remember(playlist.id, mediaType, refreshTrigger) {
        repo.db.getCategories(playlist.id, mediaType)
    }
    val allCount = remember(categories) { categories.sumOf { it.count } }
    val favCount = remember(playlist.id, mediaType, refreshTrigger) {
        repo.db.getFavoriteCount(playlist.id, mediaType)
    }
    val histCount = remember(playlist.id, mediaType, refreshTrigger) {
        repo.db.getHistoryCount(playlist.id, mediaType)
    }

    val isSelectedLocked = lockedCategories.contains(selectedCategory) && !unlockedTempCategories.contains(selectedCategory)
    val channels = remember(playlist.id, mediaType, selectedCategory, searchQuery, lockedCategories, unlockedTempCategories, refreshTrigger) {
        if (isSelectedLocked) emptyList()
        else repo.db.queryChannels(
            playlistId = playlist.id,
            mediaType = mediaType,
            specialFilter = selectedCategory,
            searchQuery = searchQuery,
            lockedCategories = lockedCategories - unlockedTempCategories,
            allowLockedCategory = unlockedTempCategories.contains(selectedCategory),
            limit = 600
        )
    }

    // Mini Preview Player for Live TV split-screen
    var previewChannel by remember(playlist.id, mediaType) { mutableStateOf<ChannelItem?>(null) }
    val previewPlayer = remember(playlist.id, mediaType) {
        if (mediaType == MediaType.LIVE) {
            val dsFactory = OkHttpDataSource.Factory(repo.httpClient)
                .setUserAgent(playlist.userAgent.ifBlank { "YlowstoneTV/1.0" })
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
                .build()
        } else null
    }

    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer?.release() }
    }

    LaunchedEffect(previewChannel) {
        val ch = previewChannel
        if (ch != null && previewPlayer != null) {
            previewPlayer.setMediaItem(MediaItem.fromUri(ch.streamUrl))
            previewPlayer.prepare()
            previewPlayer.playWhenReady = true
            repo.db.markWatched(ch.id)
        }
    }

    // PIN Lock/Unlock dialog states
    var pinActionCategory by remember { mutableStateOf<String?>(null) }
    var pinActionMode by remember { mutableStateOf("") } // "TOGGLE_LOCK" or "OPEN_LOCKED"

    // Series Episodes dialog
    var activeSeries by remember { mutableStateOf<ChannelItem?>(null) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var loadingEpisodes by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 78.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- LEFT SIDEBAR: Search + Categories (with Long-Press PIN Lock) ----
        Column(
            modifier = Modifier
                .width(235.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(c.surface)
                .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
                .padding(10.dp)
        ) {
            // In-Playlist Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(strings.searchInPlaylist, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.longPressPinHint,
                color = c.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(6.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    CategoryRowItem(strings.allChannels, allCount, selectedCategory == "ALL", false, isTvMode,
                        onClick = { selectedCategory = "ALL" }, onLongClick = {})
                }
                item {
                    CategoryRowItem(strings.favourites, favCount, selectedCategory == "FAV", false, isTvMode,
                        onClick = { selectedCategory = "FAV" }, onLongClick = {})
                }
                item {
                    CategoryRowItem(strings.history, histCount, selectedCategory == "HIST", false, isTvMode,
                        onClick = { selectedCategory = "HIST" }, onLongClick = {})
                }
                item { HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 4.dp)) }

                items(categories, key = { it.name }) { cat ->
                    val isLocked = lockedCategories.contains(cat.name)
                    CategoryRowItem(
                        name = cat.name,
                        count = cat.count,
                        selected = selectedCategory == cat.name,
                        locked = isLocked,
                        isTvMode = isTvMode,
                        onClick = {
                            if (isLocked && !unlockedTempCategories.contains(cat.name)) {
                                pinActionCategory = cat.name
                                pinActionMode = "OPEN_LOCKED"
                            } else {
                                selectedCategory = cat.name
                            }
                        },
                        onLongClick = {
                            pinActionCategory = cat.name
                            pinActionMode = "TOGGLE_LOCK"
                        }
                    )
                }
            }
        }

        // ---- RIGHT CONTENT AREA ----
        if (mediaType == MediaType.LIVE) {
            // Split-Screen Live TV: Channel List (Middle) + Live Preview (Right)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surface)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(channels, key = { it.id }) { ch ->
                    val active = previewChannel?.id == ch.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) c.accent.copy(alpha = 0.20f) else c.surfaceElevated.copy(alpha = 0.55f))
                            .tvFocusBorder(isTvMode, RoundedCornerShape(12.dp))
                            .clickable {
                                if (active) {
                                    previewPlayer?.pause()
                                    onLaunchFullscreen(ch, channels)
                                } else {
                                    previewChannel = ch
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChannelLogo(ch.logoUrl, 34.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                ch.name,
                                color = if (active) c.accent else c.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(ch.category, color = c.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(
                            onClick = {
                                repo.db.toggleFavorite(ch.id, !ch.isFavorite)
                                refreshTrigger++
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                if (ch.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Fav",
                                tint = if (ch.isFavorite) c.accent else c.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Live Mini Preview Pane
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surface)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .clickable(enabled = previewChannel != null) {
                            previewChannel?.let {
                                previewPlayer?.pause()
                                onLaunchFullscreen(it, channels)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (previewChannel != null && previewPlayer != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = previewPlayer
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text(strings.fullscreen, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(40.dp))
                            Text(strings.tapToWatch, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                    }
                }

                previewChannel?.let { ch ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ch.name, color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ch.category, color = c.textSecondary, fontSize = 11.sp, maxLines = 1)
                        }
                        Button(
                            onClick = {
                                previewPlayer?.pause()
                                onLaunchFullscreen(ch, channels)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.fullscreen, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // Movies / Series Poster Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 124.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                items(channels, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surface)
                            .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                            .tvFocusBorder(isTvMode, RoundedCornerShape(16.dp))
                            .clickable {
                                if (mediaType == MediaType.SERIES && playlist.type == PlaylistType.XTREAM) {
                                    activeSeries = item
                                    loadingEpisodes = true
                                    scope.launch {
                                        episodes = repo.fetchSeriesEpisodes(playlist, item.streamId)
                                        loadingEpisodes = false
                                    }
                                } else {
                                    repo.db.markWatched(item.id)
                                    onLaunchFullscreen(item, channels)
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                                .background(c.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.logoUrl)
                                    .crossfade(true)
                                    .size(260)
                                    .build(),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = {
                                    repo.db.toggleFavorite(item.id, !item.isFavorite)
                                    refreshTrigger++
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                Icon(
                                    if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Fav",
                                    tint = if (item.isFavorite) c.accent else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Text(
                            text = item.name,
                            color = c.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }

    // PIN Dialog for Lock/Unlock or Opening Locked Category
    pinActionCategory?.let { catName ->
        val currentlyLocked = lockedCategories.contains(catName)
        val dialogTitle = when (pinActionMode) {
            "OPEN_LOCKED" -> "${strings.categoryLocked}: $catName"
            else -> if (currentlyLocked) strings.unlockCategory else strings.lockCategory
        }
        PinDialog(
            title = dialogTitle,
            expectedPin = pinCode,
            strings = strings,
            onDismiss = { pinActionCategory = null },
            onConfirm = {
                if (pinActionMode == "OPEN_LOCKED") {
                    unlockedTempCategories = unlockedTempCategories + catName
                    selectedCategory = catName
                } else {
                    onToggleLockCategory(catName)
                    if (currentlyLocked) unlockedTempCategories = unlockedTempCategories - catName
                    Toast.makeText(
                        context,
                        if (currentlyLocked) "${strings.unlockCategory}: $catName" else "${strings.lockCategory}: $catName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                pinActionCategory = null
            }
        )
    }

    // Series Episodes Picker
    activeSeries?.let { series ->
        AlertDialog(
            onDismissRequest = { activeSeries = null },
            containerColor = c.surface,
            title = { Text(series.name, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp)) {
                    if (loadingEpisodes) {
                        CircularProgressIndicator(color = c.accent, modifier = Modifier.align(Alignment.Center))
                    } else if (episodes.isEmpty()) {
                        Text(strings.noEpisodes, color = c.textSecondary, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(episodes, key = { it.id }) { ep ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(c.surfaceElevated)
                                        .clickable {
                                            activeSeries = null
                                            val epChannels = episodes.map {
                                                ChannelItem(
                                                    id = it.id.toLongOrNull() ?: 0L,
                                                    playlistId = playlist.id,
                                                    streamId = it.id,
                                                    name = "S${it.season}E${it.episodeNum} - ${it.title}",
                                                    category = series.name,
                                                    logoUrl = series.logoUrl,
                                                    streamUrl = it.streamUrl,
                                                    mediaType = MediaType.SERIES
                                                )
                                            }
                                            val picked = epChannels.first { it.streamUrl == ep.streamUrl }
                                            onLaunchFullscreen(picked, epChannels)
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("S${ep.season} E${ep.episodeNum} • ${ep.title}", color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        if (ep.plot.isNotBlank()) {
                                            Text(ep.plot, color = c.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.accent)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeSeries = null }) {
                    Text(strings.cancel, color = c.textSecondary)
                }
            }
        )
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRowItem(
    name: String,
    count: Int,
    selected: Boolean,
    locked: Boolean,
    isTvMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.accent else Color.Transparent)
            .tvFocusBorder(isTvMode, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (locked) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = "Locked",
                    tint = if (selected) Color.White else c.accent,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = name,
                color = if (selected) Color.White else c.textPrimary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = count.toString(),
            color = if (selected) Color.White.copy(alpha = 0.85f) else c.textSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ChannelLogo(url: String, size: androidx.compose.ui.unit.Dp) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(c.surface),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(false)
                    .size(120)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp)
            )
        } else {
            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
