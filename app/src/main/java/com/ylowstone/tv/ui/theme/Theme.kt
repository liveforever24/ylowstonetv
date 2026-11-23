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
import com.ylowstone.tv.model.StylePreset
import com.ylowstone.tv.model.ThemeMode

data class YlowColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val glassBg: Color,
    val glassHighlight: Color,
    val glassBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentSecondary: Color
)

val LocalYlowColors = staticCompositionLocalOf {
    YlowColors(
        isDark = true,
        bg = Color.Black,
        surface = Color(0xFF121216),
        surfaceElevated = Color(0xFF1C1C24),
        glassBg = Color(0xCC14141A),
        glassHighlight = Color(0x38FFFFFF),
        glassBorder = Color(0x28FFFFFF),
        textPrimary = Color.White,
        textSecondary = Color(0xFF9E9EAC),
        accent = Color(0xFFFA2D48),
        accentSecondary = Color(0xFFFF6B81)
    )
}

@Composable
fun YlowstoneTheme(
    themeMode: ThemeMode,
    stylePreset: StylePreset,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val accentColor = Color(stylePreset.accentHex)
    val accentSec = Color(stylePreset.secondaryHex)
    val darkSurface = Color(stylePreset.darkSurfaceHex)
    val darkElevated = Color(stylePreset.darkElevatedHex)

    val ylow = if (isDark) {
        YlowColors(
            isDark = true,
            bg = if (themeMode == ThemeMode.AMOLED) Color(0xFF000000) else darkSurface,
            surface = Color(0xFF121215),
            surfaceElevated = darkElevated,
            glassBg = Color(0xD615151B),
            glassHighlight = Color(0x38FFFFFF),
            glassBorder = Color(0x26FFFFFF),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF9898A6),
            accent = accentColor,
            accentSecondary = accentSec
        )
    } else {
        YlowColors(
            isDark = false,
            bg = Color(0xFFF3F3F7),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFE9E9F0),
            glassBg = Color(0xDCFFFFFF),
            glassHighlight = Color(0xB8FFFFFF),
            glassBorder = Color(0x20000000),
            textPrimary = Color(0xFF111116),
            textSecondary = Color(0xFF5D5D6C),
            accent = accentColor,
            accentSecondary = accentSec
        )
    }

    // Native FontFamily.Default inherits device default system font (Motorola Edge 60 Fusion)
    val typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp),
        labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    )

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accentColor,
            secondary = accentSec,
            background = ylow.bg,
            surface = ylow.surface,
            onPrimary = Color.White,
            onBackground = ylow.textPrimary,
            onSurface = ylow.textPrimary
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            secondary = accentSec,
            background = ylow.bg,
            surface = ylow.surface,
            onPrimary = Color.White,
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
