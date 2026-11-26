package com.ylowstone.tv.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

private object HomeCoverColors {
    val AmoledBlack = Color(0xFF000000)
    val CardSurfaceTv = Color(0xFF14110D)
    val CardSurfaceFilm = Color(0xFF121718)
    val CardSurfaceSeri = Color(0xFF161412)
    val UtilitySurface = Color(0xFF111113)

    // 3 warna ambien halus berbeda yang tetap senada
    val AmbientTv = Color(0xFFC88435)      // Amber-Bronze
    val AmbientFilm = Color(0xFF6E8B8A)    // Sage-Slate
    val AmbientSeri = Color(0xFFB09878)    // Champagne-Taupe
    val LiveRed = Color(0xFFFA263E)
    val MutedText = Color(0xFF9C968F)
}

private enum class HomeSubView { NONE, HISTORY, FAVORITES }

/**
 * Beranda YlowstoneTV v2.4.1 — 1 Layar Penuh (Zero-Scroll) untuk Ponsel (9:16) & TV (16:9)
 * dengan 3 cover WebP kustom, font InterDisplay, serta panel Collapsing Borderless untuk Riwayat & Favorit.
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
    onOpenProfileMenu: () -> Unit
) {
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
    val shortHistoryLabel = if (isIndonesian) "Riwayat" else "History"
    val shortFavoriteLabel = if (isIndonesian) "Favorit" else "Favourites"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeCoverColors.AmoledBlack)
            .statusBarsPadding()
    ) {
        val isWideMode = isTvMode || isTvHardware || maxWidth > maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isWideMode) 28.dp else 18.dp,
                    vertical = if (isWideMode) 18.dp else 14.dp
                )
        ) {
            YlowstoneTopHeader(
                regionLabel = activePlaylist.name.ifBlank { "YlowstoneTV" },
                onPlaylistClick = onOpenPlaylists,
                onSettingsClick = onOpenProfileMenu
            )
            Spacer(modifier = Modifier.height(if (isWideMode) 14.dp else 16.dp))

            if (isWideMode) {
                // MODE TV / LANDSCAPE (16:9) — Kiri TV Besar, Kanan FILM & SERI
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    YellowstoneCoverCard(
                        title = strings.liveTv,
                        badgeText = tvCountText,
                        coverRes = R.drawable.cover_yellowstone_tv,
                        icon = Icons.Rounded.LiveTv,
                        surfaceColor = HomeCoverColors.CardSurfaceTv,
                        ambientColor = HomeCoverColors.AmbientTv,
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
                            coverRes = R.drawable.cover_yellowstone_film,
                            icon = Icons.Rounded.Movie,
                            surfaceColor = HomeCoverColors.CardSurfaceFilm,
                            ambientColor = HomeCoverColors.AmbientFilm,
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
                            coverRes = R.drawable.cover_yellowstone_seri,
                            icon = Icons.Rounded.Tv,
                            surfaceColor = HomeCoverColors.CardSurfaceSeri,
                            ambientColor = HomeCoverColors.AmbientSeri,
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

                // Baris Bawah TV: Riwayat & Favorit 50/50
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    UtilityCompactTile(
                        label = shortHistoryLabel,
                        count = history.size.toString(),
                        icon = Icons.Rounded.History,
                        isHorizontal = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { activeSubView = HomeSubView.HISTORY }
                    )
                    UtilityCompactTile(
                        label = shortFavoriteLabel,
                        count = favourites.size.toString(),
                        icon = Icons.Rounded.Favorite,
                        isHorizontal = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { activeSubView = HomeSubView.FAVORITES }
                    )
                }
            } else {
                // MODE PONSEL (9:16) — Penuh Layar Tanpa Ruang Kosong
                YellowstoneCoverCard(
                    title = strings.liveTv,
                    badgeText = tvCountText,
                    coverRes = R.drawable.cover_yellowstone_tv,
                    icon = Icons.Rounded.LiveTv,
                    surfaceColor = HomeCoverColors.CardSurfaceTv,
                    ambientColor = HomeCoverColors.AmbientTv,
                    isHero = true,
                    showLiveDot = true,
                    centerContent = false,
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxWidth(),
                    onClick = { onOpenSection(MediaType.LIVE) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .weight(0.41f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    YellowstoneCoverCard(
                        title = strings.movies,
                        badgeText = filmCountText,
                        coverRes = R.drawable.cover_yellowstone_film,
                        icon = Icons.Rounded.Movie,
                        surfaceColor = HomeCoverColors.CardSurfaceFilm,
                        ambientColor = HomeCoverColors.AmbientFilm,
                        isHero = false,
                        centerContent = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onOpenSection(MediaType.MOVIE) }
                    )
                    YellowstoneCoverCard(
                        title = strings.series,
                        badgeText = seriCountText,
                        coverRes = R.drawable.cover_yellowstone_seri,
                        icon = Icons.Rounded.Tv,
                        surfaceColor = HomeCoverColors.CardSurfaceSeri,
                        ambientColor = HomeCoverColors.AmbientSeri,
                        isHero = false,
                        centerContent = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onOpenSection(MediaType.SERIES) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2 Kotak Persegi Membulat Kecil: Riwayat & Favorit
                Row(
                    modifier = Modifier
                        .weight(0.19f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UtilityCompactTile(
                        label = shortHistoryLabel,
                        count = history.size.toString(),
                        icon = Icons.Rounded.History,
                        isHorizontal = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { activeSubView = HomeSubView.HISTORY }
                    )
                    UtilityCompactTile(
                        label = shortFavoriteLabel,
                        count = favourites.size.toString(),
                        icon = Icons.Rounded.Favorite,
                        isHorizontal = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { activeSubView = HomeSubView.FAVORITES }
                    )
                }
            }
        }

        // PANEL KHUSUS RIWAYAT & FAVORIT (Collapsing Header + Borderless Rows)
        AnimatedVisibility(
            visible = activeSubView != HomeSubView.NONE,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 12 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 12 }
        ) {
            HomeCollectionOverlay(
                subView = activeSubView,
                history = history,
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
    val isHistory = subView == HomeSubView.HISTORY
    val title = if (isHistory) strings.watchHistory else strings.favouriteChannels
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
        coverRes = if (isHistory) R.drawable.cover_yellowstone_seri else R.drawable.cover_yellowstone_tv,
        headerGradientColor = if (isHistory) CinematicNoirTokens.GradSeri else CinematicNoirTokens.GradNeutral,
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
                    icon = if (isHistory) Icons.Rounded.History else Icons.Rounded.FavoriteBorder,
                    message = if (isHistory) strings.noHistoryYet else strings.noFavouritesYet
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
                    "${tagged.playlistName} • Lanjut $posStr$durStr"
                } else {
                    "${tagged.playlistName} • ${tagged.channel.category}"
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
                                .background(Color(0xFF0E0D0C))
                                .border(
                                    width = if (isActive) 1.3.dp else 0.7.dp,
                                    color = if (isActive) CinematicNoirTokens.ChampagnePlay else CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.22f),
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
                                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = CinematicNoirTokens.ChampagnePlay, modifier = Modifier.size(15.dp))
                                }
                            }
                            Text(
                                text = tagged.channel.name,
                                style = TextStyle(
                                    fontFamily = YlowInterDisplay,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isActive) CinematicNoirTokens.ChampagneHighlight else CinematicNoirTokens.TextPrimary,
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
                                    color = CinematicNoirTokens.ChampagneAccent
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Image(
                painter = painterResource(id = R.drawable.ylowstone_wordmark),
                contentDescription = "YlowstoneTV",
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.height(22.dp)
            )
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
                        .background(Color(0xFF7D8590))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = regionLabel,
                    style = TextStyle(
                        fontFamily = YlowInterDisplay,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = HomeCoverColors.MutedText,
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
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(180),
        label = "cardFocusScale"
    )
    val borderAlpha = if (isFocused) 0.88f else 0.30f
    val borderWidth = if (isFocused) 2.dp else 1.dp

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(cornerRadius))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ambientColor.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.14f),
                                Color.Black.copy(alpha = 0.62f)
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

@Composable
private fun UtilityCompactTile(
    label: String,
    count: String,
    icon: ImageVector,
    isHorizontal: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) CinematicNoirTokens.ChampagnePlay else Color.White.copy(alpha = 0.14f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = HomeCoverColors.UtilitySurface,
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
                        tint = Color(0xFFD6D0C8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = label,
                        style = TextStyle(
                            fontFamily = YlowInterDisplay,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xFFEBE6DF)
                        )
                    )
                }
                StatPillBadge(text = count, showLiveDot = false)
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
                        .background(Color(0xFF1B1B1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = Color(0xFFD6D0C8),
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
                        color = Color.White
                    )
                )
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = YlowInterDisplay,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = HomeCoverColors.MutedText
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyHintCard(icon: ImageVector, message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0E0D0C))
            .border(0.8.dp, CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = CinematicNoirTokens.ChampagneAccent, modifier = Modifier.size(22.dp))
        Text(message, color = CinematicNoirTokens.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
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
