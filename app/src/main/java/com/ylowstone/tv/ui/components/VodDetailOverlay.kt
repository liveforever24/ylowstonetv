package com.ylowstone.tv.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ylowstone.tv.R
import com.ylowstone.tv.data.VodMetadata
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.ChannelItem
import com.ylowstone.tv.model.EpisodeItem
import com.ylowstone.tv.model.MediaType
import com.ylowstone.tv.model.PlaylistProfile
import com.ylowstone.tv.ui.theme.LocalYlowColors

/**
 * Halaman / Kartu Detail Tujuan KHUSUS Film & Seri (Ponsel Portrait/Landscape & Android TV).
 * Menampilkan Judul, Badge Rating IMDb, Info (Tentang), Tombol Putar, dan (khusus Seri)
 * daftar episode dengan judul setiap episode serta 2 opsi tampilan: List (mendatar ke bawah) & Grid (kotak kartu).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VodDetailOverlay(
    item: ChannelItem,
    playlist: PlaylistProfile,
    metadata: VodMetadata?,
    loadingMeta: Boolean,
    episodes: List<EpisodeItem>,
    loadingEpisodes: Boolean,
    strings: AppStrings,
    isTvMode: Boolean,
    onPlayMovie: () -> Unit,
    onPlayEpisode: (EpisodeItem, List<ChannelItem>) -> Unit,
    onLongPressEpisode: (ChannelItem) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val isIndonesian = strings.watchHistory.startsWith("Riwayat", ignoreCase = true)
    val isSeries = item.mediaType == MediaType.SERIES
    var episodeGridMode by remember(item.id) { mutableStateOf(false) }

    BackHandler(onBack = onDismiss)

    val titleText = metadata?.title?.ifBlank { item.name } ?: item.name
    val imdbText = metadata?.imdbRating?.ifBlank { item.rating } ?: item.rating
    val plotText = metadata?.plot?.trim().orEmpty()
    val posterUrl = metadata?.posterUrl?.ifBlank { item.logoUrl } ?: item.logoUrl
    val fallbackCover = if (isSeries) R.drawable.cover_home_seri else R.drawable.cover_home_film

    val epChannels = remember(episodes, item, playlist.id) {
        episodes.map { ep ->
            ChannelItem(
                id = ep.id.toLongOrNull() ?: 0L,
                playlistId = playlist.id,
                streamId = ep.id,
                name = "S${ep.season}E${ep.episodeNum} - ${ep.title}",
                category = item.name,
                logoUrl = posterUrl,
                streamUrl = ep.streamUrl,
                mediaType = MediaType.SERIES,
                rating = imdbText
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val isWide = isTvMode || maxWidth > 680.dp
        val gridCols = if (isWide) 3 else 2
        val epRows = remember(episodes, gridCols) { episodes.chunked(gridCols) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (isWide) 28.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar Kembali
            item(key = "detail_top_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c.surfaceElevated)
                            .focusable()
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary)
                    }
                    Text(
                        text = if (isSeries) strings.series else strings.movies,
                        color = c.textSecondary,
                        fontFamily = YlowInterDisplay,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Hero Poster + Judul + Rating IMDb + Info (Tentang) + Tombol Putar
            item(key = "detail_hero") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.surface)
                        .border(0.8.dp, c.glassBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .width(if (isWide) 136.dp else 108.dp)
                            .height(if (isWide) 196.dp else 156.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF141416))
                    ) {
                        if (posterUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(posterUrl).crossfade(true).build(),
                                contentDescription = titleText,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = fallbackCover),
                                contentDescription = titleText,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = titleText,
                            color = c.textPrimary,
                            fontFamily = YlowInterDisplay,
                            fontSize = if (isWide) 22.sp else 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Bar Badge Rating IMDb + Tahun + Durasi
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (imdbText.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0x2AE89D3C))
                                        .border(0.8.dp, Color(0x88E89D3C), CircleShape)
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Star,
                                        contentDescription = "IMDb",
                                        tint = Color(0xFFE89D3C),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "$imdbText IMDb",
                                        color = Color(0xFFF5D099),
                                        fontFamily = YlowInterDisplay,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (!metadata?.releaseYear.isNullOrBlank()) {
                                Text(
                                    text = metadata!!.releaseYear,
                                    color = c.textSecondary,
                                    fontFamily = YlowInterDisplay,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (!metadata?.duration.isNullOrBlank()) {
                                Text(
                                    text = "• ${metadata!!.duration}",
                                    color = c.textSecondary,
                                    fontFamily = YlowInterDisplay,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (!metadata?.genre.isNullOrBlank()) {
                            Text(
                                text = metadata!!.genre,
                                color = c.accent,
                                fontFamily = YlowInterDisplay,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Tombol Putar
                        Button(
                            onClick = {
                                if (isSeries && episodes.isNotEmpty()) {
                                    onPlayEpisode(episodes.first(), epChannels)
                                } else {
                                    onPlayMovie()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.focusable()
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isIndonesian) "Putar Sekarang" else "Play Now",
                                fontFamily = YlowInterDisplay,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Info (Tentang / Sinopsis)
            item(key = "detail_about") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .border(0.8.dp, c.glassBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isIndonesian) "Tentang" else "About",
                        color = c.textPrimary,
                        fontFamily = YlowInterDisplay,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (loadingMeta) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Text(
                                text = if (isIndonesian) "Memuat informasi..." else "Loading details...",
                                color = c.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text(
                            text = plotText.ifBlank {
                                if (isIndonesian) "Kategori: ${item.category.ifBlank { playlist.name }}"
                                else "Category: ${item.category.ifBlank { playlist.name }}"
                            },
                            color = c.textSecondary,
                            fontFamily = YlowInterDisplay,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            // Khusus Seri: Bar Pilihan Tampilan Episode (List mendatar ke bawah / Kotak Kartu Grid) + Daftar Episode
            if (isSeries) {
                item(key = "series_episode_bar") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isIndonesian) "Daftar Episode (${episodes.size})" else "Episodes (${episodes.size})",
                            color = c.textPrimary,
                            fontFamily = YlowInterDisplay,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EpisodeViewOptionChip(
                                icon = Icons.Rounded.ViewAgenda,
                                label = if (isIndonesian) "Daftar" else "List",
                                selected = !episodeGridMode,
                                onClick = { episodeGridMode = false }
                            )
                            EpisodeViewOptionChip(
                                icon = Icons.Rounded.GridView,
                                label = if (isIndonesian) "Kotak" else "Grid",
                                selected = episodeGridMode,
                                onClick = { episodeGridMode = true }
                            )
                        }
                    }
                }

                if (loadingEpisodes) {
                    item(key = "ep_loading") {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = c.accent)
                        }
                    }
                } else if (episodes.isEmpty()) {
                    item(key = "ep_empty") {
                        Text(strings.noEpisodes, color = c.textSecondary, fontSize = 13.sp)
                    }
                } else if (!episodeGridMode) {
                    // Opsi 1: Episode mendatar ke bawah (List) dengan judul setiap episode
                    items(episodes, key = { "ep_list_${it.id}_${it.season}_${it.episodeNum}" }) { ep ->
                        val epCh = epChannels.firstOrNull { it.streamUrl == ep.streamUrl }
                        val epTitleClean = ep.title.ifBlank { "Episode ${ep.episodeNum}" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.surface)
                                .border(0.8.dp, c.glassBorder, RoundedCornerShape(14.dp))
                                .focusable()
                                .combinedClickable(
                                    onClick = { onPlayEpisode(ep, epChannels) },
                                    onLongClick = { epCh?.let(onLongPressEpisode) }
                                )
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.accentContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "E${ep.episodeNum}",
                                    color = c.accent,
                                    fontFamily = YlowInterDisplay,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "S${ep.season} E${ep.episodeNum} • $epTitleClean",
                                    color = c.textPrimary,
                                    fontFamily = YlowInterDisplay,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (ep.plot.isNotBlank()) {
                                    Text(
                                        text = ep.plot,
                                        color = c.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.accent)
                        }
                    }
                } else {
                    // Opsi 2: Kotak Kartu (Grid) dengan judul setiap episode
                    items(epRows, key = { row -> "ep_grid_${row.first().id}" }) { rowEps ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowEps.forEach { ep ->
                                val epCh = epChannels.firstOrNull { it.streamUrl == ep.streamUrl }
                                val epTitleClean = ep.title.ifBlank { "Episode ${ep.episodeNum}" }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(c.surface)
                                        .border(0.8.dp, c.glassBorder, RoundedCornerShape(14.dp))
                                        .focusable()
                                        .combinedClickable(
                                            onClick = { onPlayEpisode(ep, epChannels) },
                                            onLongClick = { epCh?.let(onLongPressEpisode) }
                                        )
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "S${ep.season} E${ep.episodeNum}",
                                            color = c.accent,
                                            fontFamily = YlowInterDisplay,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = c.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = epTitleClean,
                                        color = c.textPrimary,
                                        fontFamily = YlowInterDisplay,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                            repeat(gridCols - rowEps.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeViewOptionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) c.accent else c.surfaceElevated)
            .border(0.8.dp, if (selected) c.accent else c.glassBorder, CircleShape)
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
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
            fontFamily = YlowInterDisplay,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Kartu Info Ringkas Film & Seri (Judul + Badge Rating IMDb + Info Tentang)
 * untuk Video Setengah Layar (SplitPlayerScreen — Ponsel Portrait/Landscape & Android TV).
 * TIDAK pernah ditampilkan untuk Live TV.
 */
@Composable
fun VodSplitInfoCard(
    channel: ChannelItem,
    metadata: VodMetadata?,
    isIndonesian: Boolean,
    modifier: Modifier = Modifier
) {
    if (channel.mediaType == MediaType.LIVE) return
    val c = LocalYlowColors.current
    var expanded by remember(channel.streamUrl) { mutableStateOf(false) }

    val titleText = metadata?.title?.ifBlank { channel.name } ?: channel.name
    val imdbText = metadata?.imdbRating?.ifBlank { channel.rating } ?: channel.rating
    val plotText = metadata?.plot?.trim().orEmpty()
    val metaLine = buildList {
        if (!metadata?.releaseYear.isNullOrBlank()) add(metadata!!.releaseYear)
        if (!metadata?.genre.isNullOrBlank()) add(metadata!!.genre)
        if (!metadata?.duration.isNullOrBlank()) add(metadata!!.duration)
    }.joinToString(" • ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceElevated)
            .border(0.8.dp, c.glassBorder, RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = titleText,
                color = c.textPrimary,
                fontFamily = YlowInterDisplay,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            if (imdbText.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x28E89D3C))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFE89D3C), modifier = Modifier.size(12.dp))
                    Text("$imdbText IMDb", color = Color(0xFFF5D099), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        if (metaLine.isNotBlank()) {
            Text(
                text = metaLine,
                color = c.accent,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (plotText.isNotBlank()) {
            Text(
                text = plotText,
                color = c.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = if (expanded) 8 else 2,
                overflow = TextOverflow.Ellipsis
            )
        } else if (expanded) {
            Text(
                text = if (isIndonesian) "Info sinopsis tidak tersedia dari server." else "No synopsis provided by server.",
                color = c.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}
