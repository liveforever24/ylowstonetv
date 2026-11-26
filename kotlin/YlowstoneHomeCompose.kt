package com.ylowstone.tv.ui.home

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.R

/**
 * Font Family InterDisplay — sama persis dengan desain (res/font/inter_display_*.otf).
 */
val InterDisplayFamily = FontFamily(
    Font(R.font.inter_display_light, FontWeight.Light),
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)

object YlowstoneColors {
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

/**
 * YlowstoneHomeScreen v2.4.0 — 100% Jetpack Compose (Ponsel 9:16 & TV 16:9).
 * Semua label bisa langsung diisi dari AppStrings agar mendukung ID/EN.
 */
@Composable
fun YlowstoneHomeScreen(
    regionLabel: String = "US/UK/ID",
    tvTitle: String = "TV",
    filmTitle: String = "FILM",
    seriTitle: String = "SERI",
    tvCountText: String = "6,5k saluran",
    filmCountText: String = "27,2k",
    seriCountText: String = "8,1k",
    historyLabel: String = "Riwayat",
    favoriteLabel: String = "Favorit",
    historyCount: Int = 30,
    favoriteCount: Int = 12,
    onPlaylistClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTvClick: () -> Unit,
    onFilmClick: () -> Unit,
    onSeriClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    val context = LocalContext.current
    val isTvDevice = remember(context) { isAndroidTv(context) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(YlowstoneColors.AmoledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val isWideMode = isTvDevice || maxWidth > maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isWideMode) 28.dp else 18.dp,
                    vertical = if (isWideMode) 18.dp else 14.dp
                )
        ) {
            YlowstoneTopHeader(
                regionLabel = regionLabel,
                onPlaylistClick = onPlaylistClick,
                onSettingsClick = onSettingsClick
            )
            Spacer(modifier = Modifier.height(if (isWideMode) 14.dp else 16.dp))

            if (isWideMode) {
                // =================================================
                // MODE TV / LANDSCAPE (16:9) — Kiri TV Besar, Kanan FILM & SERI
                // =================================================
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    YellowstoneCoverCard(
                        title = tvTitle,
                        badgeText = tvCountText,
                        coverRes = R.drawable.cover_yellowstone_tv,
                        icon = Icons.Rounded.LiveTv,
                        surfaceColor = YlowstoneColors.CardSurfaceTv,
                        ambientColor = YlowstoneColors.AmbientTv,
                        isHero = true,
                        showLiveDot = true,
                        centerContent = true,
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight(),
                        onClick = onTvClick
                    )

                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        YellowstoneCoverCard(
                            title = filmTitle,
                            badgeText = filmCountText,
                            coverRes = R.drawable.cover_yellowstone_film,
                            icon = Icons.Rounded.Movie,
                            surfaceColor = YlowstoneColors.CardSurfaceFilm,
                            ambientColor = YlowstoneColors.AmbientFilm,
                            isHero = false,
                            centerContent = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onClick = onFilmClick
                        )
                        YellowstoneCoverCard(
                            title = seriTitle,
                            badgeText = seriCountText,
                            coverRes = R.drawable.cover_yellowstone_seri,
                            icon = Icons.Rounded.Tv,
                            surfaceColor = YlowstoneColors.CardSurfaceSeri,
                            ambientColor = YlowstoneColors.AmbientSeri,
                            isHero = false,
                            centerContent = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onClick = onSeriClick
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
                        label = historyLabel,
                        count = historyCount.toString(),
                        icon = Icons.Rounded.History,
                        isHorizontal = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onHistoryClick
                    )
                    UtilityCompactTile(
                        label = favoriteLabel,
                        count = favoriteCount.toString(),
                        icon = Icons.Rounded.Favorite,
                        isHorizontal = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onFavoriteClick
                    )
                }
            } else {
                // =================================================
                // MODE PONSEL (9:16) — Penuh Layar Tanpa Ruang Kosong
                // =================================================
                YellowstoneCoverCard(
                    title = tvTitle,
                    badgeText = tvCountText,
                    coverRes = R.drawable.cover_yellowstone_tv,
                    icon = Icons.Rounded.LiveTv,
                    surfaceColor = YlowstoneColors.CardSurfaceTv,
                    ambientColor = YlowstoneColors.AmbientTv,
                    isHero = true,
                    showLiveDot = true,
                    centerContent = false,
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxWidth(),
                    onClick = onTvClick
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .weight(0.41f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    YellowstoneCoverCard(
                        title = filmTitle,
                        badgeText = filmCountText,
                        coverRes = R.drawable.cover_yellowstone_film,
                        icon = Icons.Rounded.Movie,
                        surfaceColor = YlowstoneColors.CardSurfaceFilm,
                        ambientColor = YlowstoneColors.AmbientFilm,
                        isHero = false,
                        centerContent = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onFilmClick
                    )
                    YellowstoneCoverCard(
                        title = seriTitle,
                        badgeText = seriCountText,
                        coverRes = R.drawable.cover_yellowstone_seri,
                        icon = Icons.Rounded.Tv,
                        surfaceColor = YlowstoneColors.CardSurfaceSeri,
                        ambientColor = YlowstoneColors.AmbientSeri,
                        isHero = false,
                        centerContent = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onSeriClick
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
                        label = historyLabel,
                        count = historyCount.toString(),
                        icon = Icons.Rounded.History,
                        isHorizontal = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onHistoryClick
                    )
                    UtilityCompactTile(
                        label = favoriteLabel,
                        count = favoriteCount.toString(),
                        icon = Icons.Rounded.Favorite,
                        isHorizontal = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onFavoriteClick
                    )
                }
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
                        fontFamily = InterDisplayFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = YlowstoneColors.MutedText,
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
                contentDescription = "Pengaturan",
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

            // Ambien tipis & scrim matte agar teks tetap terbaca tajam
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
                // =================================================
                // KONTEN TENGAH UNTUK MODE TV (16:9)
                // =================================================
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
                                        .background(YlowstoneColors.LiveRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE",
                                    style = TextStyle(
                                        fontFamily = InterDisplayFamily,
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
                            fontFamily = InterDisplayFamily,
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
                // =================================================
                // KONTEN MODE PONSEL (9:16) — Kotak ikon kiri atas + judul bawah kiri
                // =================================================
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
                            .background(YlowstoneColors.LiveRed)
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
                            fontFamily = InterDisplayFamily,
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
                    .background(YlowstoneColors.LiveRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = TextStyle(
                fontFamily = InterDisplayFamily,
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
    val borderColor = if (isFocused) Color(0xFFE0A14A) else Color.White.copy(alpha = 0.14f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = YlowstoneColors.UtilitySurface,
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
                            fontFamily = InterDisplayFamily,
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
                        fontFamily = InterDisplayFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                )
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = InterDisplayFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = YlowstoneColors.MutedText
                    )
                )
            }
        }
    }
}

private fun isAndroidTv(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
