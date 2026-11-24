package com.ylowstone.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.components.ChannelLogoBox
import com.ylowstone.tv.ui.components.ProfileYBadgeButton
import com.ylowstone.tv.ui.components.glossyPressPill
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors

@Composable
fun HomeScreen(
    activePlaylist: PlaylistProfile,
    allPlaylists: List<PlaylistProfile>,
    counts: Map<MediaType, Int>,
    favourites: List<TaggedChannel>,
    history: List<TaggedChannel>,
    strings: AppStrings,
    isTvMode: Boolean,
    onSwitchPlaylist: (PlaylistProfile) -> Unit,
    onOpenPlaylistManager: () -> Unit,
    onOpenSection: (MediaType) -> Unit,
    onPlayTaggedChannel: (TaggedChannel, List<TaggedChannel>) -> Unit,
    onOpenProfileMenu: () -> Unit
) {
    val c = LocalYlowColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ---- Top Header (FotMob crisp bar: Brand + Active Playlist left, Refresh + Yellow-Y right) ----
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "YlowstoneTV",
                        color = c.textPrimary,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                ProfileYBadgeButton(isTvMode = isTvMode, onClick = onOpenProfileMenu)
            }
        }

        // ---- Section 1: Saved Playlists Carousel ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeaderRow(
                    title = strings.myPlaylists,
                    actionLabel = strings.managePlaylists,
                    onAction = onOpenPlaylistManager
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(allPlaylists, key = { it.id }) { pl ->
                        val active = pl.id == activePlaylist.id
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) c.accentContainer else c.surface)
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) c.accent else c.glassBorder,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .tvFocusBorder(isTvMode, RoundedCornerShape(16.dp))
                                .glossyPressPill(16f)
                                .clickable { onSwitchPlaylist(pl) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (active) c.accent else c.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pl.name.take(2).uppercase(),
                                    color = if (active) c.onAccent else c.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                            Column {
                                Text(
                                    text = pl.name,
                                    color = if (active) c.accent else c.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(pl.type.name, color = c.textSecondary, fontSize = 10.sp)
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.surface)
                                .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                                .tvFocusBorder(isTvMode, RoundedCornerShape(16.dp))
                                .glossyPressPill(16f)
                                .clickable(onClick = onOpenPlaylistManager)
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                            Text(strings.addPlaylist, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ---- Section 2: Explore Quick Cards (Clean FotMob Flat Rounded Cards) ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.explore, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExploreTileCard(
                        title = strings.liveTv,
                        count = counts[MediaType.LIVE] ?: 0,
                        icon = Icons.Rounded.LiveTv,
                        featured = true,
                        isTvMode = isTvMode,
                        onClick = { onOpenSection(MediaType.LIVE) },
                        modifier = Modifier.weight(1f)
                    )
                    ExploreTileCard(
                        title = strings.movies,
                        count = counts[MediaType.MOVIE] ?: 0,
                        icon = Icons.Rounded.Movie,
                        featured = false,
                        isTvMode = isTvMode,
                        onClick = { onOpenSection(MediaType.MOVIE) },
                        modifier = Modifier.weight(1f)
                    )
                    ExploreTileCard(
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
        }

        // ---- Section 3: Cross-Playlist Favourites ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Text(strings.favouriteChannels, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (favourites.isNotEmpty()) {
                        Text("(${favourites.size})", color = c.textSecondary, fontSize = 13.sp)
                    }
                }

                if (favourites.isEmpty()) {
                    EmptyHintCard(Icons.Rounded.FavoriteBorder, strings.noFavouritesYet)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(favourites, key = { "fav_${it.channel.id}" }) { tagged ->
                            Column(
                                modifier = Modifier
                                    .width(136.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(c.surface)
                                    .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                                    .tvFocusBorder(isTvMode, RoundedCornerShape(16.dp))
                                .glossyPressPill(16f)
                                    .clickable { onPlayTaggedChannel(tagged, favourites) }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    ChannelLogoBox(tagged.channel.logoUrl, 42.dp, corner = 10.dp)
                                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
                                }
                                Text(
                                    text = tagged.channel.name,
                                    color = c.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 15.sp
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
                    }
                }
            }
        }

        // ---- Section 4: Watch History (FotMob Grouped Card Style) ----
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                Text(strings.watchHistory, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

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
                        .clickable { onPlayTaggedChannel(tagged, history) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ChannelLogoBox(tagged.channel.logoUrl, 42.dp, corner = 10.dp)
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
}

@Composable
private fun SectionHeaderRow(title: String, actionLabel: String, onAction: () -> Unit) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            text = actionLabel,
            color = c.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onAction).padding(4.dp)
        )
    }
}

@Composable
private fun ExploreTileCard(
    title: String,
    count: Int,
    icon: ImageVector,
    featured: Boolean,
    isTvMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    val bg = if (featured) c.accent else c.surface
    val fg = if (featured) c.onAccent else c.textPrimary
    val subFg = if (featured) c.onAccent.copy(alpha = 0.78f) else c.textSecondary

    Column(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, if (featured) c.accent else c.glassBorder, RoundedCornerShape(16.dp))
            .tvFocusBorder(isTvMode, RoundedCornerShape(16.dp))
                                .glossyPressPill(16f)
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = if (featured) fg else c.accent, modifier = Modifier.size(22.dp))
            Text(
                text = formatCompactCount(count),
                color = subFg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
