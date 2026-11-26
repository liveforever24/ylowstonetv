package com.ylowstone.tv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
 * Palet konsisten AMOLED Noir & Champagne-Taupe YlowstoneTV v2.4.1.
 */
object CinematicNoirTokens {
    val BgAmoled = Color(0xFF000000)
    val SurfaceDark = Color(0xFF121110)
    val MiniBarBg = Color(0xFF161412)
    val ChampagneAccent = Color(0xFFB09878)      // Ikon, garis tipis search bar, border aktif
    val ChampagnePlay = Color(0xFFC4A67E)        // Tombol Play bulat utama
    val ChampagneHighlight = Color(0xFFD4B88E)   // Sorotan judul channel yang sedang aktif diputar
    val LiveRed = Color(0xFFFA263E)              // Indikator "• LIVE"
    val TextPrimary = Color(0xFFF5F1E8)
    val TextSecondary = Color(0xFF9C968F)

    // Gradasi atas sesuai cover masing-masing bagian
    val GradTv = Color(0xFF6B4B27)               // Amber-Bronze
    val GradFilm = Color(0xFF3E5655)             // Sage-Slate
    val GradSeri = Color(0xFF6E5C45)             // Champagne-Taupe
    val GradNeutral = Color(0xFF473B2D)          // Warm Taupe untuk Riwayat / Favorit / Playlist
}

data class QuickChipSpec(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Reusable Collapsing Scroll Header berbasis LazyColumn + rememberLazyListState:
 * - Search bar atas + tombol Urut
 * - Cover kotak di tengah yang mengecil & fade-out halus saat di-scroll
 * - Gradasi lembut senada cover memudar mulus ke #000000
 * - Sticky top bar (tombol kembali + judul) dengan tombol Play bulat (#C4A67E) menempel (docked) di kanan
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CinematicNoirTokens.BgAmoled)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = if (activeMiniChannel != null) 96.dp else 28.dp
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
                                    headerGradientColor.copy(alpha = 0.88f),
                                    headerGradientColor.copy(alpha = 0.46f),
                                    CinematicNoirTokens.BgAmoled
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
                                        .background(Color.Black.copy(alpha = 0.28f))
                                        .border(0.8.dp, CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.35f), CircleShape)
                                        .tvFocusBorder(isTvMode, CircleShape)
                                        .clickable(onClick = onBack),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = CinematicNoirTokens.TextPrimary,
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
                                    .background(Color.Black.copy(alpha = 0.32f))
                                    .border(
                                        width = 0.9.dp,
                                        color = CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.42f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = CinematicNoirTokens.ChampagneAccent,
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
                                                color = CinematicNoirTokens.TextPrimary.copy(alpha = 0.60f)
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = onSearchQueryChange,
                                        singleLine = true,
                                        cursorBrush = SolidColor(CinematicNoirTokens.ChampagnePlay),
                                        textStyle = TextStyle(
                                            fontFamily = YlowInterDisplay,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = CinematicNoirTokens.TextPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        tint = CinematicNoirTokens.TextSecondary,
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
                                        .background(
                                            if (sortActive) CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.28f)
                                            else Color.Black.copy(alpha = 0.32f)
                                        )
                                        .border(
                                            width = 0.9.dp,
                                            color = if (sortActive) CinematicNoirTokens.ChampagnePlay
                                            else CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.42f),
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
                                            color = if (sortActive) CinematicNoirTokens.ChampagneHighlight else CinematicNoirTokens.TextPrimary
                                        )
                                    )
                                }
                            }
                        }

                        // Cover Kotak di Tengah (mengecil & fade-out saat di-scroll)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = coverRes),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(182.dp)
                                    .scale(coverScale)
                                    .alpha(coverAlpha)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        width = 1.dp,
                                        color = CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.28f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            )
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
                                        color = Color.White
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
                                            color = CinematicNoirTokens.TextSecondary
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
                                        .background(CinematicNoirTokens.ChampagnePlay)
                                        .tvFocusBorder(isTvMode, CircleShape)
                                        .clickable(enabled = inlinePlayAlpha > 0.15f, onClick = onPlayAction),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = playButtonIcon,
                                        contentDescription = "Play",
                                        tint = Color(0xFF14110D),
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
                                            .background(
                                                if (chip.selected) CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.24f)
                                                else Color(0xFF1A1816)
                                            )
                                            .border(
                                                width = 0.9.dp,
                                                color = if (chip.selected) CinematicNoirTokens.ChampagnePlay
                                                else CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.30f),
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
                                                color = if (chip.selected) CinematicNoirTokens.ChampagneHighlight else CinematicNoirTokens.TextPrimary
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

        // Sticky Top Bar + Docked Play Button (muncul mulus saat di-scroll ke bawah)
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
                                Color(0xFF14110E).copy(alpha = 0.96f)
                            )
                        )
                    )
                    .border(
                        width = 0.5.dp,
                        color = CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.22f)
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
                                .background(Color.Black.copy(alpha = 0.28f))
                                .tvFocusBorder(isTvMode, CircleShape)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
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
                            color = Color.White
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
                                .background(CinematicNoirTokens.ChampagnePlay)
                                .tvFocusBorder(isTvMode, CircleShape)
                                .clickable(onClick = onPlayAction),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = playButtonIcon,
                                contentDescription = "Play",
                                tint = Color(0xFF14110D),
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

/**
 * Floating Mini-Player Bar di bagian bawah daftar saat ada channel yang sedang aktif diputar.
 */
@Composable
fun FloatingMiniPlayerBar(
    channel: ChannelItem,
    isPlaying: Boolean,
    isTvMode: Boolean,
    onTogglePlay: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CinematicNoirTokens.MiniBarBg)
            .border(
                width = 0.9.dp,
                color = CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.45f),
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
                    color = CinematicNoirTokens.ChampagneHighlight
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
                        color = CinematicNoirTokens.TextSecondary
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
                    tint = CinematicNoirTokens.ChampagnePlay,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Baris Channel / Item Borderless di atas #000000:
 * - Logo kotak membulat di kiri
 * - Judul & subjudul di tengah (sorotan #D4B88E + badge "• LIVE" #FA263E saat aktif diputar)
 * - Tombol hati & menu titik tiga di kanan
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
                    .background(CinematicNoirTokens.ChampagneAccent)
            )
            Text(
                text = cleanTitle.ifBlank { ch.name },
                style = TextStyle(
                    fontFamily = YlowInterDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = CinematicNoirTokens.ChampagneAccent
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
                if (active) {
                    Modifier.background(CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.08f))
                } else {
                    Modifier
                }
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
                        if (active) {
                            Modifier.border(1.2.dp, CinematicNoirTokens.ChampagneAccent, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        }
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
                        color = if (active) CinematicNoirTokens.ChampagneHighlight else CinematicNoirTokens.TextPrimary
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
                            color = if (active) CinematicNoirTokens.ChampagneAccent else CinematicNoirTokens.TextSecondary
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
                            tint = if (ch.isFavorite) CinematicNoirTokens.ChampagnePlay else CinematicNoirTokens.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onLongClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = CinematicNoirTokens.TextSecondary,
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
                    .background(Color(0xFF22201D))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(CinematicNoirTokens.ChampagnePlay)
                )
            }
        }
    }
}

/**
 * Baris Kategori Borderless di atas #000000.
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlight) Modifier.background(CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.08f))
                else Modifier
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
                .background(
                    if (highlight || locked) CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.22f)
                    else Color(0xFF151412)
                )
                .border(
                    width = 0.8.dp,
                    color = if (highlight || locked) CinematicNoirTokens.ChampagnePlay
                    else CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight || locked) CinematicNoirTokens.ChampagneHighlight else CinematicNoirTokens.ChampagneAccent,
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
                    color = if (highlight) CinematicNoirTokens.ChampagneHighlight else CinematicNoirTokens.TextPrimary
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
                    color = CinematicNoirTokens.TextSecondary
                ),
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = CinematicNoirTokens.ChampagneAccent.copy(alpha = 0.72f),
            modifier = Modifier.size(19.dp)
        )
    }
}
