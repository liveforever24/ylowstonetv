package com.ylowstone.tv.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.derivedStateOf
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ylowstone.tv.ui.components.liquidGlassSurface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.R
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.ChannelItem
import com.ylowstone.tv.model.FavoriteViewMode
import com.ylowstone.tv.model.MediaType
import com.ylowstone.tv.model.PlaylistProfile
import com.ylowstone.tv.model.TaggedChannel
import com.ylowstone.tv.ui.components.BorderlessChannelRow
import com.ylowstone.tv.ui.components.ChannelLogoBox
import com.ylowstone.tv.ui.components.CinematicCollapsingListScreen
import com.ylowstone.tv.ui.components.CinematicNoirTokens
import com.ylowstone.tv.ui.components.QuickChipSpec
import com.ylowstone.tv.ui.components.YlowInterDisplay
import com.ylowstone.tv.ui.components.glossyPressPill
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

private object HomeCoverColors {
    val CardSurfaceTv = Color(0xFF14110D)
    val CardSurfaceFilm = Color(0xFF121718)
    val CardSurfaceSeri = Color(0xFF161412)
    val AmbientTv = Color(0xFFC88435)
    val AmbientFilm = Color(0xFF6E8B8A)
    val AmbientSeri = Color(0xFFB09878)
    val LiveRed = Color(0xFFFA263E)
}

private enum class HomeSubView { NONE, CONTINUE_WATCHING, FAVORITES }

/**
 * Beranda YlowstoneTV v2.4.2:
 * - Strict Tap Guard (Swipe-vs-Tap Rejection > 12dp + 350ms debounce) pada semua kartu Beranda
 * - Animasi ketuk "Tenggelam" (Dive-In sink) halus ~110ms pada kartu TV, FILM, & SERI
 * - Adaptif penuh terhadap 5 Tema Palet Terpadu (Sistem, Gelap, Cerah, Amber Dusk, Velvet Crimson)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    activePlaylist: PlaylistProfile,
    counts: Map<MediaType, Int>,
    favourites: List<TaggedChannel>,
    history: List<TaggedChannel>,
    favoriteViewMode: FavoriteViewMode,
    activeChannel: ChannelItem?,
    isPlaying: Boolean,
    strings: AppStrings,
    isTvMode: Boolean,
    onOpenSection: (MediaType) -> Unit,
    onOpenPlaylists: () -> Unit,
    onChangeFavoriteViewMode: (FavoriteViewMode) -> Unit,
    onPlayTaggedChannel: (TaggedChannel, List<TaggedChannel>) -> Unit,
    onLongPressTaggedChannel: (TaggedChannel) -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenActivePlayer: () -> Unit,
    onOpenProfileMenu: () -> Unit,
    verticalListState: LazyListState = rememberLazyListState(),
    continueRowState: LazyListState = rememberLazyListState(),
    favouritesRowState: LazyListState = rememberLazyListState()
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val isTvHardware = remember(context) { isAndroidTvDevice(context) }
    var activeSubView by remember { mutableStateOf(HomeSubView.NONE) }

    BackHandler(enabled = activeSubView != HomeSubView.NONE) {
        activeSubView = HomeSubView.NONE
    }

    val liveCount = counts[MediaType.LIVE] ?: 0
    val movieCount = counts[MediaType.MOVIE] ?: 0
    val seriesCount = counts[MediaType.SERIES] ?: 0

    val tvCountText = "${formatCompactCount(liveCount)} ${strings.channelsCount}"
    val filmCountText = formatCompactCount(movieCount)
    val seriCountText = formatCompactCount(seriesCount)
    val isIndonesian = strings.watchHistory.startsWith("Riwayat", ignoreCase = true)
    val continueWatchingVod = remember(history) {
        history.filter { it.channel.mediaType == MediaType.MOVIE || it.channel.mediaType == MediaType.SERIES }
    }
    val shortHistoryLabel = if (isIndonesian) "Lanjutkan Menonton" else "Continue Watching"
    val shortFavoriteLabel = if (isIndonesian) "Favorit" else "Favourites"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
    ) {
        val isWideMode = isTvMode || isTvHardware

        if (c.isDark && isWideMode) {
            YlowstoneAmbientBlurBackdrop(modifier = Modifier.fillMaxSize())
        }

        if (!isWideMode) {
            PhoneEditorialHomeContent(
                playlistName = activePlaylist.name.ifBlank { "YlowstoneTV" },
                totalChannelsCount = liveCount + movieCount + seriesCount,
                channelsCountLabel = strings.channelsCount,
                continueWatching = continueWatchingVod,
                favourites = favourites,
                continueTitle = shortHistoryLabel,
                favouritesTitle = shortFavoriteLabel,
                emptyContinueHint = if (isIndonesian) "Belum ada film atau seri yang ditonton" else "No movies or series in progress",
                emptyFavouriteHint = strings.noFavouritesYet,
                verticalListState = verticalListState,
                continueRowState = continueRowState,
                favouritesRowState = favouritesRowState,
                onOpenPlaylists = onOpenPlaylists,
                onOpenProfileMenu = onOpenProfileMenu,
                onOpenAllContinue = { activeSubView = HomeSubView.CONTINUE_WATCHING },
                onOpenAllFavourites = { activeSubView = HomeSubView.FAVORITES },
                onPlayTaggedChannel = onPlayTaggedChannel,
                onLongPressTaggedChannel = onLongPressTaggedChannel
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 18.dp)
        ) {
            YlowstoneTopHeader(
                regionLabel = activePlaylist.name.ifBlank { "YlowstoneTV" },
                onPlaylistClick = onOpenPlaylists,
                onSettingsClick = onOpenProfileMenu
            )
            Spacer(modifier = Modifier.height(14.dp))

            run {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    YellowstoneCoverCard(
                        title = strings.liveTv,
                        badgeText = tvCountText,
                        coverRes = R.drawable.cover_home_tv,
                        icon = Icons.Rounded.LiveTv,
                        surfaceColor = HomeCoverColors.CardSurfaceTv,
                        ambientColor = if (c.isDark) HomeCoverColors.AmbientTv else c.accent,
                        isHero = true,
                        showLiveDot = true,
                        centerContent = true,
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight(),
                        onClick = { onOpenSection(MediaType.LIVE) }
                    )

                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        YellowstoneCoverCard(
                            title = strings.movies,
                            badgeText = filmCountText,
                            coverRes = R.drawable.cover_home_film,
                            icon = Icons.Rounded.Movie,
                            surfaceColor = HomeCoverColors.CardSurfaceFilm,
                            ambientColor = if (c.isDark) HomeCoverColors.AmbientFilm else c.accent,
                            isHero = false,
                            centerContent = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onClick = { onOpenSection(MediaType.MOVIE) }
                        )
                        YellowstoneCoverCard(
                            title = strings.series,
                            badgeText = seriCountText,
                            coverRes = R.drawable.cover_home_seri,
                            icon = Icons.Rounded.Tv,
                            surfaceColor = HomeCoverColors.CardSurfaceSeri,
                            ambientColor = if (c.isDark) HomeCoverColors.AmbientSeri else c.accent,
                            isHero = false,
                            centerContent = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onClick = { onOpenSection(MediaType.SERIES) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HomeNotchPill(
                        label = shortHistoryLabel,
                        count = continueWatchingVod.size.toString(),
                        thumbRes = R.drawable.cover_pill_continue,
                        icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = {
activeSubView = HomeSubView.CONTINUE_WATCHING }
                    )
                    HomeNotchPill(
                        label = shortFavoriteLabel,
                        count = favourites.size.toString(),
                        thumbRes = R.drawable.cover_pill_favorite,
                        icon = Icons.Rounded.Favorite,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = {
activeSubView = HomeSubView.FAVORITES }
                    )
                }
            }
        }
        }

        // PANEL KHUSUS RIWAYAT & FAVORIT
        AnimatedVisibility(
            visible = activeSubView != HomeSubView.NONE,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 12 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 12 }
        ) {
            HomeCollectionOverlay(
                subView = activeSubView,
                history = continueWatchingVod,
                favourites = favourites,
                favoriteViewMode = favoriteViewMode,
                activeChannel = activeChannel,
                isPlaying = isPlaying,
                strings = strings,
                isTvMode = isWideMode,
                onChangeFavoriteViewMode = onChangeFavoriteViewMode,
                onPlayTaggedChannel = onPlayTaggedChannel,
                onLongPressTaggedChannel = onLongPressTaggedChannel,
                onTogglePlayPause = onTogglePlayPause,
                onOpenActivePlayer = onOpenActivePlayer,
                onClose = { activeSubView = HomeSubView.NONE }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeCollectionOverlay(
    subView: HomeSubView,
    history: List<TaggedChannel>,
    favourites: List<TaggedChannel>,
    favoriteViewMode: FavoriteViewMode,
    activeChannel: ChannelItem?,
    isPlaying: Boolean,
    strings: AppStrings,
    isTvMode: Boolean,
    onChangeFavoriteViewMode: (FavoriteViewMode) -> Unit,
    onPlayTaggedChannel: (TaggedChannel, List<TaggedChannel>) -> Unit,
    onLongPressTaggedChannel: (TaggedChannel) -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenActivePlayer: () -> Unit,
    onClose: () -> Unit
) {
    val c = LocalYlowColors.current
    val isHistory = subView == HomeSubView.CONTINUE_WATCHING
    val title = if (isHistory) (if (strings.watchHistory.startsWith("Riwayat", ignoreCase = true)) "Lanjutkan Menonton" else "Continue Watching") else strings.favouriteChannels
    val isIndonesian = strings.watchHistory.startsWith("Riwayat", ignoreCase = true)
    var searchQuery by remember(subView) { mutableStateOf("") }
    var sortAz by remember(subView) { mutableStateOf(false) }

    val sourceList = if (isHistory) history else favourites
    val filteredList = remember(sourceList, searchQuery, sortAz) {
        val q = searchQuery.trim()
        val f = if (q.isEmpty()) sourceList
        else sourceList.filter {
            it.channel.name.contains(q, ignoreCase = true) ||
                it.channel.category.contains(q, ignoreCase = true)
        }
        if (sortAz) f.sortedBy { it.channel.name.lowercase() } else f
    }

    val favColumns = if (isTvMode) 4 else 2
    val favGridRows = remember(filteredList, favColumns) { filteredList.chunked(favColumns) }

    CinematicCollapsingListScreen(
        title = title,
        subtitleInfo = "ylowstone • ${filteredList.size} ${strings.channelsCount}",
        coverRes = if (isHistory) R.drawable.cover_home_seri else R.drawable.cover_home_tv,
        headerGradientColor = if (isHistory) c.headerGradSeri else c.headerGradNeutral,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        searchPlaceholder = if (isIndonesian) "Cari di daftar putar..." else "Search in playlist...",
        isTvMode = isTvMode,
        sortLabel = if (isIndonesian) "Urut" else "Sort",
        sortActive = sortAz,
        onSortClick = { sortAz = !sortAz },
        quickChips = buildList {
            add(QuickChipSpec("sort", if (isIndonesian) "Urutkan A-Z" else "Sort A-Z", sortAz) { sortAz = !sortAz })
            if (!isHistory) {
                add(QuickChipSpec("list_mode", strings.viewModeList, favoriteViewMode == FavoriteViewMode.LIST) {
                    onChangeFavoriteViewMode(FavoriteViewMode.LIST)
                })
                add(QuickChipSpec("grid_mode", strings.viewModeGrid, favoriteViewMode == FavoriteViewMode.GRID) {
                    onChangeFavoriteViewMode(FavoriteViewMode.GRID)
                })
            }
        },
        onPlayAction = {
            filteredList.firstOrNull()?.let { first ->
                onPlayTaggedChannel(first, filteredList)
            }
        },
        onBack = onClose,
        activeMiniChannel = activeChannel,
        isMiniPlaying = isPlaying,
        onMiniTogglePlay = onTogglePlayPause,
        onMiniOpenPlayer = onOpenActivePlayer
    ) {
        if (filteredList.isEmpty()) {
            item(key = "empty_collection") {
                EmptyHintCard(
                    icon = if (isHistory) Icons.Rounded.PlayCircleOutline else Icons.Rounded.FavoriteBorder,
                    message = if (isHistory) (if (isIndonesian) "Belum ada film atau seri yang ditonton" else "No movies or series in progress") else strings.noFavouritesYet
                )
            }
        } else if (isHistory) {
            items(filteredList, key = { "hist_${it.channel.streamUrl}" }) { tagged ->
                val isVodItem = tagged.channel.mediaType == MediaType.MOVIE || tagged.channel.mediaType == MediaType.SERIES
                val hasProgress = isVodItem && tagged.positionMs > 5_000L
                val progressFraction = if (hasProgress && tagged.durationMs > 0L) {
                    (tagged.positionMs.toFloat() / tagged.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val subText = if (hasProgress) {
                    val posStr = formatHistoryTime(tagged.positionMs)
                    val durStr = if (tagged.durationMs > 0L) " / ${formatHistoryTime(tagged.durationMs)}" else ""
                    "${tagged.playlistName} • $posStr$durStr"
                } else {
                    tagged.playlistName
                }
                val isActive = activeChannel?.streamUrl == tagged.channel.streamUrl

                BorderlessChannelRow(
                    ch = tagged.channel,
                    active = isActive,
                    isTvMode = isTvMode,
                    subtitleOverride = subText,
                    progressFraction = progressFraction,
                    showFavoriteIcon = false,
                    onClick = { onPlayTaggedChannel(tagged, filteredList) },
                    onLongClick = { onLongPressTaggedChannel(tagged) }
                )
            }
        } else if (favoriteViewMode == FavoriteViewMode.GRID) {
            items(favGridRows, key = { row -> "fav_grid_${row.first().channel.id}" }) { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { tagged ->
                        val isActive = activeChannel?.streamUrl == tagged.channel.streamUrl
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.surface)
                                .border(
                                    width = if (isActive) 1.3.dp else 0.7.dp,
                                    color = if (isActive) c.accent else c.glassBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .tvFocusBorder(isTvMode, RoundedCornerShape(14.dp))
                                .glossyPressPill(14f)
                                .combinedClickable(
                                    onClick = { onPlayTaggedChannel(tagged, filteredList) },
                                    onLongClick = { onLongPressTaggedChannel(tagged) }
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                ChannelLogoBox(tagged.channel.logoUrl, 40.dp, corner = 10.dp)
                                if (isActive) {
                                    Text("• LIVE", color = CinematicNoirTokens.LiveRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
                                }
                            }
                            Text(
                                text = tagged.channel.name,
                                style = TextStyle(
                                    fontFamily = YlowInterDisplay,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isActive) c.accent else c.textPrimary,
                                    lineHeight = 16.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tagged.playlistName,
                                style = TextStyle(
                                    fontFamily = YlowInterDisplay,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp,
                                    color = c.accent
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    repeat(favColumns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            items(filteredList, key = { "fav_list_${it.channel.id}" }) { tagged ->
                val isActive = activeChannel?.streamUrl == tagged.channel.streamUrl
                BorderlessChannelRow(
                    ch = tagged.channel,
                    active = isActive,
                    isTvMode = isTvMode,
                    subtitleOverride = "${tagged.playlistName} • ${tagged.channel.category}",
                    showFavoriteIcon = false,
                    onClick = { onPlayTaggedChannel(tagged, filteredList) },
                    onLongClick = { onLongPressTaggedChannel(tagged) }
                )
            }
        }
    }
}

@Composable
private fun YlowstoneTopHeader(
    regionLabel: String,
    onPlaylistClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            if (c.isDark) {
                Image(
                    painter = painterResource(id = R.drawable.ylowstone_wordmark),
                    contentDescription = "YlowstoneTV",
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier.height(22.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ylowstone",
                        style = TextStyle(
                            fontFamily = YlowInterDisplay,
                            fontWeight = FontWeight.Light,
                            fontSize = 20.sp,
                            color = c.textPrimary
                        )
                    )
                    Text(
                        text = "TV",
                        style = TextStyle(
                            fontFamily = YlowInterDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = c.textPrimary
                        )
                    )
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(HomeCoverColors.LiveRed)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .focusable()
                    .clickable(onClick = onPlaylistClick)
                    .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = regionLabel,
                    style = TextStyle(
                        fontFamily = YlowInterDisplay,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = c.textSecondary,
                        letterSpacing = 0.4.sp
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .focusable()
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ylowstone_y_emblem),
                contentDescription = "Ylowstone Menu",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

/**
 * Kartu Cover Utama (TV, FILM, SERI) dengan:
 * - Strict Tap Guard (geseran > 12dp dibatalkan otomatis agar tidak salah klik saat swipe di Beranda)
 * - Animasi "Tenggelam" (Dive-In sink: scale 0.94f + fade halus ~110ms) saat diketuk sah
 * - Dukungan tombol OK / Enter untuk remote Android TV
 */
@Composable
private fun YellowstoneCoverCard(
    title: String,
    badgeText: String,
    @DrawableRes coverRes: Int,
    icon: ImageVector,
    surfaceColor: Color,
    ambientColor: Color,
    isHero: Boolean,
    showLiveDot: Boolean = false,
    centerContent: Boolean = false,
    cornerRadius: Dp = 28.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalYlowColors.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxSlopPx = remember(density) { with(density) { 12.dp.toPx() } }

    var isFocused by remember { mutableStateOf(false) }
    var isSinking by remember { mutableStateOf(false) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    fun triggerDiveInTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapMs < 350L || isSinking) return
lastTapMs = now
        isSinking = true
        scope.launch {
            delay(110L)
            onClick()
            delay(180L)
            isSinking = false
        }
    }

    val scale by animateFloatAsState(
        targetValue = when {
            isSinking -> 0.935f
            isFocused -> 1.02f
            else -> 1f
        },
        animationSpec = tween(if (isSinking) 105 else 180),
        label = "cardDiveScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSinking) 0.78f else 1f,
        animationSpec = tween(105),
        label = "cardDiveAlpha"
    )
    val borderAlpha = if (isFocused) 0.92f else 0.34f
    val borderWidth = if (isFocused) 2.dp else 1.dp

    Surface(
        modifier = modifier
            .scale(scale)
            .alpha(contentAlpha)
            .clip(RoundedCornerShape(cornerRadius))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp && (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
                    triggerDiveInTap()
                    true
                } else false
            }
            .pointerInput(maxSlopPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    var movedTooFar = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (!movedTooFar && !change.isConsumed) {
                                triggerDiveInTap()
                            }
                            break
                        }
                        val dist = hypot(
                            (change.position.x - startPos.x).toDouble(),
                            (change.position.y - startPos.y).toDouble()
                        )
                        if (dist > maxSlopPx) {
                            movedTooFar = true
                        }
                    }
                }
            },
        shape = RoundedCornerShape(cornerRadius),
        color = surfaceColor,
        border = BorderStroke(
            width = borderWidth,
            brush = Brush.verticalGradient(
                colors = listOf(
                    ambientColor.copy(alpha = borderAlpha),
                    ambientColor.copy(alpha = borderAlpha * 0.35f)
                )
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = coverRes),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Ambien tipis & tint tema + scrim matte agar teks tetap terbaca tajam
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                c.coverTint,
                                Color.Black.copy(alpha = 0.14f),
                                Color.Black.copy(alpha = 0.64f)
                            )
                        )
                    )
            )

            if (centerContent) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isHero) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x990D0D0F))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(HomeCoverColors.LiveRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE",
                                    style = TextStyle(
                                        fontFamily = YlowInterDisplay,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        letterSpacing = 0.6.sp
                                    )
                                )
                            }
                            StatPillBadge(text = badgeText, showLiveDot = false)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color(0xFFEBE6DF).copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = title,
                        style = TextStyle(
                            fontFamily = YlowInterDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isHero) 40.sp else 26.sp,
                            letterSpacing = (-0.6).sp,
                            color = Color.White
                        )
                    )

                    if (!isHero) {
                        Spacer(modifier = Modifier.height(6.dp))
                        StatPillBadge(text = badgeText, showLiveDot = false)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(if (isHero) 44.dp else 38.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x730D0D0F))
                        .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isHero) Color(0xFFEBE6DF) else ambientColor.copy(alpha = 0.95f),
                        modifier = Modifier.size(if (isHero) 22.dp else 19.dp)
                    )
                }

                if (showLiveDot) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(18.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(HomeCoverColors.LiveRed)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                    ) {
                        StatPillBadge(text = badgeText, showLiveDot = false)
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                ) {
                    Text(
                        text = title,
                        style = TextStyle(
                            fontFamily = YlowInterDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isHero) 32.sp else 24.sp,
                            letterSpacing = (-0.5).sp,
                            color = Color.White
                        )
                    )
                    if (isHero) {
                        Spacer(modifier = Modifier.height(6.dp))
                        StatPillBadge(text = badgeText, showLiveDot = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPillBadge(text: String, showLiveDot: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0x8C0D0D0F))
            .border(0.8.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 4.dp)
    ) {
        if (showLiveDot) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(HomeCoverColors.LiveRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = TextStyle(
                fontFamily = YlowInterDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color(0xFFEBE6DF)
            )
        )
    }
}

/**
 * Kartu Ringkas Riwayat & Favorit dengan Strict Tap Guard (Swipe-vs-Tap Rejection > 12dp).
 */
@Composable
private fun UtilityCompactTile(
    label: String,
    count: String,
    icon: ImageVector,
    isHorizontal: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalYlowColors.current
    val density = LocalDensity.current
    val maxSlopPx = remember(density) { with(density) { 12.dp.toPx() } }

    var isFocused by remember { mutableStateOf(false) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    val borderColor = if (isFocused) c.accent else c.glassBorder

    fun triggerStrictTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapMs < 350L) return
        lastTapMs = now
        onClick()
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp && (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
                    triggerStrictTap()
                    true
                } else false
            }
            .pointerInput(maxSlopPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    var movedTooFar = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (!movedTooFar && !change.isConsumed) {
                                triggerStrictTap()
                            }
                            break
                        }
                        val dist = hypot(
                            (change.position.x - startPos.x).toDouble(),
                            (change.position.y - startPos.y).toDouble()
                        )
                        if (dist > maxSlopPx) {
                            movedTooFar = true
                        }
                    }
                }
            },
        shape = RoundedCornerShape(24.dp),
        color = c.surface,
        border = BorderStroke(if (isFocused) 2.dp else 1.dp, borderColor)
    ) {
        if (isHorizontal) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = c.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = label,
                        style = TextStyle(
                            fontFamily = YlowInterDisplay,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = c.textPrimary
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(c.surfaceElevated)
                        .border(0.8.dp, c.glassBorder, CircleShape)
                        .padding(horizontal = 11.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = count,
                        style = TextStyle(
                            fontFamily = YlowInterDisplay,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = c.textPrimary
                        )
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = c.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = count,
                    style = TextStyle(
                        fontFamily = YlowInterDisplay,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = c.textPrimary
                    )
                )
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = YlowInterDisplay,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = c.textSecondary
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyHintCard(icon: ImageVector, message: String) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(0.8.dp, c.glassBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
        Text(message, color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

private fun formatCompactCount(n: Int): String = when {
    n >= 1000 -> String.format(java.util.Locale("id", "ID"), "%.1fk", n / 1000f)
    else -> n.toString()
}

private fun formatHistoryTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%02d:%02d", m, s)
}

private fun isAndroidTvDevice(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}


@Composable
private fun YlowstoneAmbientBlurBackdrop(modifier: Modifier = Modifier) {
    val c = LocalYlowColors.current
    Box(modifier = modifier.background(c.bg)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.18f)
                    .blur(64.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .alpha(0.34f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.cover_home_tv),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(0.46f).fillMaxWidth()
                )
                Row(modifier = Modifier.weight(0.54f).fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.cover_home_film),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Image(
                        painter = painterResource(id = R.drawable.cover_home_seri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            c.bg.copy(alpha = 0.62f),
                            c.bg.copy(alpha = 0.44f),
                            c.bg.copy(alpha = 0.78f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun HomeNotchPill(
    label: String,
    count: String,
    @DrawableRes thumbRes: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = LocalYlowColors.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(160),
        label = "notchPillScale"
    )
    val bgColor = if (c.isDark) Color(0xFF000000) else c.surface
    val borderColor = if (isFocused) c.accent else c.glassBorder

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(if (isFocused) 1.8.dp else 0.9.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = thumbRes),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(52.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(9.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = YlowInterDisplay,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = c.textPrimary,
                        letterSpacing = (-0.1).sp
                    )
                )
                Text(
                    text = "• $count",
                    maxLines = 1,
                    style = TextStyle(
                        fontFamily = YlowInterDisplay,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = c.textSecondary
                    )
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.textPrimary.copy(alpha = 0.88f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


/**
 * Beranda Editorial Mode Ponsel/Tablet:
 * - Collapsing Large Title ("Home" 34sp Bold) + indikator status playlist/server yang menyusut ke bar frosted glass
 * - Carousel horizontal borderless 14dp: Continue Watching (175x230dp + 3dp LinearProgressIndicator) & Favourites (155x155dp #141416 + 16dp padding)
 * - Bottom contentPadding 104dp agar konten paling bawah tidak tertutup Floating Liquid Glass Dock
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneEditorialHomeContent(
    playlistName: String,
    totalChannelsCount: Int,
    channelsCountLabel: String,
    continueWatching: List<TaggedChannel>,
    favourites: List<TaggedChannel>,
    continueTitle: String,
    favouritesTitle: String,
    emptyContinueHint: String,
    emptyFavouriteHint: String,
    verticalListState: LazyListState,
    continueRowState: LazyListState,
    favouritesRowState: LazyListState,
    onOpenPlaylists: () -> Unit,
    onOpenProfileMenu: () -> Unit,
    onOpenAllContinue: () -> Unit,
    onOpenAllFavourites: () -> Unit,
    onPlayTaggedChannel: (TaggedChannel, List<TaggedChannel>) -> Unit,
    onLongPressTaggedChannel: (TaggedChannel) -> Unit
) {
    val c = LocalYlowColors.current
    val isCollapsed by remember {
        derivedStateOf {
            verticalListState.firstVisibleItemIndex > 0 || verticalListState.firstVisibleItemScrollOffset > 36
        }
    }
    val largeTitleAlpha by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 1f,
        animationSpec = tween(180),
        label = "homeLargeTitleAlpha"
    )
    val serverSummary = "$playlistName • ${formatCompactCount(totalChannelsCount)} $channelsCountLabel"

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = verticalListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 60.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Collapsing Large Title ("Home" 34sp Bold) + Status Server Ringkas
            item(key = "editorial_large_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .alpha(largeTitleAlpha),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Home",
                            color = c.textPrimary,
                            fontFamily = YlowInterDisplay,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.6).sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onOpenPlaylists)
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Text(
                                text = serverSummary,
                                color = c.textSecondary,
                                fontFamily = YlowInterDisplay,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Bagian 1: Continue Watching (Movies & Series)
            item(key = "editorial_continue_section") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditorialSectionHeader(
                        title = continueTitle,
                        count = continueWatching.size,
                        onClick = onOpenAllContinue
                    )
                    if (continueWatching.isEmpty()) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            EmptyHintCard(
                                icon = Icons.Rounded.PlayCircleOutline,
                                message = emptyContinueHint
                            )
                        }
                    } else {
                        LazyRow(
                            state = continueRowState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(
                                items = continueWatching,
                                key = { "cw_${it.channel.streamUrl}" }
                            ) { tagged ->
                                EditorialPosterCard(
                                    tagged = tagged,
                                    onClick = { onPlayTaggedChannel(tagged, continueWatching) },
                                    onLongClick = { onLongPressTaggedChannel(tagged) }
                                )
                            }
                        }
                    }
                }
            }

            // Bagian 2: Favourites (Live TV & Saluran Favorit 1:1 #141416)
            item(key = "editorial_favourites_section") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditorialSectionHeader(
                        title = favouritesTitle,
                        count = favourites.size,
                        onClick = onOpenAllFavourites
                    )
                    if (favourites.isEmpty()) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            EmptyHintCard(
                                icon = Icons.Rounded.FavoriteBorder,
                                message = emptyFavouriteHint
                            )
                        }
                    } else {
                        LazyRow(
                            state = favouritesRowState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(
                                items = favourites,
                                key = { "fav_${it.channel.id}_${it.channel.streamUrl}" }
                            ) { tagged ->
                                EditorialFavouriteSquareCard(
                                    tagged = tagged,
                                    onClick = { onPlayTaggedChannel(tagged, favourites) },
                                    onLongClick = { onLongPressTaggedChannel(tagged) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sticky Frosted Top Bar (menyusut mulus saat konten digulir ke bawah)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .then(
                    if (isCollapsed) {
                        Modifier
                            .liquidGlassSurface(
                                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                                isDark = c.isDark,
                                baseColor = c.navBarBg
                            )
                    } else {
                        Modifier.background(Color.Transparent)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            YlowstoneTopHeader(
                regionLabel = if (isCollapsed) "Home • $playlistName" else playlistName,
                onPlaylistClick = onOpenPlaylists,
                onSettingsClick = onOpenProfileMenu
            )
        }
    }
}

@Composable
private fun EditorialSectionHeader(
    title: String,
    count: Int,
    onClick: () -> Unit
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = c.textPrimary,
                fontFamily = YlowInterDisplay,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        if (count > 0) {
            Text(
                text = "• $count",
                color = c.textSecondary,
                fontFamily = YlowInterDisplay,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorialPosterCard(
    tagged: TaggedChannel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val ch = tagged.channel
    val cardShape = RoundedCornerShape(14.dp)
    val progressFraction = if (tagged.positionMs > 5_000L && tagged.durationMs > 0L) {
        (tagged.positionMs.toFloat() / tagged.durationMs.toFloat()).coerceIn(0.03f, 1f)
    } else 0f
    val subtitle = buildString {
        if (ch.category.isNotBlank()) {
            append(ch.category)
        } else {
            append(tagged.playlistName)
        }
        if (tagged.positionMs > 5_000L) {
            append(" • ")
            append(formatHistoryTime(tagged.positionMs))
        }
    }
    val fallbackRes = if (ch.mediaType == MediaType.SERIES) R.drawable.cover_home_seri else R.drawable.cover_home_film

    Column(
        modifier = Modifier
            .width(175.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .width(175.dp)
                .height(230.dp)
                .clip(cardShape)
                .background(Color(0xFF141416))
        ) {
            if (ch.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ch.logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = ch.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = fallbackRes),
                    contentDescription = ch.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                        )
                    )
            )

            if (progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = c.accent,
                    trackColor = Color.White.copy(alpha = 0.24f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = ch.name,
            color = c.textPrimary,
            fontFamily = YlowInterDisplay,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = Color(0xFF8E8E93),
            fontFamily = YlowInterDisplay,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorialFavouriteSquareCard(
    tagged: TaggedChannel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val ch = tagged.channel
    val cardShape = RoundedCornerShape(14.dp)
    val subtitle = ch.category.ifBlank { tagged.playlistName }

    Column(
        modifier = Modifier
            .width(155.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .size(155.dp)
                .clip(cardShape)
                .background(Color(0xFF141416))
        ) {
            if (ch.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ch.logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = ch.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else {
                Icon(
                    imageVector = if (ch.mediaType == MediaType.LIVE) Icons.Rounded.LiveTv else Icons.Rounded.Favorite,
                    contentDescription = ch.name,
                    tint = c.accent.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.Center)
                )
            }

            if (ch.mediaType == MediaType.LIVE) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(HomeCoverColors.LiveRed)
                    )
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontFamily = YlowInterDisplay,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = ch.name,
            color = c.textPrimary,
            fontFamily = YlowInterDisplay,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = Color(0xFF8E8E93),
            fontFamily = YlowInterDisplay,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
