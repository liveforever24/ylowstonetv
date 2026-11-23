package com.ylowstone.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.components.ChannelLogoBox
import com.ylowstone.tv.ui.components.ProfileYBadgeButton
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
    activePlaylist: PlaylistProfile,
    lockedByPlaylist: Map<Long, Set<String>>,
    repo: IptvRepository,
    strings: AppStrings,
    isTvMode: Boolean,
    nestedScrollConnection: NestedScrollConnection,
    onPlayTagged: (TaggedChannel, List<TaggedChannel>) -> Unit,
    onToggleFavorite: (ChannelItem) -> Unit,
    onOpenProfileMenu: () -> Unit
) {
    val c = LocalYlowColors.current
    var query by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<MediaType?>(null) }
    var searchAllPlaylists by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<TaggedChannel>>(emptyList()) }
    var favVersion by remember { mutableIntStateOf(0) }

    // Battery & CPU friendly 150ms debounced indexed SQLite search
    LaunchedEffect(query, filterType, searchAllPlaylists, activePlaylist.id, lockedByPlaylist, favVersion) {
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        results = withContext(Dispatchers.IO) {
            repo.db.searchAcrossPlaylists(
                query = query,
                playlistIdFilter = if (searchAllPlaylists) null else activePlaylist.id,
                mediaType = filterType,
                lockedByPlaylist = lockedByPlaylist,
                limit = 120
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // Top Bar with Search Input & Yellow-Y Profile Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(strings.searchInPlaylist, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
            ProfileYBadgeButton(isTvMode = isTvMode, onClick = onOpenProfileMenu)
        }

        Spacer(Modifier.height(10.dp))

        // BitChord-Style Segmented Filter Capsule (All | Live | Movies | Series)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surface)
                .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                null to strings.allFilter,
                MediaType.LIVE to strings.liveTv,
                MediaType.MOVIE to strings.movies,
                MediaType.SERIES to strings.series
            ).forEach { (type, label) ->
                val active = filterType == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) c.accent else Color.Transparent)
                        .clickable { filterType = type }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) Color.White else c.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Scope Toggle: Active Playlist vs All Playlists
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (results.isNotEmpty()) "${results.size} ${strings.channelsCount}" else "",
                color = c.textSecondary,
                fontSize = 11.sp
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface)
                    .clickable { searchAllPlaylists = !searchAllPlaylists }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    if (searchAllPlaylists) Icons.Rounded.Public else Icons.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (searchAllPlaylists) strings.allPlaylists else strings.activePlaylistOnly,
                    color = c.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Results List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(results, key = { "srch_${it.channel.id}" }) { tagged ->
                val ch = tagged.channel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(c.surface)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(15.dp))
                        .tvFocusBorder(isTvMode, RoundedCornerShape(15.dp))
                        .clickable { onPlayTagged(tagged, results) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    ChannelLogoBox(ch.logoUrl, 40.dp, corner = 10.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ch.name,
                            color = c.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${tagged.playlistName} • ${ch.mediaType.name} • ${ch.category}",
                            color = c.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            onToggleFavorite(ch)
                            favVersion++
                        },
                        modifier = Modifier.size(32.dp)
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
    }
}
