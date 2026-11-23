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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.components.ChannelLogoBox
import com.ylowstone.tv.ui.components.ProfileYBadgeButton
import com.ylowstone.tv.ui.components.SmallIconPill
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors

private val playlistPalette = listOf(
    Color(0xFFFA2D48),
    Color(0xFF0A84FF),
    Color(0xFF30D158),
    Color(0xFFFF9F0A),
    Color(0xFFBF5AF2),
    Color(0xFF5AC8FA)
)

@Composable
fun HomeScreen(
    activePlaylist: PlaylistProfile,
    allPlaylists: List<PlaylistProfile>,
    counts: Map<MediaType, Int>,
    favourites: List<TaggedChannel>,
    history: List<TaggedChannel>,
    strings: AppStrings,
    isTvMode: Boolean,
    nestedScrollConnection: NestedScrollConnection,
    onSwitchPlaylist: (PlaylistProfile) -> Unit,
    onOpenPlaylistManager: () -> Unit,
    onOpenSection: (MediaType) -> Unit,
    onRefreshActive: () -> Unit,
    onPlayTaggedChannel: (TaggedChannel, List<TaggedChannel>) -> Unit,
    onOpenProfileMenu: () -> Unit
) {
    val c = LocalYlowColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .nestedScroll(nestedScrollConnection)
            .systemBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ---- Top Header (BitChord-style: Brand left + Refresh + Yellow-Y Profile Menu right) ----
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
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmallIconPill(Icons.Rounded.Refresh, strings.refresh, isTvMode, onRefreshActive)
                    ProfileYBadgeButton(isTvMode = isTvMode, onClick = onOpenProfileMenu)
                }
            }
        }

        // ---- Section 1: Saved Playlists Carousel (Instant 1-Tap Switch!) ----
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
                        val tint = playlistPalette[pl.colorIndex % playlistPalette.size]
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (active) c.accent.copy(alpha = 0.20f) else c.surface)
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) c.accent else c.glassBorder,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .tvFocusBorder(isTvMode, RoundedCornerShape(18.dp))
                                .clickable { onSwitchPlaylist(pl) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.55f)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pl.name.take(2).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
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
                                Text(
                                    text = pl.type.name,
                                    color = c.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // + Add Playlist Pill
                    item {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(c.surface)
                                .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
                                .tvFocusBorder(isTvMode, RoundedCornerShape(18.dp))
                                .clickable(onClick = onOpenPlaylistManager)
                                .padding(horizontal = 14.dp, vertical = 14.dp),
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

        // ---- Section 2: Explore Quick Cards (Live TV · Movies · Series) ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.explore, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExplorePillCard(
                        title = strings.liveTv,
                        count = counts[MediaType.LIVE] ?: 0,
                        icon = Icons.Rounded.LiveTv,
                        gradient = listOf(c.accent, c.accentSecondary),
                        isTvMode = isTvMode,
                        onClick = { onOpenSection(MediaType.LIVE) },
                        modifier = Modifier.weight(1f)
                    )
                    ExplorePillCard(
                        title = strings.movies,
                        count = counts[MediaType.MOVIE] ?: 0,
                        icon = Icons.Rounded.Movie,
                        gradient = listOf(Color(0xFF0A84FF), Color(0xFF0055A4)),
                        isTvMode = isTvMode,
                        onClick = { onOpenSection(MediaType.MOVIE) },
                        modifier = Modifier.weight(1f)
                    )
                    ExplorePillCard(
                        title = strings.series,
                        count = counts[MediaType.SERIES] ?: 0,
                        icon = Icons.Rounded.Tv,
                        gradient = listOf(Color(0xFF9D4EDD), Color(0xFF5A189A)),
                        isTvMode = isTvMode,
                        onClick = { onOpenSection(MediaType.SERIES) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ---- Section 3: Cross-Playlist Favourites (Appears right on Home!) ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Text(strings.favouriteChannels, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (favourites.isNotEmpty()) {
                        Text("(${favourites.size})", color = c.textSecondary, fontSize = 13.sp)
                    }
                }

                if (favourites.isEmpty()) {
                    EmptyHintCard(Icons.Rounded.FavoriteBorder, strings.noFavouritesYet)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favourites, key = { "fav_${it.channel.id}" }) { tagged ->
                            Column(
                                modifier = Modifier
                                    .width(138.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(c.surface)
                                    .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
                                    .tvFocusBorder(isTvMode, RoundedCornerShape(18.dp))
                                    .clickable { onPlayTaggedChannel(tagged, favourites) }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    ChannelLogoBox(tagged.channel.logoUrl, 44.dp, corner = 12.dp)
                                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
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
                                        .background(c.accent.copy(alpha = 0.14f))
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

        // ---- Section 4: Watch History (BitChord Recents Style!) ----
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                Text(strings.watchHistory, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (history.isEmpty()) {
            item { EmptyHintCard(Icons.Rounded.History, strings.noHistoryYet) }
        } else {
            items(history.take(15), key = { "hist_${it.channel.id}" }) { tagged ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                        .tvFocusBorder(isTvMode, RoundedCornerShape(16.dp))
                        .clickable { onPlayTaggedChannel(tagged, history) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ChannelLogoBox(tagged.channel.logoUrl, 44.dp, corner = 12.dp)
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
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
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
        Text(title, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
private fun ExplorePillCard(
    title: String,
    count: Int,
    icon: ImageVector,
    gradient: List<Color>,
    isTvMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(98.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradient))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .tvFocusBorder(isTvMode, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(
                text = formatCompactCount(count),
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        Text(message, color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
