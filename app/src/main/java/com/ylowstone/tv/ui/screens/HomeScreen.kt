package com.ylowstone.tv.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.R
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.components.ChannelLogoBox
import com.ylowstone.tv.ui.components.ProfileYBadgeButton
import com.ylowstone.tv.ui.components.glossyPressPill
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    activePlaylist: PlaylistProfile,
    counts: Map<MediaType, Int>,
    favourites: List<TaggedChannel>,
    history: List<TaggedChannel>,
    watchGroups: Map<String, List<TaggedChannel>>,
    favoriteViewMode: FavoriteViewMode,
    strings: AppStrings,
    isTvMode: Boolean,
    onOpenSection: (MediaType) -> Unit,
    onOpenSearch: () -> Unit,
    onChangeFavoriteViewMode: (FavoriteViewMode) -> Unit,
    onPlayTaggedChannel: (TaggedChannel, List<TaggedChannel>) -> Unit,
    onLongPressTaggedChannel: (TaggedChannel) -> Unit,
    onRemoveFromWatchGroup: (groupName: String, streamUrl: String) -> Unit,
    onOpenProfileMenu: () -> Unit
) {
    val c = LocalYlowColors.current
    var watchGroupsExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedWatchGroup by rememberSaveable { mutableStateOf("ALL") }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var favouritesExpanded by rememberSaveable { mutableStateOf(false) }

    val favColumns = if (isTvMode) 3 else 2
    val favGridRows = remember(favourites, favColumns) { favourites.chunked(favColumns) }

    val totalGroupedCount = remember(watchGroups) { watchGroups.values.sumOf { it.size } }
    val groupNames = remember(watchGroups) { watchGroups.keys.toList() }
    val activeWatchGroupItems: List<Pair<String, TaggedChannel>> = remember(watchGroups, selectedWatchGroup) {
        if (selectedWatchGroup == "ALL" || !watchGroups.containsKey(selectedWatchGroup)) {
            watchGroups.entries.flatMap { (gName, items) -> items.map { gName to it } }
        } else {
            watchGroups[selectedWatchGroup].orEmpty().map { selectedWatchGroup to it }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- Top Header: Logo Banner YlowstoneTV • + Nama Playlist Aktif | Cari & Profil ----
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = if (c.isDark) {
                            Modifier
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0E1014))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ylowstone_wordmark),
                            contentDescription = "YlowstoneTV",
                            contentScale = ContentScale.FillHeight,
                            modifier = Modifier.height(25.dp)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(c.accent)
                        )
                        Text(
                            text = activePlaylist.name,
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c.surface)
                            .border(1.dp, c.glassBorder, CircleShape)
                            .tvFocusBorder(isTvMode, CircleShape)
                            .clickable(onClick = onOpenSearch),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = strings.search,
                            tint = c.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    ProfileYBadgeButton(isTvMode = isTvMode, onClick = onOpenProfileMenu)
                }
            }
        }

        // ---- Section 1: 3 Kotak Besar Utama (TV, FILM, SERI) ----
        item {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MainCategoryHeroCard(
                    title = strings.liveTv,
                    count = counts[MediaType.LIVE] ?: 0,
                    icon = Icons.Rounded.LiveTv,
                    featured = true,
                    isTvMode = isTvMode,
                    onClick = { onOpenSection(MediaType.LIVE) },
                    modifier = Modifier.weight(1f)
                )
                MainCategoryHeroCard(
                    title = strings.movies,
                    count = counts[MediaType.MOVIE] ?: 0,
                    icon = Icons.Rounded.Movie,
                    featured = false,
                    isTvMode = isTvMode,
                    onClick = { onOpenSection(MediaType.MOVIE) },
                    modifier = Modifier.weight(1f)
                )
                MainCategoryHeroCard(
                    title = strings.series,
                    count = counts[MediaType.SERIES] ?: 0,
                    icon = Icons.Rounded.Tv,
                    featured = false,
                    isTvMode = isTvMode,
                    onClick = { onOpenSection(MediaType.SERIES) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ---- Section 2: Kategori Tontonan (Sports, Event Pertandingan, Daftar Tontonan) - Show & Hide ----
        item {
            CollapsibleSectionBar(
                icon = Icons.Rounded.Bookmarks,
                title = strings.watchGroupsTitle,
                count = totalGroupedCount,
                expanded = watchGroupsExpanded,
                expandLabel = strings.openWatchGroups,
                collapseLabel = strings.closeWatchGroups,
                accentWhenCollapsed = totalGroupedCount > 0,
                isTvMode = isTvMode,
                onToggle = { watchGroupsExpanded = !watchGroupsExpanded }
            )
        }

        if (watchGroupsExpanded) {
            if (watchGroups.isEmpty()) {
                item { EmptyHintCard(Icons.Rounded.Bookmarks, strings.noWatchGroupsYet) }
            } else {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            val allSelected = selectedWatchGroup == "ALL" || !watchGroups.containsKey(selectedWatchGroup)
                            WatchGroupFilterChip(
                                label = "${strings.allFilter} ($totalGroupedCount)",
                                selected = allSelected,
                                onClick = { selectedWatchGroup = "ALL" }
                            )
                        }
                        items(groupNames, key = { "grp_chip_$it" }) { grp ->
                            val count = watchGroups[grp]?.size ?: 0
                            WatchGroupFilterChip(
                                label = "$grp ($count)",
                                selected = selectedWatchGroup == grp,
                                onClick = { selectedWatchGroup = grp }
                            )
                        }
                    }
                }

                val queueForPlayback = activeWatchGroupItems.map { it.second }
                items(
                    items = activeWatchGroupItems,
                    key = { (gName, tagged) -> "wg_${gName}_${tagged.channel.streamUrl}" }
                ) { (gName, tagged) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.surface)
                            .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                            .tvFocusBorder(isTvMode, RoundedCornerShape(14.dp))
                            .glossyPressPill(14f)
                            .combinedClickable(
                                onClick = { onPlayTaggedChannel(tagged, queueForPlayback) },
                                onLongClick = { onLongPressTaggedChannel(tagged) }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ChannelLogoBox(tagged.channel.logoUrl, 38.dp, corner = 10.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tagged.channel.name,
                                color = c.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(c.accentContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = gName,
                                        color = c.accent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = tagged.playlistName,
                                    color = c.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { onRemoveFromWatchGroup(gName, tagged.channel.streamUrl) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = strings.delete,
                                tint = c.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- Section 3: Riwayat Tontonan (Bisa Show & Hide) ----
        item {
            CollapsibleSectionBar(
                icon = Icons.Rounded.History,
                title = strings.watchHistory,
                count = history.size,
                expanded = historyExpanded,
                expandLabel = strings.openWatchHistory,
                collapseLabel = strings.closeWatchHistory,
                accentWhenCollapsed = false,
                isTvMode = isTvMode,
                onToggle = { historyExpanded = !historyExpanded }
            )
        }

        if (historyExpanded) {
            if (history.isEmpty()) {
                item { EmptyHintCard(Icons.Rounded.History, strings.noHistoryYet) }
            } else {
                items(history.take(20), key = { "hist_${it.channel.streamUrl}" }) { tagged ->
                    val isVodItem = tagged.channel.mediaType == MediaType.MOVIE || tagged.channel.mediaType == MediaType.SERIES
                    val hasProgress = isVodItem && tagged.positionMs > 5_000L
                    val progressFraction = if (hasProgress && tagged.durationMs > 0L) {
                        (tagged.positionMs.toFloat() / tagged.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.surface)
                            .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                            .tvFocusBorder(isTvMode, RoundedCornerShape(14.dp))
                            .glossyPressPill(14f)
                            .combinedClickable(
                                onClick = { onPlayTaggedChannel(tagged, history) },
                                onLongClick = { onLongPressTaggedChannel(tagged) }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ChannelLogoBox(tagged.channel.logoUrl, 40.dp, corner = 10.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tagged.channel.name,
                                    color = c.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                val subText = if (hasProgress) {
                                    val posStr = formatHistoryTime(tagged.positionMs)
                                    val durStr = if (tagged.durationMs > 0L) " / ${formatHistoryTime(tagged.durationMs)}" else ""
                                    "${tagged.playlistName} • Lanjut $posStr$durStr"
                                } else {
                                    "${tagged.playlistName} • ${tagged.channel.category}"
                                }
                                Text(
                                    text = subText,
                                    color = if (hasProgress) c.accent else c.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (hasProgress) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
                        }
                        if (hasProgress && progressFraction > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(c.surfaceElevated)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFraction)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(c.accent)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Section 4: Saluran Favorit (Bisa Show & Hide + Kotak / Daftar) ----
        item {
            CollapsibleSectionBar(
                icon = Icons.Rounded.Favorite,
                title = strings.favouriteChannels,
                count = favourites.size,
                expanded = favouritesExpanded,
                expandLabel = strings.openFavourites,
                collapseLabel = strings.closeFavourites,
                accentWhenCollapsed = true,
                isTvMode = isTvMode,
                onToggle = { favouritesExpanded = !favouritesExpanded }
            )
        }

        if (favouritesExpanded) {
            if (favourites.isEmpty()) {
                item { EmptyHintCard(Icons.Rounded.FavoriteBorder, strings.noFavouritesYet) }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.surface)
                                .border(1.dp, c.glassBorder, RoundedCornerShape(10.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            FavoriteModeToggleChip(
                                icon = Icons.Rounded.GridView,
                                label = strings.viewModeGrid,
                                selected = favoriteViewMode == FavoriteViewMode.GRID,
                                onClick = { onChangeFavoriteViewMode(FavoriteViewMode.GRID) }
                            )
                            FavoriteModeToggleChip(
                                icon = Icons.Rounded.ViewList,
                                label = strings.viewModeList,
                                selected = favoriteViewMode == FavoriteViewMode.LIST,
                                onClick = { onChangeFavoriteViewMode(FavoriteViewMode.LIST) }
                            )
                        }
                    }
                }

                if (favoriteViewMode == FavoriteViewMode.GRID) {
                    items(favGridRows, key = { row -> "fav_grid_${row.first().channel.id}" }) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { tagged ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(c.surface)
                                        .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                                        .tvFocusBorder(isTvMode, RoundedCornerShape(16.dp))
                                        .glossyPressPill(16f)
                                        .combinedClickable(
                                            onClick = { onPlayTaggedChannel(tagged, favourites) },
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
                                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
                                    }
                                    Text(
                                        text = tagged.channel.name,
                                        color = c.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(c.accentContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = tagged.playlistName,
                                            color = c.accent,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            repeat(favColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(favourites, key = { "fav_list_${it.channel.id}" }) { tagged ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.surface)
                                .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                                .tvFocusBorder(isTvMode, RoundedCornerShape(14.dp))
                                .glossyPressPill(14f)
                                .combinedClickable(
                                    onClick = { onPlayTaggedChannel(tagged, favourites) },
                                    onLongClick = { onLongPressTaggedChannel(tagged) }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ChannelLogoBox(tagged.channel.logoUrl, 36.dp, corner = 8.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tagged.channel.name,
                                    color = c.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${tagged.playlistName} • ${tagged.channel.category}",
                                    color = c.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchGroupFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = LocalYlowColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.accent else c.surface)
            .border(
                width = 1.dp,
                color = if (selected) c.accent else c.glassBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) c.onAccent else c.textPrimary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun CollapsibleSectionBar(
    icon: ImageVector,
    title: String,
    count: Int,
    expanded: Boolean,
    expandLabel: String,
    collapseLabel: String,
    accentWhenCollapsed: Boolean,
    isTvMode: Boolean,
    onToggle: () -> Unit
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(
                width = 1.dp,
                color = if (expanded) c.accent.copy(alpha = 0.55f) else c.glassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .tvFocusBorder(isTvMode, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (expanded || accentWhenCollapsed) c.accent else c.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                color = c.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.surfaceElevated)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = count.toString(),
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (expanded) collapseLabel else expandLabel,
                color = c.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FavoriteModeToggleChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) c.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) c.onAccent else c.textSecondary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = if (selected) c.onAccent else c.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun MainCategoryHeroCard(
    title: String,
    count: Int,
    icon: ImageVector,
    featured: Boolean,
    isTvMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    val fg = if (featured) c.onAccent else c.textPrimary
    val subFg = if (featured) c.onAccent.copy(alpha = 0.85f) else c.textSecondary
    val cardHeight = if (isTvMode) 204.dp else 178.dp

    val bgBrush = if (featured) {
        Brush.verticalGradient(
            colors = listOf(c.accent, c.accent.copy(alpha = 0.82f))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(c.surfaceElevated, c.surface)
        )
    }

    Column(
        modifier = modifier
            .height(cardHeight)
            .clip(RoundedCornerShape(24.dp))
            .background(bgBrush)
            .border(
                width = if (featured) 1.6.dp else 1.2.dp,
                color = if (featured) c.accent else c.glassBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .tvFocusBorder(isTvMode, RoundedCornerShape(24.dp))
            .glossyPressPill(24f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(if (isTvMode) 64.dp else 56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (featured) Color.Black.copy(alpha = 0.16f) else c.accentContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (featured) fg else c.accent,
                modifier = Modifier.size(if (isTvMode) 36.dp else 32.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = fg,
                fontSize = if (isTvMode) 22.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (featured) Color.Black.copy(alpha = 0.18f) else c.bg.copy(alpha = 0.55f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = formatCompactCount(count),
                    color = subFg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatCompactCount(n: Int): String = when {
    n >= 1000 -> String.format("%.1fk", n / 1000f)
    else -> n.toString()
}

@Composable
private fun EmptyHintCard(icon: ImageVector, message: String) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        Text(message, color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

private fun formatHistoryTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%02d:%02d", m, s)
}
