package com.ylowstone.tv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ylowstone.tv.model.LoadingAnimStyle

/**
 * Animasi loading "Bumerang" ala Motorola Edge 60 Fusion (60fps native Compose Canvas):
 * - 4 lapisan squircle lembut transparan yang saling bertumpuk
 * - Berotasi berlawanan arah & mengembang-mengempis halus dengan kurva boomerang (Reverse)
 * - 3 pilihan palet warna: Bumerang Moto (biru-ungu-magenta), Bumerang Amber (splash YlowstoneTV), Bumerang Aurora (emerald-cyan)
 */
@Composable
fun BoomerangLoadingIndicator(
    style: LoadingAnimStyle = LoadingAnimStyle.MOTOROLA_BOOMERANG,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "boomerangLoader")

    // Rotasi bolak-balik (efek "bumerang") + putaran kontinu lembut
    val angleA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleA"
    )
    val angleB by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleB"
    )
    val swing by transition.animateFloat(
        initialValue = -28f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swing"
    )
    val breath by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // 4 pasangan gradasi warna per gaya animasi
    val palettes: List<Pair<Color, Color>> = when (style) {
        LoadingAnimStyle.MOTOROLA_BOOMERANG -> listOf(
            Color(0xAA9D174D) to Color(0xAA7E22CE), // Deep Magenta -> Violet
            Color(0xB86D28D9) to Color(0xB82563EB), // Purple -> Royal Blue
            Color(0xC43B82F6) to Color(0xC406B6D4), // Electric Blue -> Cyan
            Color(0xB88B5CF6) to Color(0xB838BDF8)  // Soft Violet -> Sky Blue
        )
        LoadingAnimStyle.YLLOWSTONE_EMBER -> listOf(
            Color(0xAA1F4A37) to Color(0xAA2E6F56), // Smoked Olive-Emerald
            Color(0xB892400E) to Color(0xB8D97706), // Deep Bronze -> Warm Amber
            Color(0xC4E89D3C) to Color(0xC4F5C26B), // Amber Sunstone -> Gold
            Color(0xB0E2D5BE) to Color(0xB0C98A2E)  // Champagne Titanium -> Amber
        )
        LoadingAnimStyle.AURORA_EMERALD -> listOf(
            Color(0xAA065F46) to Color(0xAA0D9488), // Deep Forest -> Teal
            Color(0xB8059669) to Color(0xB806B6D4), // Emerald -> Cyan
            Color(0xC410B981) to Color(0xC438BDF8), // Bright Emerald -> Sky
            Color(0xB034D399) to Color(0xB022D3EE)  // Mint -> Aqua
        )
    }

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val baseDim = this.size.minDimension * 0.62f

        val layerSpecs = listOf(
            Triple(angleA + swing, breath * 1.02f, 0.36f),
            Triple(angleB - swing * 0.8f, (1.88f - breath) * 0.98f, 0.40f),
            Triple(angleA * 0.75f + 45f, breath * 0.94f, 0.38f),
            Triple(angleB * 0.65f + 22f, (1.88f - breath) * 0.92f, 0.44f)
        )

        layerSpecs.forEachIndexed { idx, (rot, scale, cornerRatio) ->
            val w = baseDim * scale * (if (idx % 2 == 0) 1.06f else 0.96f)
            val h = baseDim * scale * (if (idx % 2 == 0) 0.94f else 1.05f)
            val topLeft = Offset(cx - w / 2f, cy - h / 2f)
            val radius = minOf(w, h) * cornerRatio
            val (cStart, cEnd) = palettes[idx % palettes.size]

            rotate(degrees = rot, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(cStart, cEnd),
                        start = topLeft,
                        end = Offset(topLeft.x + w, topLeft.y + h)
                    ),
                    topLeft = topLeft,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
        }
    }
}
