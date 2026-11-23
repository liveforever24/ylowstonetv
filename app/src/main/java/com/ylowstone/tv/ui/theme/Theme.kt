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
import com.ylowstone.tv.model.AccentTheme
import com.ylowstone.tv.model.ThemeMode

data class YlowColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val glassBg: Color,
    val glassBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color
)

val LocalYlowColors = staticCompositionLocalOf {
    YlowColors(
        isDark = true,
        bg = Color.Black,
        surface = Color(0xFF111114),
        surfaceElevated = Color(0xFF1C1C22),
        glassBg = Color(0xCC16161C),
        glassBorder = Color(0x33FFFFFF),
        textPrimary = Color.White,
        textSecondary = Color(0xFFA0A0AE),
        accent = Color(0xFFFA2D48)
    )
}

@Composable
fun YlowstoneTheme(
    themeMode: ThemeMode,
    accent: AccentTheme,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val accentColor = Color(accent.hex)

    val ylow = if (isDark) {
        YlowColors(
            isDark = true,
            bg = Color(0xFF000000),
            surface = Color(0xFF111114),
            surfaceElevated = Color(0xFF1B1B21),
            glassBg = Color(0xD916161C),
            glassBorder = Color(0x2EFFFFFF),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF9E9EAC),
            accent = accentColor
        )
    } else {
        YlowColors(
            isDark = false,
            bg = Color(0xFFF4F4F7),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFEAEAF0),
            glassBg = Color(0xDCFFFFFF),
            glassBorder = Color(0x24000000),
            textPrimary = Color(0xFF111116),
            textSecondary = Color(0xFF5D5D6C),
            accent = accentColor
        )
    }

    // Uses FontFamily.Default to inherit Motorola Edge 60 Fusion / device native system font
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
            background = ylow.bg,
            surface = ylow.surface,
            onPrimary = Color.White,
            onBackground = ylow.textPrimary,
            onSurface = ylow.textPrimary
        )
    } else {
        lightColorScheme(
            primary = accentColor,
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
