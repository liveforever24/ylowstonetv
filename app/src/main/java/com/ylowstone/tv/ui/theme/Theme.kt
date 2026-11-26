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
    val accentContainer: Color
)

// Default Ylowstone Signature: Pure Black AMOLED (#000000) + Champagne-Taupe Accent (#B09878)
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
        accent = Color(0xFFB09878),
        onAccent = Color(0xFF14110D),
        accentContainer = Color(0xFF241E17)
    )
}

fun accentSwatchColor(preset: AccentPreset): Color = when (preset) {
    AccentPreset.AMBER_SUNSTONE -> Color(0xFFC4A67E)
    AccentPreset.AMOLED_NOIR -> Color(0xFFB09878)
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
    val isDark = when (themeMode) {
        ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val (accent, onAccent, accentContainer) = if (isDark) {
        when (accentPreset) {
            AccentPreset.AMBER_SUNSTONE -> Triple(Color(0xFFC4A67E), Color(0xFF14110D), Color(0xFF262018))
            AccentPreset.AMOLED_NOIR -> Triple(Color(0xFFB09878), Color(0xFF0D0B09), Color(0xFF1E1A15))
            AccentPreset.VELVET_CRIMSON -> Triple(Color(0xFFFA2D48), Color(0xFFFFFFFF), Color(0xFF36121A))
            AccentPreset.SLATE_MIST -> Triple(Color(0xFFB5C8D6), Color(0xFF0B1116), Color(0xFF202B33))
            AccentPreset.HYDROGEN_ICE -> Triple(Color(0xFF38BDF8), Color(0xFF07131C), Color(0xFF102A3A))
            AccentPreset.EMERALD_STUDIO -> Triple(Color(0xFF10B981), Color(0xFF041610), Color(0xFF0E2D22))
        }
    } else {
        when (accentPreset) {
            AccentPreset.AMBER_SUNSTONE -> Triple(Color(0xFF8C6E48), Color(0xFFFFFFFF), Color(0xFFF3ECE2))
            AccentPreset.AMOLED_NOIR -> Triple(Color(0xFF5C4D3B), Color(0xFFFFFFFF), Color(0xFFEBE5DC))
            AccentPreset.VELVET_CRIMSON -> Triple(Color(0xFFD91E3A), Color(0xFFFFFFFF), Color(0xFFFCE4E8))
            AccentPreset.SLATE_MIST -> Triple(Color(0xFF526A7A), Color(0xFFFFFFFF), Color(0xFFDCE5EC))
            AccentPreset.HYDROGEN_ICE -> Triple(Color(0xFF0284C7), Color(0xFFFFFFFF), Color(0xFFE0F2FE))
            AccentPreset.EMERALD_STUDIO -> Triple(Color(0xFF059669), Color(0xFFFFFFFF), Color(0xFFD1FAE5))
        }
    }

    val isSignature = accentPreset == AccentPreset.AMBER_SUNSTONE
    val isNoir = accentPreset == AccentPreset.AMOLED_NOIR

    val ylow = if (isDark) {
        YlowColors(
            isDark = true,
            bg = Color(0xFF000000),
            surface = when {
                isNoir -> Color(0xFF0D0C0B)
                isSignature -> Color(0xFF11100E)
                else -> Color(0xFF121416)
            },
            surfaceElevated = when {
                isNoir -> Color(0xFF161412)
                isSignature -> Color(0xFF1B1815)
                else -> Color(0xFF1B1E22)
            },
            navBarBg = Color(0xFF0A0908),
            glassBorder = when {
                isNoir || isSignature -> Color(0xFF2D2720)
                else -> Color(0xFF23282D)
            },
            textPrimary = if (isSignature || isNoir) Color(0xFFF5F1E8) else Color(0xFFFFFFFF),
            textSecondary = if (isSignature || isNoir) Color(0xFF9C968F) else Color(0xFF9AA3AB),
            accent = accent,
            onAccent = onAccent,
            accentContainer = accentContainer
        )
    } else {
        YlowColors(
            isDark = false,
            bg = Color(0xFFF5F6F4),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFEAECE8),
            navBarBg = Color(0xFFFFFFFF),
            glassBorder = Color(0xFFD8DDD6),
            textPrimary = Color(0xFF121512),
            textSecondary = Color(0xFF5D655E),
            accent = accent,
            onAccent = onAccent,
            accentContainer = accentContainer
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
