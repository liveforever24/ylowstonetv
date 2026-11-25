package com.ylowstone.tv.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.model.AccentPreset
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

object YlowMotion {
    fun <T> snappySpring() = spring<T>(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMediumLow
    )
}

fun resolveAccentTokens(preset: AccentPreset, isDark: Boolean): Triple<Color, Color, Color> {
    return when (preset) {
        AccentPreset.PIXEL_FOG -> if (isDark) {
            Triple(Color(0xFFB5C8D6), Color(0xFF0B1116), Color(0xFF202B33))
        } else {
            Triple(Color(0xFF526A7A), Color(0xFFFFFFFF), Color(0xFFDCE5EC))
        }
        AccentPreset.OBSIDIAN_CRIMSON -> if (isDark) {
            Triple(Color(0xFFFF453A), Color(0xFFFFFFFF), Color(0xFF331413))
        } else {
            Triple(Color(0xFFC82820), Color(0xFFFFFFFF), Color(0xFFFBE1DF))
        }
        AccentPreset.HYDROGEN_CYAN -> if (isDark) {
            Triple(Color(0xFF38BDF8), Color(0xFF06131A), Color(0xFF132A38))
        } else {
            Triple(Color(0xFF0284C7), Color(0xFFFFFFFF), Color(0xFFE0F2FE))
        }
        AccentPreset.EMERALD_STUDIO -> if (isDark) {
            Triple(Color(0xFF34D399), Color(0xFF061811), Color(0xFF112E23))
        } else {
            Triple(Color(0xFF059669), Color(0xFFFFFFFF), Color(0xFFD1FAE5))
        }
    }
}

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
    accentPreset: AccentPreset = AccentPreset.PIXEL_FOG,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val (accent, onAccent, accentContainer) = resolveAccentTokens(accentPreset, isDark)

    val ylow = if (isDark) {
        YlowColors(
            isDark = true,
            bg = Color(0xFF000000),
            surface = Color(0xFF141618),
            surfaceElevated = Color(0xFF1D2125),
            navBarBg = Color(0xFF121416),
            glassBorder = Color(0xFF23282D),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF9AA3AB),
            accent = accent,
            onAccent = onAccent,
            accentContainer = accentContainer
        )
    } else {
        YlowColors(
            isDark = false,
            bg = Color(0xFFF3F5F7),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFE7ECF0),
            navBarBg = Color(0xFFFFFFFF),
            glassBorder = Color(0xFFD8DFE5),
            textPrimary = Color(0xFF111417),
            textSecondary = Color(0xFF5D6770),
            accent = accent,
            onAccent = onAccent,
            accentContainer = accentContainer
        )
    }

    val typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.4).sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp),
        labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.2.sp)
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

fun responsiveGridMinSize(isTvMode: Boolean, maxWidth: Dp): Dp {
    return when {
        isTvMode -> 176.dp
        maxWidth >= 600.dp -> 148.dp
        else -> 114.dp
    }
}
