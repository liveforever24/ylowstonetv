package com.ylowstone.tv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.R
import com.ylowstone.tv.model.ChannelItem
import com.ylowstone.tv.model.MediaType
import com.ylowstone.tv.ui.theme.LocalYlowColors

/**
 * Font Family InterDisplay global untuk seluruh UI YlowstoneTV.
 */
val YlowInterDisplay = FontFamily(
    Font(R.font.inter_display_light, FontWeight.Light),
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)

/**
 * Token fallback warna live indicator.
 * Semua warna latar, aksen, gradasi, & cover kini mengikuti LocalYlowColors secara dinamis (v2.4.2).
 */
object CinematicNoirTokens {
    val LiveRed = Color(0xFFFA263E)
}

data class QuickChipSpec(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Reusable Collapsing Scroll Header berbasis LazyColumn + rememberLazyListState (v2.4.2):
 * - Seluruh warna latar, aksen, tombol Play, gradasi header, dan nuansa cover otomatis berubah mengikuti tema aktif
 * - Cover mengecil & fade-out halus saat di-scroll, lalu tombol Play menempel (docked) di sticky top bar
 * - Floating mini-player bar di bawah saat ada channel sedang diputar
 */
@Composable
fun CinematicCollapsingListScreen(
    title: String,
    subtitleInfo: String,
    @DrawableRes coverRes: Int,
    headerGradientColor: Color,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    isTvMode: Boolean,
    listState: LazyListState = rememberLazyListState(),
    showLiveMetaTag: Boolean = false,
    sortLabel: String? = null,
    sortActive: Boolean = false,
    onSortClick: (() -> Unit)? = null,
    quickChips: List<QuickChipSpec> = emptyList(),
    playButtonIcon: ImageVector = Icons.Rounded.PlayArrow,
    onPlayAction: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    topRightAction: (@Composable () -> Unit)? = null,
    activeMiniChannel: ChannelItem? = null,
    isMiniPlaying: Boolean = false,
    onMiniTogglePlay: (() -> Unit)? = null,
    onMiniOpenPlayer: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val c = LocalYlowColors.current
    val density = LocalDensity.current
    val collapseRangePx = remember(density) { with(density) { 230.dp.toPx() } }

    val collapseProgress by remember(listState, collapseRangePx) {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> (listState.firstVisibleItemScrollOffset / collapseRangePx).coerceIn(0f, 1f)
            }
        }
    }

    val coverScale = 1f - (collapseProgress * 0.30f)
    val coverAlpha = (1f - (collapseProgress * 1.35f)).coerceIn(0f, 1f)
    val stickyAlpha = ((collapseProgress - 0.52f) / 0.42f).coerceIn(0f, 1f)
    val inlinePlayAlpha = (1f - ((collapseProgress - 0.38f) / 0.35f)).coerceIn(0f, 1f)

    val fieldBg = if (c.isDark) Color.Black.copy(alpha = 0.32f) else c.surface.copy(alpha = 0.85f)
    val chipBg = if (c.isDark) c.surfaceElevated else c.surface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = if (activeMiniChannel != null) 96.dp else 84.dp
            )
        ) {
            // Header Collapsing (index 0)
            item(key = "cinematic_collapsing_header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    headerGradientColor.copy(alpha = if (c.isDark) 0.90f else 0.78f),
                                    headerGradientColor.copy(alpha = if (c.isDark) 0.44f else 0.32f),
                                    c.bg
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Baris navigasi atas (Back + Trailing)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (onBack != null) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(fieldBg)
                                        .border(0.8.dp, c.accent.copy(alpha = 0.38f), CircleShape)
                                        .tvFocusBorder(isTvMode, CircleShape)
                                        .clickable(onClick = onBack),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = c.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Spacer(Modifier.size(38.dp))
                            }

                            if (topRightAction != null) {
                                topRightAction()
                            } else {
                                Spacer(Modifier.size(38.dp))
                            }
                        }

                        // Search Bar + Tombol Urut
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(fieldBg)
                                    .border(
                                        width = 0.9.dp,
                                        color = c.accent.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = c.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = searchPlaceholder,
                                            style = TextStyle(
                                                fontFamily = YlowInterDisplay,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 13.sp,
                                                color = c.textSecondary
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = onSearchQueryChange,
                                        singleLine = true,
                                        cursorBrush = SolidColor(c.accent),
                                        textStyle = TextStyle(
                                            fontFamily = YlowInterDisplay,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = c.textPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        tint = c.textSecondary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { onSearchQueryChange("") }
                                    )
                                }
                            }

                            if (sortLabel != null && onSortClick != null) {
                                Box(
                                    modifier = Modifier
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (sortActive) c.accentContainer else fieldBg)
                                        .border(
                                            width = 0.9.dp,
                                            color = if (sortActive) c.accent else c.accent.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .tvFocusBorder(isTvMode, RoundedCornerShape(10.dp))
                                        .clickable(onClick = onSortClick)
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sortLabel,
                                        style = TextStyle(
                                            fontFamily = YlowInterDisplay,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = if (sortActive) c.accent else c.textPrimary
                                        )
                                    )
                                }
                            }
                        }

                        // Cover Kotak di Tengah (mengecil & fade-out + tint dinamis sesuai tema aktif)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(182.dp)
                                    .scale(coverScale)
                                    .alpha(coverAlpha)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        width = 1.2.dp,
                                        color = c.accent.copy(alpha = 0.42f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Image(
                                    painter = painterResource(id = coverRes),
                                    contentDescription = title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Lapisan tint halus agar nuansa cover menyatu dengan tema aktif
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    c.coverTint,
                                                    headerGradientColor.copy(alpha = 0.16f)
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        // Judul Kategori + Info Row + Tombol Play Bulat
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = TextStyle(
                                        fontFamily = YlowInterDisplay,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 23.sp,
                                        letterSpacing = (-0.4).sp,
                                        color = c.textPrimary
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ylowstone_y_emblem),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = subtitleInfo,
                                        style = TextStyle(
                                            fontFamily = YlowInterDisplay,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = c.textSecondary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (showLiveMetaTag) {
                                        Text(
                                            text = "• LIVE",
                                            style = TextStyle(
                                                fontFamily = YlowInterDisplay,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = CinematicNoirTokens.LiveRed
                                            )
                                        )
                                    }
                                }
                            }

                            if (onPlayAction != null) {
                                Spacer(Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .alpha(inlinePlayAlpha)
                                        .clip(CircleShape)
                                        .background(c.accent)
                                        .tvFocusBorder(isTvMode, CircleShape)
                                        .clickable(enabled = inlinePlayAlpha > 0.15f, onClick = onPlayAction),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = playButtonIcon,
                                        contentDescription = "Play",
                                        tint = c.onAccent,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        // Chip Aksi Cepat
                        if (quickChips.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(quickChips, key = { it.id }) { chip ->
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (chip.selected) c.accentContainer else chipBg)
                                            .border(
                                                width = 0.9.dp,
                                                color = if (chip.selected) c.accent else c.accent.copy(alpha = 0.30f),
                                                shape = CircleShape
                                            )
                                            .tvFocusBorder(isTvMode, CircleShape)
                                            .clickable(onClick = chip.onClick)
                                            .padding(horizontal = 13.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = chip.label,
                                            style = TextStyle(
                                                fontFamily = YlowInterDisplay,
                                                fontWeight = if (chip.selected) FontWeight.SemiBold else FontWeight.Medium,
                                                fontSize = 11.sp,
                                                color = if (chip.selected) c.accent else c.textPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Daftar Konten Borderless di bawah header
            content()
        }

        // Sticky Top Bar + Docked Play Button
        if (stickyAlpha > 0.02f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .alpha(stickyAlpha)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                headerGradientColor.copy(alpha = 0.96f),
                                c.surface.copy(alpha = 0.96f)
                            )
                        )
                    )
                    .border(
                        width = 0.5.dp,
                        color = c.accent.copy(alpha = 0.25f)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onBack != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(fieldBg)
                                .tvFocusBorder(isTvMode, CircleShape)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = c.textPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Text(
                        text = title,
                        style = TextStyle(
                            fontFamily = YlowInterDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = c.textPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (onPlayAction != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(c.accent)
                                .tvFocusBorder(isTvMode, CircleShape)
                                .clickable(onClick = onPlayAction),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = playButtonIcon,
                                contentDescription = "Play",
                                tint = c.onAccent,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating Mini-Player Bar di bagian bawah saat ada channel sedang diputar
        if (activeMiniChannel != null && onMiniOpenPlayer != null) {
            FloatingMiniPlayerBar(
                channel = activeMiniChannel,
                isPlaying = isMiniPlaying,
                isTvMode = isTvMode,
                onTogglePlay = onMiniTogglePlay,
                onClick = onMiniOpenPlayer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun FloatingMiniPlayerBar(
    channel: ChannelItem,
    isPlaying: Boolean,
    isTvMode: Boolean,
    onTogglePlay: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceElevated)
            .border(
                width = 0.9.dp,
                color = c.accent.copy(alpha = 0.48f),
                shape = RoundedCornerShape(14.dp)
            )
            .tvFocusBorder(isTvMode, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ChannelLogoBox(channel.logoUrl, 40.dp, corner = 9.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = TextStyle(
                    fontFamily = YlowInterDisplay,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = c.accent
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (channel.mediaType == MediaType.LIVE) {
                    Text(
                        text = "• LIVE",
                        style = TextStyle(
                            fontFamily = YlowInterDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = CinematicNoirTokens.LiveRed
                        )
                    )
                }
                Text(
                    text = channel.category.ifBlank { "YlowstoneTV" },
                    style = TextStyle(
                        fontFamily = YlowInterDisplay,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.sp,
                        color = c.textSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onTogglePlay != null) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "PlayPause",
                    tint = c.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Baris Channel / Item Borderless yang mengikuti tema aktif secara dinamis.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BorderlessChannelRow(
    ch: ChannelItem,
    active: Boolean,
    isTvMode: Boolean,
    subtitleOverride: String? = null,
    progressFraction: Float = 0f,
    showFavoriteIcon: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFav: (() -> Unit)? = null
) {
    val c = LocalYlowColors.current
    if (ch.isProviderSeparator()) {
        val cleanTitle = ch.name.trim().trim('#', '=', '-', ' ')
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.accent)
            )
            Text(
                text = cleanTitle.ifBlank { ch.name },
                style = TextStyle(
                    fontFamily = YlowInterDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = c.accent
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (active) Modifier.background(c.accent.copy(alpha = 0.10f)) else Modifier
            )
            .tvFocusBorder(isTvMode, RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (active) Modifier.border(1.2.dp, c.accent, RoundedCornerShape(10.dp)) else Modifier
                    )
            ) {
                ChannelLogoBox(ch.logoUrl, 44.dp, corner = 10.dp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ch.name,
                    style = TextStyle(
                        fontFamily = YlowInterDisplay,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (active) c.accent else c.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = subtitleOverride ?: ch.category,
                        style = TextStyle(
                            fontFamily = YlowInterDisplay,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                            color = if (active) c.accent.copy(alpha = 0.85f) else c.textSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (active) {
                        Text(
                            text = "• LIVE",
                            style = TextStyle(
                                fontFamily = YlowInterDisplay,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = CinematicNoirTokens.LiveRed
                            )
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showFavoriteIcon && onToggleFav != null) {
                    IconButton(onClick = onToggleFav, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (ch.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Fav",
                            tint = if (ch.isFavorite) c.accent else c.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onLongClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = c.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (progressFraction > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.surfaceElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.accent)
                )
            }
        }
    }
}

/**
 * Baris Kategori Borderless yang mengikuti tema aktif secara dinamis.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BorderlessCategoryRow(
    name: String,
    countText: String,
    icon: ImageVector,
    locked: Boolean,
    highlight: Boolean,
    isTvMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlight) Modifier.background(c.accent.copy(alpha = 0.10f)) else Modifier
            )
            .tvFocusBorder(isTvMode, RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (highlight || locked) c.accentContainer else c.surface)
                .border(
                    width = 0.8.dp,
                    color = if (highlight || locked) c.accent else c.accent.copy(alpha = 0.24f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = TextStyle(
                    fontFamily = YlowInterDisplay,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (highlight) c.accent else c.textPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = countText,
                style = TextStyle(
                    fontFamily = YlowInterDisplay,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    color = c.textSecondary
                ),
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = c.accent.copy(alpha = 0.72f),
            modifier = Modifier.size(19.dp)
        )
    }
}
