package com.ylowstone.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.SkipIntroMode
import com.ylowstone.tv.ui.theme.LocalYlowColors
import java.util.Locale

fun formatSkipIntroMmSs(totalSeconds: Int): String {
    val safe = totalSeconds.coerceIn(5, 600)
    return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
}

/**
 * Kontrol Custom Skip Intro (Mati / Tombol / Otomatis + Stepper Menit & Detik MM:SS)
 * langsung di dalam menu Pengaturan Video (Fullscreen & Layar Setengah — Mode Ponsel & TV).
 */
@Composable
fun SkipIntroInlineSection(
    mode: SkipIntroMode,
    seconds: Int,
    strings: AppStrings,
    onUpdate: (SkipIntroMode, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    val isIndo = strings.watchHistory.startsWith("Riwayat", ignoreCase = true)
    val safeSec = seconds.coerceIn(5, 600)
    val curMin = safeSec / 60
    val curSec = safeSec % 60

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.FastForward, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
            Text(
                text = strings.skipIntroTitle,
                color = c.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Mode: Mati / Tombol / Otomatis
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceElevated)
                .border(0.8.dp, c.glassBorder, RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf(
                SkipIntroMode.OFF to strings.skipIntroOff,
                SkipIntroMode.MANUAL to strings.skipIntroManual,
                SkipIntroMode.AUTO to strings.skipIntroAuto
            ).forEach { (optMode, lbl) ->
                val active = mode == optMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) c.accent else Color.Transparent)
                        .clickable { onUpdate(optMode, safeSec) }
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lbl,
                        color = if (active) c.onAccent else c.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (mode != SkipIntroMode.OFF) {
            Text(
                text = "${strings.skipIntroDurationLabel}: ${formatSkipIntroMmSs(safeSec)}",
                color = c.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Preset Cepat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(60 to "01:00", 85 to "01:25", 100 to "01:40").forEach { (presetSec, lbl) ->
                    val active = safeSec == presetSec
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) c.accentContainer else c.surfaceElevated)
                            .border(0.9.dp, if (active) c.accent else c.glassBorder, RoundedCornerShape(9.dp))
                            .clickable { onUpdate(mode, presetSec) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lbl,
                            color = if (active) c.accent else c.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Custom Stepper Menit & Detik
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surfaceElevated)
                        .border(0.8.dp, c.glassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            val next = ((curMin - 1).coerceAtLeast(0) * 60 + curSec).coerceIn(5, 600)
                            onUpdate(mode, next)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "-", tint = c.accent, modifier = Modifier.size(15.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format(Locale.US, "%02d", curMin), color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(if (isIndo) "Menit" else "Min", color = c.textSecondary, fontSize = 9.sp)
                    }
                    IconButton(
                        onClick = {
                            val next = ((curMin + 1).coerceAtMost(10) * 60 + curSec).coerceIn(5, 600)
                            onUpdate(mode, next)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "+", tint = c.accent, modifier = Modifier.size(15.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surfaceElevated)
                        .border(0.8.dp, c.glassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            val next = (safeSec - 1).coerceIn(5, 600)
                            onUpdate(mode, next)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "-", tint = c.accent, modifier = Modifier.size(15.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format(Locale.US, "%02d", curSec), color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(if (isIndo) "Detik" else "Sec", color = c.textSecondary, fontSize = 9.sp)
                    }
                    IconButton(
                        onClick = {
                            val next = (safeSec + 1).coerceIn(5, 600)
                            onUpdate(mode, next)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "+", tint = c.accent, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}
