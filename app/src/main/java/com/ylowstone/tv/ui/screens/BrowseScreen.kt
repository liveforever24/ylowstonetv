package com.ylowstone.tv.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.text.TextStyle
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
import com.ylowstone.tv.R
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.components.BorderlessCategoryRow
import com.ylowstone.tv.ui.components.BorderlessChannelRow
import com.ylowstone.tv.ui.components.CinematicCollapsingListScreen
import com.ylowstone.tv.ui.components.CinematicNoirTokens
import com.ylowstone.tv.ui.components.GlossyGridScrollPositionPill
import com.ylowstone.tv.ui.components.PinDialog
import com.ylowstone.tv.ui.components.ProfileYBadgeButton
import com.ylowstone.tv.ui.components.QuickChipSpec
import com.ylowstone.tv.ui.components.YlowInterDisplay
import com.ylowstone.tv.ui.components.glossyPressPill
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
    isPlaying: Boolean,
    playerMode: PlayerMode,
    lockedCategories: Set<String>,
    pinCode: String,
    strings: AppStrings,
    isTvMode: Boolean,
    onBackHome: () -> Unit,
    onToggleLockCategory: (String) -> Unit,
    onToggleFavorite: (ChannelItem) -> Unit,
    onSelectChannelInline: (ChannelItem, List<ChannelItem>) -> Unit,
    onLaunchPlayer: (ChannelItem, List<ChannelItem>, Boolean) -> Unit,
    onLongPressChannel: (ChannelItem) -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenActivePlayer: () -> Unit,
    onFloatCurrent: () -> Unit,
    onOpenProfileMenu: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var openedCategory by remember(playlist.id, mediaType) { mutableStateOf<String?>(null) }
    var landscapeCategory by remember(playlist.id, mediaType) { mutableStateOf("ALL") }
    var catFilterQuery by remember(playlist.id, mediaType) { mutableStateOf("") }
    var catSortAz by remember(playlist.id, mediaType) { mutableStateOf(false) }
    var channelSearchQuery by remember(playlist.id, mediaType) { mutableStateOf("") }
    var debouncedChannelSearch by remember(playlist.id, mediaType) { mutableStateOf("") }
    var channelSortAz by remember(playlist.id, mediaType) { mutableStateOf(false) }
    var channelFavOnly by remember(playlist.id, mediaType) { mutableStateOf(false) }
    var unlockedTempCategories by remember(playlist.id) { mutableStateOf<Set<String>>(emptySet()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val catListState = rememberLazyListState()

    LaunchedEffect(channelSearchQuery) {
        delay(150)
        debouncedChannelSearch = channelSearchQuery
    }

    val categories = remember(playlist.id, mediaType, refreshTrigger) {
        repo.db.getCategories(playlist.id, mediaType)
    }
    val filteredCategories = remember(categories, catFilterQuery, catSortAz) {
        val base = if (catFilterQuery.isBlank()) categories
        else categories.filter { it.name.contains(catFilterQuery.trim(), ignoreCase = true) }
        if (catSortAz) base.sortedBy { it.name.lowercase() } else base
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
    val sectionCoverRes = when (mediaType) {
        MediaType.LIVE -> R.drawable.cover_yellowstone_tv
        MediaType.MOVIE -> R.drawable.cover_yellowstone_film
        MediaType.SERIES -> R.drawable.cover_yellowstone_seri
    }
    val sectionGradColor = when (mediaType) {
        MediaType.LIVE -> CinematicNoirTokens.GradTv
        MediaType.MOVIE -> CinematicNoirTokens.GradFilm
        MediaType.SERIES -> CinematicNoirTokens.GradSeri
    }
    val isIndonesian = strings.watchHistory.startsWith("Riwayat", ignoreCase = true)
    val sortLabel = if (isIndonesian) "Urut" else "Sort"
    val favChipLabel = if (isIndonesian) "+ Favorit" else "+ Favourites"
    val sortChipLabel = if (isIndonesian) "Urutkan" else "Sort A-Z"
    val searchPlaylistHint = if (isIndonesian) "Cari di daftar putar..." else "Search in playlist..."

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CinematicNoirTokens.BgAmoled)
            .statusBarsPadding()
    ) {
        val isPortrait = maxWidth < 600.dp
        BackHandler(enabled = isPortrait && openedCategory != null && playerMode != PlayerMode.FULLSCREEN && playerMode != PlayerMode.SPLIT) {
            openedCategory = null
            channelSearchQuery = ""
            channelFavOnly = false
        }
        val activeCatForQuery = if (isPortrait) (openedCategory ?: "ALL") else landscapeCategory
        val isActiveCatLocked = lockedCategories.contains(activeCatForQuery) && !unlockedTempCategories.contains(activeCatForQuery)

        var rawChannels by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
        LaunchedEffect(playlist.id, mediaType, activeCatForQuery, debouncedChannelSearch, lockedCategories, unlockedTempCategories, refreshTrigger, isPortrait, openedCategory) {
            if (isPortrait && openedCategory == null && debouncedChannelSearch.isBlank()) {
                rawChannels = emptyList()
                return@LaunchedEffect
            }
            if (isActiveCatLocked) {
                rawChannels = emptyList()
                return@LaunchedEffect
            }
            rawChannels = withContext(Dispatchers.IO) {
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

        val channels = remember(rawChannels, channelFavOnly, channelSortAz) {
            val f = if (channelFavOnly) rawChannels.filter { it.isFavorite } else rawChannels
            if (channelSortAz) f.sortedBy { it.name.lowercase() } else f
        }

        fun openMediaItem(item: ChannelItem, queue: List<ChannelItem>) {
            if (mediaType == MediaType.SERIES && playlist.type == PlaylistType.XTREAM) {
                activeSeries = item
                loadingEpisodes = true
                scope.launch {
                    episodes = repo.fetchSeriesEpisodes(playlist, item.streamId)
                    loadingEpisodes = false
                }
            } else {
                if (mediaType != MediaType.LIVE) repo.db.markWatched(item.id)
                onLaunchPlayer(item, queue, false)
            }
        }

        if (isPortrait) {
            AnimatedContent(
                targetState = openedCategory,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInVertically { it / 5 } + fadeIn()).togetherWith(fadeOut())
                    } else {
                        fadeIn().togetherWith(slideOutVertically { it / 5 } + fadeOut())
                    }
                },
                label = "catToChannels",
                modifier = Modifier.fillMaxSize()
            ) { currentCat ->
                if (currentCat == null) {
                    // =================================================
                    // PORTRAIT: DAFTAR KATEGORI (COLLAPSING HEADER + BORDERLESS)
                    // =================================================
                    CinematicCollapsingListScreen(
                        title = "$sectionTitle • ${strings.categories}",
                        subtitleInfo = "ylowstone • $allCount ${strings.channelsCount}",
                        coverRes = sectionCoverRes,
                        headerGradientColor = sectionGradColor,
                        searchQuery = catFilterQuery,
                        onSearchQueryChange = { catFilterQuery = it },
                        searchPlaceholder = searchPlaylistHint,
                        isTvMode = isTvMode,
                        listState = catListState,
                        showLiveMetaTag = mediaType == MediaType.LIVE,
                        sortLabel = sortLabel,
                        sortActive = catSortAz,
                        onSortClick = { catSortAz = !catSortAz },
                        quickChips = listOf(
                            QuickChipSpec("all", strings.allChannels, false) { openedCategory = "ALL" },
                            QuickChipSpec("fav", "${strings.favourites} ($favCount)", false) { openedCategory = "FAV" },
                            QuickChipSpec("hist", "${strings.history} ($histCount)", false) { openedCategory = "HIST" }
                        ),
                        onPlayAction = { openedCategory = "ALL" },
                        onBack = onBackHome,
                        topRightAction = {
                            ProfileYBadgeButton(isTvMode = isTvMode, onClick = onOpenProfileMenu)
                        },
                        activeMiniChannel = if (playerMode == PlayerMode.NONE || playerMode == PlayerMode.FLOATING) activeChannel else null,
                        isMiniPlaying = isPlaying,
                        onMiniTogglePlay = onTogglePlayPause,
                        onMiniOpenPlayer = onOpenActivePlayer
                    ) {
                        item(key = "cat_all") {
                            BorderlessCategoryRow(
                                name = strings.allChannels,
                                countText = "$allCount ${strings.channelsCount}",
                                icon = Icons.Rounded.GridView,
                                locked = false,
                                highlight = true,
                                isTvMode = isTvMode,
                                onClick = { openedCategory = "ALL" },
                                onLongClick = {}
                            )
                        }
                        item(key = "cat_fav") {
                            BorderlessCategoryRow(
                                name = strings.favourites,
                                countText = "$favCount ${strings.channelsCount}",
                                icon = Icons.Rounded.Favorite,
                                locked = false,
                                highlight = false,
                                isTvMode = isTvMode,
                                onClick = { openedCategory = "FAV" },
                                onLongClick = {}
                            )
                        }
                        item(key = "cat_hist") {
                            BorderlessCategoryRow(
                                name = strings.history,
                                countText = "$histCount ${strings.channelsCount}",
                                icon = Icons.Rounded.History,
                                locked = false,
                                highlight = false,
                                isTvMode = isTvMode,
                                onClick = { openedCategory = "HIST" },
                                onLongClick = {}
                            )
                        }
                        item(key = "cat_divider") {
                            HorizontalDivider(
                                color = CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.16f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        items(filteredCategories, key = { it.name }) { cat ->
                            val isLocked = lockedCategories.contains(cat.name)
                            BorderlessCategoryRow(
                                name = cat.name,
                                countText = "${cat.count} ${strings.channelsCount}",
                                icon = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.FolderOpen,
                                locked = isLocked,
                                highlight = false,
                                isTvMode = isTvMode,
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
                } else {
                    // =================================================
                    // PORTRAIT: DAFTAR CHANNEL / FILM / SERI (COLLAPSING HEADER + BORDERLESS)
                    // =================================================
                    val channelListState = rememberLazyListState()
                    val openedTitle = when (currentCat) {
                        "ALL" -> strings.allChannels
                        "FAV" -> strings.favourites
                        "HIST" -> strings.history
                        else -> currentCat
                    }
                    val firstPlayable = channels.firstOrNull { !it.isProviderSeparator() }

                    CinematicCollapsingListScreen(
                        title = openedTitle,
                        subtitleInfo = "ylowstone • ${channels.size} ${strings.channelsCount}",
                        coverRes = sectionCoverRes,
                        headerGradientColor = sectionGradColor,
                        searchQuery = channelSearchQuery,
                        onSearchQueryChange = { channelSearchQuery = it },
                        searchPlaceholder = searchPlaylistHint,
                        isTvMode = isTvMode,
                        listState = channelListState,
                        showLiveMetaTag = mediaType == MediaType.LIVE,
                        sortLabel = sortLabel,
                        sortActive = channelSortAz,
                        onSortClick = { channelSortAz = !channelSortAz },
                        quickChips = buildList {
                            add(QuickChipSpec("fav_filter", favChipLabel, channelFavOnly) { channelFavOnly = !channelFavOnly })
                            add(QuickChipSpec("sort_az", sortChipLabel, channelSortAz) { channelSortAz = !channelSortAz })
                            if (mediaType == MediaType.LIVE) {
                                add(QuickChipSpec("live_badge", "LIVE", true) {
                                    firstPlayable?.let { openMediaItem(it, channels) }
                                })
                            }
                        },
                        onPlayAction = {
                            firstPlayable?.let { openMediaItem(it, channels) }
                        },
                        onBack = {
                            openedCategory = null
                            channelSearchQuery = ""
                            channelFavOnly = false
                        },
                        topRightAction = {
                            ProfileYBadgeButton(isTvMode = isTvMode, onClick = onOpenProfileMenu)
                        },
                        activeMiniChannel = if (playerMode == PlayerMode.NONE || playerMode == PlayerMode.FLOATING) activeChannel else null,
                        isMiniPlaying = isPlaying,
                        onMiniTogglePlay = onTogglePlayPause,
                        onMiniOpenPlayer = onOpenActivePlayer
                    ) {
                        items(channels, key = { it.id }) { ch ->
                            val isActive = activeChannel?.id == ch.id && activeChannel.streamUrl == ch.streamUrl
                            BorderlessChannelRow(
                                ch = ch,
                                active = isActive,
                                isTvMode = isTvMode,
                                onClick = { openMediaItem(ch, channels) },
                                onLongClick = { onLongPressChannel(ch) },
                                onToggleFav = {
                                    onToggleFavorite(ch)
                                    refreshTrigger++
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // =================================================
            // LANDSCAPE / ANDROID TV (AMOLED #000000 + CHAMPAGNE-TAUPE #B09878 + BORDERLESS)
            // =================================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CinematicNoirTokens.BgAmoled)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Kolom Kategori Kiri
                Column(
                    modifier = Modifier
                        .width(248.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0A0908))
                        .border(0.8.dp, CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF171513))
                                .border(0.8.dp, CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.35f), CircleShape)
                                .tvFocusBorder(isTvMode, CircleShape)
                                .clickable(onClick = onBackHome),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = CinematicNoirTokens.TextPrimary, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = sectionTitle,
                            style = TextStyle(
                                fontFamily = YlowInterDisplay,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = channelSearchQuery,
                        onValueChange = { channelSearchQuery = it },
                        placeholder = { Text(searchPlaylistHint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = CinematicNoirTokens.ChampagneAccent, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = YlowInterDisplay, fontSize = 12.sp, color = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinematicNoirTokens.ChampagnePlay,
                            unfocusedBorderColor = CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.35f),
                            cursorColor = CinematicNoirTokens.ChampagnePlay
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        item { CategoryRowItem(strings.allChannels, allCount, landscapeCategory == "ALL", false, isTvMode, { landscapeCategory = "ALL" }, {}) }
                        item { CategoryRowItem(strings.favourites, favCount, landscapeCategory == "FAV", false, isTvMode, { landscapeCategory = "FAV" }, {}) }
                        item { CategoryRowItem(strings.history, histCount, landscapeCategory == "HIST", false, isTvMode, { landscapeCategory = "HIST" }, {}) }
                        item { HorizontalDivider(color = CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.16f), modifier = Modifier.padding(vertical = 4.dp)) }
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
                    // Kolom Daftar Channel Borderless di Tengah
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(CinematicNoirTokens.BgAmoled),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(channels, key = { it.id }) { ch ->
                            val active = activeChannel?.id == ch.id && activeChannel.streamUrl == ch.streamUrl
                            BorderlessChannelRow(
                                ch = ch,
                                active = active,
                                isTvMode = isTvMode,
                                onClick = {
                                    if (active) onLaunchPlayer(ch, channels, false)
                                    else onSelectChannelInline(ch, channels)
                                },
                                onLongClick = { onLongPressChannel(ch) },
                                onToggleFav = {
                                    onToggleFavorite(ch)
                                    refreshTrigger++
                                }
                            )
                        }
                    }

                    // Kolom Pratinjau Video Kanan
                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0A0908))
                            .border(0.8.dp, CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
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
                                    Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.45f), modifier = Modifier.size(40.dp))
                                    Text(strings.tapToWatch, color = CinematicNoirTokens.TextSecondary, fontSize = 12.sp)
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
                                    Text(
                                        text = ch.name,
                                        style = TextStyle(
                                            fontFamily = YlowInterDisplay,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = CinematicNoirTokens.ChampagneHighlight
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("• LIVE", color = CinematicNoirTokens.LiveRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(ch.category, color = CinematicNoirTokens.TextSecondary, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                                Button(
                                    onClick = { onLaunchPlayer(ch, channels, true) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CinematicNoirTokens.ChampagnePlay,
                                        contentColor = Color(0xFF14110D)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = Color(0xFF14110D), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.fullscreen, color = Color(0xFF14110D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    PosterGrid(
                        channels = channels,
                        activeChannel = activeChannel,
                        isTvMode = isTvMode,
                        onOpenItem = { item -> openMediaItem(item, channels) },
                        onLongPressItem = { item -> onLongPressChannel(item) },
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
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp)) {
                    if (loadingEpisodes) {
                        CircularProgressIndicator(color = CinematicNoirTokens.ChampagnePlay, modifier = Modifier.align(Alignment.Center))
                    } else if (episodes.isEmpty()) {
                        Text(strings.noEpisodes, color = c.textSecondary, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(episodes, key = { it.id }) { ep ->
                                val epChannel = ChannelItem(
                                    id = ep.id.toLongOrNull() ?: 0L,
                                    playlistId = playlist.id,
                                    streamId = ep.id,
                                    name = "S${ep.season}E${ep.episodeNum} - ${ep.title}",
                                    category = series.name,
                                    logoUrl = series.logoUrl,
                                    streamUrl = ep.streamUrl,
                                    mediaType = MediaType.SERIES
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .combinedClickable(
                                            onClick = {
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
                                                onLaunchPlayer(picked, epChannels, false)
                                            },
                                            onLongClick = { onLongPressChannel(epChannel) }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "S${ep.season} E${ep.episodeNum} • ${ep.title}",
                                            color = c.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (ep.plot.isNotBlank()) {
                                            Text(ep.plot, color = c.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = CinematicNoirTokens.ChampagnePlay)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeSeries = null }) {
                    Text(strings.cancel, color = CinematicNoirTokens.ChampagneAccent)
                }
            }
        )
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterGrid(
    channels: List<ChannelItem>,
    activeChannel: ChannelItem?,
    isTvMode: Boolean,
    onOpenItem: (ChannelItem) -> Unit,
    onLongPressItem: (ChannelItem) -> Unit,
    onToggleFav: (ChannelItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    Box(modifier = modifier) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 116.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(channels, key = { it.id }) { item ->
                val isActive = activeChannel?.id == item.id && activeChannel.streamUrl == item.streamUrl
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0E0D0C))
                        .border(
                            width = if (isActive) 1.4.dp else 0.7.dp,
                            color = if (isActive) CinematicNoirTokens.ChampagnePlay else CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.20f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .tvFocusBorder(isTvMode, RoundedCornerShape(14.dp))
                        .glossyPressPill(14f)
                        .combinedClickable(
                            onClick = { onOpenItem(item) },
                            onLongClick = { onLongPressItem(item) }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                            .background(Color(0xFF151412)),
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
                                tint = if (item.isFavorite) CinematicNoirTokens.ChampagnePlay else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Text(
                        text = item.name,
                        color = if (isActive) CinematicNoirTokens.ChampagneHighlight else CinematicNoirTokens.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
        GlossyGridScrollPositionPill(
            gridState = gridState,
            totalItemsCount = channels.size,
            modifier = Modifier.align(Alignment.CenterEnd)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.22f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(0.9.dp, CinematicNoirTokens.ChampagnePlay, RoundedCornerShape(10.dp))
                else Modifier
            )
            .tvFocusBorder(isTvMode, RoundedCornerShape(10.dp))
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
                    tint = CinematicNoirTokens.ChampagnePlay,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = name,
                style = TextStyle(
                    fontFamily = YlowInterDisplay,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    color = if (selected) CinematicNoirTokens.ChampagneHighlight else CinematicNoirTokens.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = count.toString(),
            style = TextStyle(
                fontFamily = YlowInterDisplay,
                fontSize = 11.sp,
                color = if (selected) CinematicNoirTokens.ChampagnePlay else CinematicNoirTokens.TextSecondary
            )
        )
    }
}
