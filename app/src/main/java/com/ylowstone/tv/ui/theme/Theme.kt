package com.ylowstone.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.model.ThemeMode

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
    val accentContainer: Color
)

// Default Google Pixel "Fog" palette on Pure Black AMOLED
val LocalYlowColors = staticCompositionLocalOf {
    YlowColors(
        isDark = true,
        bg = Color(0xFF000000),
        surface = Color(0xFF141618),
        surfaceElevated = Color(0xFF1D2125),
        navBarBg = Color(0xFF121416),
        glassBorder = Color(0xFF23282D),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF9AA3AB),
        accent = Color(0xFFB5C8D6),
        onAccent = Color(0xFF0B1116),
        accentContainer = Color(0xFF202B33)
    )
}

@Composable
fun YlowstoneTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val ylow = if (isDark) {
        // Pure AMOLED Black + FotMob flat crisp surfaces + Google Pixel "Fog" cool misty accent
        YlowColors(
            isDark = true,
            bg = Color(0xFF000000),
            surface = Color(0xFF141618),
            surfaceElevated = Color(0xFF1D2125),
            navBarBg = Color(0xFF121416),
            glassBorder = Color(0xFF23282D),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF9AA3AB),
            accent = Color(0xFFB5C8D6),          // Pixel Fog (Misty Blue-Silver)
            onAccent = Color(0xFF0B1116),        // Crisp deep ink on Fog buttons
            accentContainer = Color(0xFF202B33)
        )
    } else {
        // Clean Light Mode + Refined Deep Pixel Fog Slate accent
        YlowColors(
            isDark = false,
            bg = Color(0xFFF3F5F7),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFE7ECF0),
            navBarBg = Color(0xFFFFFFFF),
            glassBorder = Color(0xFFD8DFE5),
            textPrimary = Color(0xFF111417),
            textSecondary = Color(0xFF5D6770),
            accent = Color(0xFF526A7A),          // Deep Pixel Fog Slate
            onAccent = Color(0xFFFFFFFF),        // Pure white on deep Fog buttons
            accentContainer = Color(0xFFDCE5EC)
        )
    }

    // Inherits native system typography cleanly
    val typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 26.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 21.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp),
        labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    )

    val colorScheme = if (isDark) {
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
