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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
    sharedPlayer: ExoPlayer,
    activeChannel: ChannelItem?,
    playerMode: PlayerMode,
    lockedCategories: Set<String>,
    pinCode: String,
    strings: AppStrings,
    isTvMode: Boolean,
    onToggleLockCategory: (String) -> Unit,
    onSelectChannelInline: (ChannelItem, List<ChannelItem>) -> Unit,
    onLaunchPlayer: (ChannelItem, List<ChannelItem>, Boolean) -> Unit,
    onFloatCurrent: () -> Unit
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

    var pinActionCategory by remember { mutableStateOf<String?>(null) }
    var pinActionMode by remember { mutableStateOf("") }

    var activeSeries by remember { mutableStateOf<ChannelItem?>(null) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var loadingEpisodes by remember { mutableStateOf(false) }

    fun handleCategoryClick(catName: String) {
        val isLocked = lockedCategories.contains(catName)
        if (isLocked && !unlockedTempCategories.contains(catName)) {
            pinActionCategory = catName
            pinActionMode = "OPEN_LOCKED"
        } else {
            selectedCategory = catName
        }
    }

    fun handleCategoryLongClick(catName: String) {
        pinActionCategory = catName
        pinActionMode = "TOGGLE_LOCK"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
    ) {
        val isPortrait = maxWidth < 600.dp

        if (isPortrait) {
            // ---- PORTRAIT SMARTPHONE LAYOUT ----
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Bar inside playlist
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchInPlaylist, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Horizontal Category Chips (with Long-Press PIN Lock)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        CategoryChip(strings.allChannels, allCount, selectedCategory == "ALL", false,
                            onClick = { selectedCategory = "ALL" }, onLongClick = {})
                    }
                    item {
                        CategoryChip(strings.favourites, favCount, selectedCategory == "FAV", false,
                            onClick = { selectedCategory = "FAV" }, onLongClick = {})
                    }
                    item {
                        CategoryChip(strings.history, histCount, selectedCategory == "HIST", false,
                            onClick = { selectedCategory = "HIST" }, onLongClick = {})
                    }
                    items(categories, key = { it.name }) { cat ->
                        CategoryChip(
                            name = cat.name,
                            count = cat.count,
                            selected = selectedCategory == cat.name,
                            locked = lockedCategories.contains(cat.name),
                            onClick = { handleCategoryClick(cat.name) },
                            onLongClick = { handleCategoryLongClick(cat.name) }
                        )
                    }
                }

                // Inline Live Preview Box in Portrait (only when playing inline and NOT floating)
                if (mediaType == MediaType.LIVE && activeChannel != null && playerMode == PlayerMode.NONE) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.surface)
                            .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = sharedPlayer
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                update = { it.player = sharedPlayer },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(activeChannel.name, color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(activeChannel.category, color = c.textSecondary, fontSize = 11.sp, maxLines = 1)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = onFloatCurrent,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.floatPlayer, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { onLaunchPlayer(activeChannel, channels, true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.fullscreen, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Channel List or Poster Grid
                if (mediaType == MediaType.LIVE) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(channels, key = { it.id }) { ch ->
                            val active = activeChannel?.id == ch.id
                            ChannelListRow(
                                ch = ch,
                                active = active,
                                isTvMode = isTvMode,
                                onClick = {
                                    if (active && playerMode == PlayerMode.NONE) {
                                        onLaunchPlayer(ch, channels, true)
                                    } else {
                                        onSelectChannelInline(ch, channels)
                                    }
                                },
                                onToggleFav = {
                                    repo.db.toggleFavorite(ch.id, !ch.isFavorite)
                                    refreshTrigger++
                                }
                            )
                        }
                    }
                } else {
                    PosterGrid(
                        channels = channels,
                        mediaType = mediaType,
                        playlist = playlist,
                        isTvMode = isTvMode,
                        onOpenItem = { item ->
                            if (mediaType == MediaType.SERIES && playlist.type == PlaylistType.XTREAM) {
                                activeSeries = item
                                loadingEpisodes = true
                                scope.launch {
                                    episodes = repo.fetchSeriesEpisodes(playlist, item.streamId)
                                    loadingEpisodes = false
                                }
                            } else {
                                repo.db.markWatched(item.id)
                                // Launch player in Portrait first (manual fullscreen button inside player!)
                                onLaunchPlayer(item, channels, false)
                            }
                        },
                        onToggleFav = { item ->
                            repo.db.toggleFavorite(item.id, !item.isFavorite)
                            refreshTrigger++
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        } else {
            // ---- LANDSCAPE / ANDROID TV LAYOUT ----
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 78.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Sidebar
                Column(
                    modifier = Modifier
                        .width(235.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.surface)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
                        .padding(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(strings.searchInPlaylist, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(strings.longPressPinHint, color = c.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(6.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        item { CategoryRowItem(strings.allChannels, allCount, selectedCategory == "ALL", false, isTvMode, { selectedCategory = "ALL" }, {}) }
                        item { CategoryRowItem(strings.favourites, favCount, selectedCategory == "FAV", false, isTvMode, { selectedCategory = "FAV" }, {}) }
                        item { CategoryRowItem(strings.history, histCount, selectedCategory == "HIST", false, isTvMode, { selectedCategory = "HIST" }, {}) }
                        item { HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(categories, key = { it.name }) { cat ->
                            CategoryRowItem(
                                name = cat.name,
                                count = cat.count,
                                selected = selectedCategory == cat.name,
                                locked = lockedCategories.contains(cat.name),
                                isTvMode = isTvMode,
                                onClick = { handleCategoryClick(cat.name) },
                                onLongClick = { handleCategoryLongClick(cat.name) }
                            )
                        }
                    }
                }

                if (mediaType == MediaType.LIVE) {
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
                            val active = activeChannel?.id == ch.id
                            ChannelListRow(
                                ch = ch,
                                active = active,
                                isTvMode = isTvMode,
                                onClick = {
                                    if (active) onLaunchPlayer(ch, channels, true)
                                    else onSelectChannelInline(ch, channels)
                                },
                                onToggleFav = {
                                    repo.db.toggleFavorite(ch.id, !ch.isFavorite)
                                    refreshTrigger++
                                }
                            )
                        }
                    }

                    // Right Preview Pane
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
                                .clickable(enabled = activeChannel != null) {
                                    activeChannel?.let { onLaunchPlayer(it, channels, true) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (activeChannel != null && playerMode == PlayerMode.NONE) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = sharedPlayer
                                            useController = false
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        }
                                    },
                                    update = { it.player = sharedPlayer },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(40.dp))
                                    Text(strings.tapToWatch, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                                }
                            }
                        }

                        activeChannel?.let { ch ->
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
                                    onClick = { onLaunchPlayer(ch, channels, true) },
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
                    PosterGrid(
                        channels = channels,
                        mediaType = mediaType,
                        playlist = playlist,
                        isTvMode = isTvMode,
                        onOpenItem = { item ->
                            if (mediaType == MediaType.SERIES && playlist.type == PlaylistType.XTREAM) {
                                activeSeries = item
                                loadingEpisodes = true
                                scope.launch {
                                    episodes = repo.fetchSeriesEpisodes(playlist, item.streamId)
                                    loadingEpisodes = false
                                }
                            } else {
                                repo.db.markWatched(item.id)
                                onLaunchPlayer(item, channels, isTvMode)
                            }
                        },
                        onToggleFav = { item ->
                            repo.db.toggleFavorite(item.id, !item.isFavorite)
                            refreshTrigger++
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }

    // PIN Dialog
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

    // Series Episodes Dialog
    activeSeries?.let { series ->
        AlertDialog(
            onDismissRequest = { activeSeries = null },
            containerColor = c.surface,
            title = { Text(series.name, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp)) {
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
                                            onLaunchPlayer(picked, epChannels, isTvMode)
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
private fun CategoryChip(
    name: String,
    count: Int,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.accent else c.surface)
            .border(1.dp, if (selected) c.accent else c.glassBorder, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (locked) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = if (selected) Color.White else c.accent, modifier = Modifier.size(13.dp))
        }
        Text(name, color = if (selected) Color.White else c.textPrimary, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        Text(count.toString(), color = if (selected) Color.White.copy(alpha = 0.8f) else c.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun ChannelListRow(
    ch: ChannelItem,
    active: Boolean,
    isTvMode: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) c.accent.copy(alpha = 0.20f) else c.surface)
            .border(1.dp, if (active) c.accent.copy(alpha = 0.55f) else c.glassBorder, RoundedCornerShape(14.dp))
            .tvFocusBorder(isTvMode, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ChannelLogo(ch.logoUrl, 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                ch.name,
                color = if (active) c.accent else c.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(ch.category, color = c.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onToggleFav, modifier = Modifier.size(32.dp)) {
            Icon(
                if (ch.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Fav",
                tint = if (ch.isFavorite) c.accent else c.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PosterGrid(
    channels: List<ChannelItem>,
    mediaType: MediaType,
    playlist: PlaylistProfile,
    isTvMode: Boolean,
    onOpenItem: (ChannelItem) -> Unit,
    onToggleFav: (ChannelItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 114.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(channels, key = { it.id }) { item ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surface)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                    .tvFocusBorder(isTvMode, RoundedCornerShape(16.dp))
                    .clickable { onOpenItem(item) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(146.dp)
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
                        onClick = { onToggleFav(item) },
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
                Icon(Icons.Rounded.Lock, contentDescription = "Locked", tint = if (selected) Color.White else c.accent, modifier = Modifier.size(14.dp))
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
            .background(c.surfaceElevated),
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
