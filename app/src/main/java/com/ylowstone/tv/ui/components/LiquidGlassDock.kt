package com.ylowstone.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.ui.theme.LocalYlowColors

/**
 * Permukaan Frosted / Liquid Glass dengan gradasi specular highlight 0.75dp
 * (putih 28% di tepi atas -> putih 6% di tepi bawah) untuk dock ponsel & kontrol Split Player.
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape,
    isDark: Boolean = LocalYlowColors.current.isDark,
    baseColor: Color = if (isDark) Color(0xFF141417) else Color(0xFFFFFFFF)
): Modifier {
    val fill = if (isDark) baseColor.copy(alpha = 0.78f) else baseColor.copy(alpha = 0.86f)
    val rimBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.06f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.88f),
                Color(0xFF181512).copy(alpha = 0.12f)
            )
        )
    }
    return this
        .shadow(elevation = if (isDark) 18.dp else 10.dp, shape = shape, clip = false)
        .clip(shape)
        .background(fill)
        .border(width = 0.75.dp, brush = rimBrush, shape = shape)
}

/**
 * Floating Liquid Glass Navigation Dock KHUSUS mode Ponsel/Tablet:
 * - Kapsul Pil Utama (62dp, CircleShape): Home, Live, Movies, Series (ikon + label 10sp)
 * - Tombol Pencarian Lingkaran Terpisah (62dp x 62dp, CircleShape) berjarak 12dp di sisi kanan
 * - Tidak pernah dirender pada mode Android TV.
 */
@Composable
fun FloatingLiquidGlassDock(
    activeTab: MainTab,
    strings: AppStrings,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    val isIndonesian = strings.watchHistory.startsWith("Riwayat", ignoreCase = true)

    val mainTabs = remember(isIndonesian) {
        listOf(
            Triple(MainTab.HOME, Icons.Rounded.Home, if (isIndonesian) "Beranda" else "Home"),
            Triple(MainTab.LIVE, Icons.Rounded.LiveTv, "Live"),
            Triple(MainTab.MOVIES, Icons.Rounded.Movie, if (isIndonesian) "Film" else "Movies"),
            Triple(MainTab.SERIES, Icons.Rounded.Tv, if (isIndonesian) "Seri" else "Series")
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Kapsul Pil Utama (62dp, CircleShape)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(62.dp)
                .liquidGlassSurface(shape = CircleShape, isDark = c.isDark, baseColor = c.navBarBg)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            mainTabs.forEach { (tab, icon, label) ->
                val selected = activeTab == tab
                DockPillTabItem(
                    icon = icon,
                    label = label,
                    selected = selected,
                    accentColor = c.accent,
                    inactiveColor = c.textSecondary,
                    activeBgColor = c.accent.copy(alpha = if (c.isDark) 0.20f else 0.14f),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { onSelectTab(tab) }
                )
            }
        }

        // 2. Tombol Pencarian Lingkaran Terpisah (62dp x 62dp, CircleShape)
        val searchSelected = activeTab == MainTab.SEARCH
        Box(
            modifier = Modifier
                .size(62.dp)
                .liquidGlassSurface(shape = CircleShape, isDark = c.isDark, baseColor = c.navBarBg)
                .then(
                    if (searchSelected) {
                        Modifier.border(1.25.dp, c.accent, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onSelectTab(if (searchSelected) MainTab.HOME else MainTab.SEARCH)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = strings.search,
                tint = if (searchSelected) c.accent else c.textPrimary,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
private fun DockPillTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accentColor: Color,
    inactiveColor: Color,
    activeBgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = if (selected) accentColor else inactiveColor
    Column(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) activeBgColor else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = tint,
            fontFamily = YlowInterDisplay,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 11.sp
        )
    }
}
