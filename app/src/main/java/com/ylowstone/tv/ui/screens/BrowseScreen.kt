package com.ylowstone.tv.ui.screens

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.ylowstone.tv.ui.components.ChannelLogoBox
import com.ylowstone.tv.ui.components.PinDialog
import com.ylowstone.tv.ui.components.ProfileYBadgeButton
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onToggleFavorite: (ChannelItem) -> Unit,
    onSelectChannelInline: (ChannelItem, List<ChannelItem>) -> Unit,
    onLaunchPlayer: (ChannelItem, List<ChannelItem>, Boolean) -> Unit,
    onFloatCurrent: () -> Unit,
    onOpenProfileMenu: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var openedCategory by remember(playlist.id, mediaType) { mutableStateOf<String?>(null) }
    var landscapeCategory by remember(playlist.id, mediaType) { mutableStateOf("ALL") }
    var catFilterQuery by remember(playlist.id, mediaType) { mutableStateOf("") }
    var channelSearchQuery by remember(playlist.id, mediaType) { mutableStateOf("") }
    var debouncedChannelSearch by remember(playlist.id, mediaType) { mutableStateOf("") }
    var unlockedTempCategories by remember(playlist.id) { mutableStateOf<Set<String>>(emptySet()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(channelSearchQuery) {
        delay(150)
        debouncedChannelSearch = channelSearchQuery
    }

    val categories = remember(playlist.id, mediaType, refreshTrigger) {
        repo.db.getCategories(playlist.id, mediaType)
    }
    val filteredCategories = remember(categories, catFilterQuery) {
        if (catFilterQuery.isBlank()) categories
        else categories.filter { it.name.contains(catFilterQuery.trim(), ignoreCase = true) }
    }
    val allCount = remember(categories) { categories.sumOf { it.count } }
    val favCount = remember(playlist.id, mediaType, refreshTrigger) {
        repo.db.getFavoriteCount(playlist.id, mediaType)
    }
    val histCount = remember(playlist.id, mediaType, refreshTrigger) {
        repo.db.getHistoryCount(playlist.id, mediaType)
    }

    var pinActionCategory by remember { mutableStateOf<String?>(null) }
    var pinActionMode by remember { mutableStateOf("") }
    var pinActionIsPortrait by remember { mutableStateOf(true) }

    var activeSeries by remember { mutableStateOf<ChannelItem?>(null) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var loadingEpisodes by remember { mutableStateOf(false) }

    val sectionTitle = when (mediaType) {
        MediaType.LIVE -> strings.liveTv
        MediaType.MOVIE -> strings.movies
        MediaType.SERIES -> strings.series
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
    ) {
        val isPortrait = maxWidth < 600.dp
        val activeCatForQuery = if (isPortrait) (openedCategory ?: "ALL") else landscapeCategory
        val isActiveCatLocked = lockedCategories.contains(activeCatForQuery) && !unlockedTempCategories.contains(activeCatForQuery)

        var channels by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
        LaunchedEffect(playlist.id, mediaType, activeCatForQuery, debouncedChannelSearch, lockedCategories, unlockedTempCategories, refreshTrigger, isPortrait, openedCategory) {
            if (isPortrait && openedCategory == null && debouncedChannelSearch.isBlank()) {
                channels = emptyList()
                return@LaunchedEffect
            }
            if (isActiveCatLocked) {
                channels = emptyList()
                return@LaunchedEffect
            }
            channels = withContext(Dispatchers.IO) {
                repo.db.queryChannels(
                    playlistId = playlist.id,
                    mediaType = mediaType,
                    specialFilter = activeCatForQuery,
                    searchQuery = debouncedChannelSearch,
                    lockedCategories = lockedCategories - unlockedTempCategories,
                    allowLockedCategory = unlockedTempCategories.contains(activeCatForQuery),
                    limit = 600
                )
            }
        }

        if (isPortrait) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (openedCategory != null) {
                            IconButton(
                                onClick = { openedCategory = null; channelSearchQuery = "" },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(c.surface)
                                    .border(1.dp, c.glassBorder, CircleShape)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Back", tint = c.textPrimary)
                            }
                        }
                        Column {
                            Text(
                                text = when (openedCategory) {
                                    null -> "$sectionTitle • ${strings.categories}"
                                    "ALL" -> strings.allChannels
                                    "FAV" -> strings.favourites
                                    "HIST" -> strings.history
                                    else -> openedCategory!!
                                },
                                color = c.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (openedCategory == null) strings.longPressPinHint else strings.swipeDownToCategories,
                                color = c.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    ProfileYBadgeButton(isTvMode = isTvMode, onClick = onOpenProfileMenu)
                }

                Spacer(Modifier.height(10.dp))

                AnimatedContent(
                    targetState = openedCategory,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInVertically { it / 4 } + fadeIn()).togetherWith(fadeOut())
                        } else {
                            fadeIn().togetherWith(slideOutVertically { it / 4 } + fadeOut())
                        }
                    },
                    label = "catToChannels",
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { currentCat ->
                    if (currentCat == null) {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = catFilterQuery,
                                onValueChange = { catFilterQuery = it },
                                placeholder = { Text(strings.categories + "...", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (catFilterQuery.isNotEmpty()) {
                                        IconButton(onClick = { catFilterQuery = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                item {
                                    VerticalCategoryCard(
                                        name = strings.allChannels,
                                        count = allCount,
                                        icon = Icons.Rounded.GridView,
                                        locked = false,
                                        highlight = true,
                                        onClick = { openedCategory = "ALL" },
                                        onLongClick = {}
                                    )
                                }
                                item {
                                    VerticalCategoryCard(
                                        name = strings.favourites,
                                        count = favCount,
                                        icon = Icons.Rounded.Favorite,
                                        locked = false,
                                        highlight = false,
                                        onClick = { openedCategory = "FAV" },
                                        onLongClick = {}
                                    )
                                }
                                item {
                                    VerticalCategoryCard(
                                        name = strings.history,
                                        count = histCount,
                                        icon = Icons.Rounded.History,
                                        locked = false,
                                        highlight = false,
                                        onClick = { openedCategory = "HIST" },
                                        onLongClick = {}
                                    )
                                }
                                item { HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 4.dp)) }

                                items(filteredCategories, key = { it.name }) { cat ->
                                    val isLocked = lockedCategories.contains(cat.name)
                                    VerticalCategoryCard(
                                        name = cat.name,
                                        count = cat.count,
                                        icon = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.FolderOpen,
                                        locked = isLocked,
                                        highlight = false,
                                        onClick = {
                                            if (isLocked && !unlockedTempCategories.contains(cat.name)) {
                                                pinActionCategory = cat.name
                                                pinActionMode = "OPEN_LOCKED"
                                                pinActionIsPortrait = true
                                            } else {
                                                openedCategory = cat.name
                                            }
                                        },
                                        onLongClick = {
                                            pinActionCategory = cat.name
                                            pinActionMode = "TOGGLE_LOCK"
                                            pinActionIsPortrait = true
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.surface)
                                    .pointerInput(currentCat) {
                                        var totalDrag = 0f
                                        detectVerticalDragGestures(
                                            onDragEnd = {
                                                if (totalDrag > 70f) {
                                                    openedCategory = null
                                                    channelSearchQuery = ""
                                                }
                                                totalDrag = 0f
                                            }
                                        ) { _, dragAmount ->
                                            if (dragAmount > 0f) totalDrag += dragAmount
                                        }
                                    }
                                    .clickable { openedCategory = null; channelSearchQuery = "" }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(c.textSecondary.copy(alpha = 0.45f))
                                )
                            }

                            OutlinedTextField(
                                value = channelSearchQuery,
                                onValueChange = { channelSearchQuery = it },
                                placeholder = { Text(strings.searchChannelPlaceholder, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (channelSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { channelSearchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (mediaType == MediaType.LIVE && activeChannel != null && playerMode == PlayerMode.NONE) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(c.surface)
                                        .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(12.dp))
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
                                                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.fullscreen, color = c.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            if (mediaType == MediaType.LIVE) {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(bottom = 20.dp),
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
                                                onToggleFavorite(ch)
                                                refreshTrigger++
                                            }
                                        )
                                    }
                                }
                            } else {
                                PosterGrid(
                                    channels = channels,
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
                                            onLaunchPlayer(item, channels, false)
                                        }
                                    },
                                    onToggleFav = { item ->
                                        onToggleFavorite(item)
                                        refreshTrigger++
                                    },
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Landscape / Android TV split-screen
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    OutlinedTextField(
                        value = channelSearchQuery,
                        onValueChange = { channelSearchQuery = it },
                        placeholder = { Text(strings.searchChannelPlaceholder, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        item { CategoryRowItem(strings.allChannels, allCount, landscapeCategory == "ALL", false, isTvMode, { landscapeCategory = "ALL" }, {}) }
                        item { CategoryRowItem(strings.favourites, favCount, landscapeCategory == "FAV", false, isTvMode, { landscapeCategory = "FAV" }, {}) }
                        item { CategoryRowItem(strings.history, histCount, landscapeCategory == "HIST", false, isTvMode, { landscapeCategory = "HIST" }, {}) }
                        item { HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(categories, key = { it.name }) { cat ->
                            val isLocked = lockedCategories.contains(cat.name)
                            CategoryRowItem(
                                name = cat.name,
                                count = cat.count,
                                selected = landscapeCategory == cat.name,
                                locked = isLocked,
                                isTvMode = isTvMode,
                                onClick = {
                                    if (isLocked && !unlockedTempCategories.contains(cat.name)) {
                                        pinActionCategory = cat.name
                                        pinActionMode = "OPEN_LOCKED"
                                        pinActionIsPortrait = false
                                    } else {
                                        landscapeCategory = cat.name
                                    }
                                },
                                onLongClick = {
                                    pinActionCategory = cat.name
                                    pinActionMode = "TOGGLE_LOCK"
                                    pinActionIsPortrait = false
                                }
                            )
                        }
                    }
                }

                if (mediaType == MediaType.LIVE) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surface)
                            .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
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
                                    onToggleFavorite(ch)
                                    refreshTrigger++
                                }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surface)
                            .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
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
                                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.fullscreen, color = c.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    PosterGrid(
                        channels = channels,
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
                            onToggleFavorite(item)
                            refreshTrigger++
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }

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
                    if (pinActionIsPortrait) openedCategory = catName else landscapeCategory = catName
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
private fun VerticalCategoryCard(
    name: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    locked: Boolean,
    highlight: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) c.accentContainer else c.surface)
            .border(1.dp, if (highlight) c.accent.copy(alpha = 0.5f) else c.glassBorder, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (highlight || locked) c.accent else c.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (highlight || locked) c.onAccent else c.textPrimary, modifier = Modifier.size(18.dp))
            }
            Text(
                text = name,
                color = c.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(count.toString(), color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
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
            .background(if (active) c.accentContainer else c.surface)
            .border(1.dp, if (active) c.accent else c.glassBorder, RoundedCornerShape(14.dp))
            .tvFocusBorder(isTvMode, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ChannelLogoBox(ch.logoUrl, 38.dp, corner = 10.dp)
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
    isTvMode: Boolean,
    onOpenItem: (ChannelItem) -> Unit,
    onToggleFav: (ChannelItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(channels, key = { it.id }) { item ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                    .tvFocusBorder(isTvMode, RoundedCornerShape(14.dp))
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
                Icon(Icons.Rounded.Lock, contentDescription = "Locked", tint = if (selected) c.onAccent else c.accent, modifier = Modifier.size(14.dp))
            }
            Text(
                text = name,
                color = if (selected) c.onAccent else c.textPrimary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = count.toString(),
            color = if (selected) c.onAccent.copy(alpha = 0.8f) else c.textSecondary,
            fontSize = 11.sp
        )
    }
}
