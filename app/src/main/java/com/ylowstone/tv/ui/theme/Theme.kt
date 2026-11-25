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

// Default Ylowstone Signature: Pure Black AMOLED + Smoked Olive-Charcoal + Amber Sunstone
val LocalYlowColors = staticCompositionLocalOf {
    YlowColors(
        isDark = true,
        bg = Color(0xFF000000),
        surface = Color(0xFF121512),
        surfaceElevated = Color(0xFF1A1E19),
        navBarBg = Color(0xFF101210),
        glassBorder = Color(0xFF272B24),
        textPrimary = Color(0xFFF5F1E8),
        textSecondary = Color(0xFFA0A59B),
        accent = Color(0xFFE89D3C),
        onAccent = Color(0xFF140D02),
        accentContainer = Color(0xFF2B2110)
    )
}

fun accentSwatchColor(preset: AccentPreset): Color = when (preset) {
    AccentPreset.AMBER_SUNSTONE -> Color(0xFFE89D3C)
    AccentPreset.AMOLED_NOIR -> Color(0xFFE5E7EB)
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
            AccentPreset.AMBER_SUNSTONE -> Triple(Color(0xFFE89D3C), Color(0xFF140D02), Color(0xFF2B2110))
            AccentPreset.AMOLED_NOIR -> Triple(Color(0xFFE5E7EB), Color(0xFF0A0A0A), Color(0xFF1F2023))
            AccentPreset.VELVET_CRIMSON -> Triple(Color(0xFFFA2D48), Color(0xFFFFFFFF), Color(0xFF36121A))
            AccentPreset.SLATE_MIST -> Triple(Color(0xFFB5C8D6), Color(0xFF0B1116), Color(0xFF202B33))
            AccentPreset.HYDROGEN_ICE -> Triple(Color(0xFF38BDF8), Color(0xFF07131C), Color(0xFF102A3A))
            AccentPreset.EMERALD_STUDIO -> Triple(Color(0xFF10B981), Color(0xFF041610), Color(0xFF0E2D22))
        }
    } else {
        when (accentPreset) {
            AccentPreset.AMBER_SUNSTONE -> Triple(Color(0xFFC97B16), Color(0xFFFFFFFF), Color(0xFFFEF3C7))
            AccentPreset.AMOLED_NOIR -> Triple(Color(0xFF18181B), Color(0xFFFFFFFF), Color(0xFFE4E4E7))
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
                isNoir -> Color(0xFF0C0C0D)
                isSignature -> Color(0xFF121512)
                else -> Color(0xFF141618)
            },
            surfaceElevated = when {
                isNoir -> Color(0xFF161618)
                isSignature -> Color(0xFF1A1E19)
                else -> Color(0xFF1D2125)
            },
            navBarBg = when {
                isNoir -> Color(0xFF09090A)
                isSignature -> Color(0xFF101210)
                else -> Color(0xFF121416)
            },
            glassBorder = when {
                isNoir -> Color(0xFF222226)
                isSignature -> Color(0xFF272C24)
                else -> Color(0xFF23282D)
            },
            textPrimary = if (isSignature) Color(0xFFF6F2EA) else Color(0xFFFFFFFF),
            textSecondary = if (isSignature) Color(0xFFA0A69B) else Color(0xFF9AA3AB),
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
        headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.4).sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
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
