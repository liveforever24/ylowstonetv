package com.ylowstone.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.ChannelItem
import com.ylowstone.tv.model.MediaType
import com.ylowstone.tv.model.PlaylistProfile
import com.ylowstone.tv.ui.components.SmallIconPill
import com.ylowstone.tv.ui.components.YlowstoneLogoBadge
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    playlist: PlaylistProfile,
    counts: Map<MediaType, Int>,
    strings: AppStrings,
    isTvMode: Boolean,
    use24Hour: Boolean,
    onOpenSection: (MediaType) -> Unit,
    onRefresh: () -> Unit,
    onSwitchPlaylist: () -> Unit,
    onSearchQuery: (String) -> List<ChannelItem>,
    onPlayChannel: (ChannelItem) -> Unit
) {
    val c = LocalYlowColors.current
    var clockText by remember { mutableStateOf("") }
    var showMasterSearch by remember { mutableStateOf(false) }

    LaunchedEffect(use24Hour) {
        val pattern = if (use24Hour) "HH:mm • dd MMM" else "hh:mm a • dd MMM"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        while (true) {
            clockText = fmt.format(Date())
            delay(15_000)
        }
    }

    val updatedLabel = remember(playlist.lastUpdated, strings) {
        if (playlist.lastUpdated <= 0L) strings.neverUpdated
        else {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            "${strings.lastUpdated}: ${fmt.format(Date(playlist.lastUpdated))}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 84.dp)
    ) {
        // Ultra-clean top bar (no cluttered settings on home!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                YlowstoneLogoBadge(size = 36.dp)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("YlowstoneTV", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.accent.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(playlist.name, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                    Text(clockText, color = c.textSecondary, fontSize = 11.sp)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.surfaceElevated.copy(alpha = 0.85f))
                        .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
                        .tvFocusBorder(isTvMode, RoundedCornerShape(20.dp))
                        .clickable { showMasterSearch = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = strings.masterSearch, tint = c.textPrimary, modifier = Modifier.size(18.dp))
                    Text(strings.masterSearch, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                SmallIconPill(Icons.Rounded.Refresh, strings.refresh, isTvMode, onRefresh)
                SmallIconPill(Icons.Rounded.SwitchAccount, strings.switchPlaylist, isTvMode, onSwitchPlaylist)
            }
        }

        Spacer(Modifier.height(18.dp))

        // 3 Big Hero Cards (Smarters Pro Layout x Apple Music Gradient Aesthetic)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroDashboardCard(
                title = strings.liveTv,
                count = counts[MediaType.LIVE] ?: 0,
                subtitle = updatedLabel,
                icon = Icons.Rounded.LiveTv,
                gradient = listOf(Color(0xFF00C9A7), Color(0xFF0A84FF), Color(0xFF1A237E)),
                isTvMode = isTvMode,
                onClick = { onOpenSection(MediaType.LIVE) },
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxHeight()
            )

            HeroDashboardCard(
                title = strings.movies,
                count = counts[MediaType.MOVIE] ?: 0,
                subtitle = updatedLabel,
                icon = Icons.Rounded.Movie,
                gradient = listOf(Color(0xFFFA2D48), Color(0xFFFF6B35), Color(0xFF6B1839)),
                isTvMode = isTvMode,
                onClick = { onOpenSection(MediaType.MOVIE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            HeroDashboardCard(
                title = strings.series,
                count = counts[MediaType.SERIES] ?: 0,
                subtitle = updatedLabel,
                icon = Icons.Rounded.Tv,
                gradient = listOf(Color(0xFFBF5AF2), Color(0xFF5E5CE6), Color(0xFF1B194D)),
                isTvMode = isTvMode,
                onClick = { onOpenSection(MediaType.SERIES) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }

    if (showMasterSearch) {
        var q by remember { mutableStateOf("") }
        val results = remember(q) { if (q.length >= 2) onSearchQuery(q) else emptyList() }

        AlertDialog(
            onDismissRequest = { showMasterSearch = false },
            containerColor = c.surface,
            title = { Text(strings.masterSearch, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    OutlinedTextField(
                        value = q,
                        onValueChange = { q = it },
                        placeholder = { Text(strings.searchInPlaylist) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(results, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.surfaceElevated)
                                    .clickable {
                                        showMasterSearch = false
                                        onPlayChannel(item)
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${item.mediaType.name} • ${item.category}", color = c.textSecondary, fontSize = 11.sp, maxLines = 1)
                                }
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.accent)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMasterSearch = false }) {
                    Text(strings.cancel, color = c.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun HeroDashboardCard(
    title: String,
    count: Int,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    isTvMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(gradient))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
            .tvFocusBorder(isTvMode, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(28.dp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(count.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
        }
    }
}
