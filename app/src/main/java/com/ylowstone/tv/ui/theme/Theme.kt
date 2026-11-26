package com.ylowstone.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.model.AccentPreset
import com.ylowstone.tv.model.ThemeMode
import com.ylowstone.tv.ui.components.YlowInterDisplay

data class YlowColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val navBarBg: Color,
    val glassBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    // Warna gradasi header & tint cover dinamis sesuai tema aktif (v2.4.2)
    val headerGradTv: Color = Color(0xFF6B4B27),
    val headerGradFilm: Color = Color(0xFF3E5655),
    val headerGradSeri: Color = Color(0xFF6E5C45),
    val headerGradNeutral: Color = Color(0xFF473B2D),
    val coverTint: Color = Color.Transparent
)

// Default: Gelap (AMOLED Noir #000000 + Champagne-Taupe #B09878 / #C4A67E)
val LocalYlowColors = staticCompositionLocalOf {
    YlowColors(
        isDark = true,
        bg = Color(0xFF000000),
        surface = Color(0xFF100F0E),
        surfaceElevated = Color(0xFF191715),
        navBarBg = Color(0xFF0C0B0A),
        glassBorder = Color(0xFF2B251F),
        textPrimary = Color(0xFFF5F1E8),
        textSecondary = Color(0xFF9C968F),
        accent = Color(0xFFC4A67E),
        onAccent = Color(0xFF14110D),
        accentContainer = Color(0xFF241E17),
        headerGradTv = Color(0xFF6B4B27),
        headerGradFilm = Color(0xFF3E5655),
        headerGradSeri = Color(0xFF6E5C45),
        headerGradNeutral = Color(0xFF473B2D),
        coverTint = Color.Transparent
    )
}

fun themePaletteSwatchColor(mode: ThemeMode): Color = when (mode) {
    ThemeMode.SYSTEM -> Color(0xFF8E9AAF)
    ThemeMode.AMOLED -> Color(0xFFC4A67E)
    ThemeMode.LIGHT -> Color(0xFF8C6E48)
    ThemeMode.AMBER_DUSK -> Color(0xFFE89D3C)
    ThemeMode.VELVET_CRIMSON -> Color(0xFFFA2D48)
}

fun accentSwatchColor(preset: AccentPreset): Color = when (preset) {
    AccentPreset.AMBER_SUNSTONE -> Color(0xFFE89D3C)
    AccentPreset.AMOLED_NOIR -> Color(0xFFC4A67E)
    AccentPreset.VELVET_CRIMSON -> Color(0xFFFA2D48)
    AccentPreset.SLATE_MIST -> Color(0xFFB5C8D6)
    AccentPreset.HYDROGEN_ICE -> Color(0xFF38BDF8)
    AccentPreset.EMERALD_STUDIO -> Color(0xFF10B981)
}

@Composable
fun YlowstoneTheme(
    themeMode: ThemeMode,
    accentPreset: AccentPreset = AccentPreset.AMBER_SUNSTONE,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val effectiveMode = when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDark) ThemeMode.AMOLED else ThemeMode.LIGHT
        else -> themeMode
    }

    val ylow = when (effectiveMode) {
        // 1 & 2. Gelap — AMOLED Noir (#000000) + Champagne-Taupe (#C4A67E / #B09878)
        ThemeMode.AMOLED, ThemeMode.SYSTEM -> YlowColors(
            isDark = true,
            bg = Color(0xFF000000),
            surface = Color(0xFF100F0E),
            surfaceElevated = Color(0xFF191715),
            navBarBg = Color(0xFF0A0908),
            glassBorder = Color(0xFF2D2720),
            textPrimary = Color(0xFFF5F1E8),
            textSecondary = Color(0xFF9C968F),
            accent = Color(0xFFC4A67E),
            onAccent = Color(0xFF14110D),
            accentContainer = Color(0xFF241E17),
            headerGradTv = Color(0xFF6B4B27),
            headerGradFilm = Color(0xFF3E5655),
            headerGradSeri = Color(0xFF6E5C45),
            headerGradNeutral = Color(0xFF473B2D),
            coverTint = Color(0x14B09878)
        )

        // 3. Cerah — Canvas Terang Hangat (#F5F3EE) + Warm Bronze (#8C6E48)
        ThemeMode.LIGHT -> YlowColors(
            isDark = false,
            bg = Color(0xFFF5F3EE),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFEBE7DF),
            navBarBg = Color(0xFFFFFFFF),
            glassBorder = Color(0xFFD6CEC2),
            textPrimary = Color(0xFF181512),
            textSecondary = Color(0xFF625A50),
            accent = Color(0xFF8C6E48),
            onAccent = Color(0xFFFFFFFF),
            accentContainer = Color(0xFFEDE4D7),
            headerGradTv = Color(0xFFD9C3A3),
            headerGradFilm = Color(0xFFC2D1CF),
            headerGradSeri = Color(0xFFD9CDBB),
            headerGradNeutral = Color(0xFFD5CBC0),
            coverTint = Color(0x1AF5F3EE)
        )

        // 4. Amber Dusk — Hitam Hangat (#080705) + Amber Sunstone (#E89D3C)
        ThemeMode.AMBER_DUSK -> YlowColors(
            isDark = true,
            bg = Color(0xFF080705),
            surface = Color(0xFF14110C),
            surfaceElevated = Color(0xFF1E1911),
            navBarBg = Color(0xFF0C0A07),
            glassBorder = Color(0xFF352917),
            textPrimary = Color(0xFFF8F2E7),
            textSecondary = Color(0xFFA89A84),
            accent = Color(0xFFE89D3C),
            onAccent = Color(0xFF170F02),
            accentContainer = Color(0xFF2E220E),
            headerGradTv = Color(0xFF7D5018),
            headerGradFilm = Color(0xFF4E4226),
            headerGradSeri = Color(0xFF704E22),
            headerGradNeutral = Color(0xFF593D18),
            coverTint = Color(0x22E89D3C)
        )

        // 5. Velvet Crimson — Hitam Pekat (#000000) + Velvet Crimson (#FA2D48)
        ThemeMode.VELVET_CRIMSON -> YlowColors(
            isDark = true,
            bg = Color(0xFF000000),
            surface = Color(0xFF110C0D),
            surfaceElevated = Color(0xFF1B1214),
            navBarBg = Color(0xFF0B0809),
            glassBorder = Color(0xFF361A20),
            textPrimary = Color(0xFFFAF5F6),
            textSecondary = Color(0xFFA89498),
            accent = Color(0xFFFA2D48),
            onAccent = Color(0xFFFFFFFF),
            accentContainer = Color(0xFF38121B),
            headerGradTv = Color(0xFF6E1624),
            headerGradFilm = Color(0xFF471A25),
            headerGradSeri = Color(0xFF5E1B29),
            headerGradNeutral = Color(0xFF4D1520),
            coverTint = Color(0x24FA2D48)
        )
    }

    val typography = Typography(
        headlineLarge = TextStyle(fontFamily = YlowInterDisplay, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = YlowInterDisplay, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.4).sp),
        titleLarge = TextStyle(fontFamily = YlowInterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
        titleMedium = TextStyle(fontFamily = YlowInterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = TextStyle(fontFamily = YlowInterDisplay, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodyMedium = TextStyle(fontFamily = YlowInterDisplay, fontWeight = FontWeight.Normal, fontSize = 13.sp),
        labelMedium = TextStyle(fontFamily = YlowInterDisplay, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    )

    val colorScheme = if (ylow.isDark) {
        darkColorScheme(
            primary = ylow.accent,
            onPrimary = ylow.onAccent,
            primaryContainer = ylow.accentContainer,
            background = ylow.bg,
            surface = ylow.surface,
            onBackground = ylow.textPrimary,
            onSurface = ylow.textPrimary
        )
    } else {
        lightColorScheme(
            primary = ylow.accent,
            onPrimary = ylow.onAccent,
            primaryContainer = ylow.accentContainer,
            background = ylow.bg,
            surface = ylow.surface,
            onBackground = ylow.textPrimary,
            onSurface = ylow.textPrimary
        )
    }

    CompositionLocalProvider(LocalYlowColors provides ylow) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
