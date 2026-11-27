package com.ylowstone.tv.player.stabilizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Pil status sementara universal untuk seluruh tipe aliran YlowstoneTV.
 * v2.4.7: Label protokol (HLS / TS / VOD) hanya tampil saat showProtocolBadge = true (Mode Admin).
 */
@Composable
fun StabilizerStatusPill(
    state: StabilizerState,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProtocolBadge: Boolean = true,
    autoHideMillis: Long = 2_800L
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (state) {
            is StabilizerState.Idle -> {
                visible = false
            }
            is StabilizerState.Playing -> {
                visible = true
                delay(autoHideMillis)
                visible = false
            }
            is StabilizerState.SwitchingChannel,
            is StabilizerState.SwitchingNetwork,
            is StabilizerState.Reconnecting,
            is StabilizerState.QuotaExhausted,
            is StabilizerState.Failed -> {
                visible = true
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val (dotColor, label, showSpinner, clickableRetry) = when (state) {
        is StabilizerState.Playing -> {
            val text = if (showProtocolBadge) {
                if (state.recoveredFromIssue) {
                    "Tersambung kembali · ${state.protocol.shortLabel}"
                } else {
                    "Stream aktif · ${state.protocol.shortLabel}"
                }
            } else {
                if (state.recoveredFromIssue) "Tersambung kembali" else "Stream aktif"
            }
            PillVisualSpec(Color(0xFF4CAF50), text, false, false)
        }
        is StabilizerState.SwitchingChannel -> {
            val text = state.channelLabel?.let { "Memuat $it..." } ?: "Memuat aliran..."
            PillVisualSpec(colorScheme.primary, text, true, false)
        }
        is StabilizerState.SwitchingNetwork -> {
            PillVisualSpec(Color(0xFFFFB300), "Pindah ke ${state.networkLabel}...", true, false)
        }
        is StabilizerState.Reconnecting -> {
            val text = "Menyambung ulang (${state.attempt}/${state.maxAttempts}) · ${state.reason}"
            PillVisualSpec(Color(0xFFFFB300), text, true, false)
        }
        is StabilizerState.QuotaExhausted -> {
            val text = "Kuota penuh (${state.activeConnections}/${state.maxConnections}) · Ketuk coba lagi"
            PillVisualSpec(Color(0xFFE53935), text, false, true)
        }
        is StabilizerState.Failed -> {
            val text = "${state.message} · Ketuk coba lagi"
            PillVisualSpec(Color(0xFFE53935), text, false, true)
        }
        is StabilizerState.Idle -> {
            PillVisualSpec(Color.Transparent, "", false, false)
        }
    }

    AnimatedVisibility(
        visible = visible && label.isNotEmpty(),
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier
    ) {
        val pillShape = RoundedCornerShape(999.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(pillShape)
                .background(colorScheme.surface.copy(alpha = 0.86f))
                .border(1.dp, colorScheme.outline.copy(alpha = 0.28f), pillShape)
                .then(
                    if (clickableRetry) {
                        Modifier.clickable(onClick = onRetryClick)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.6.dp,
                    color = dotColor
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

private data class PillVisualSpec(
    val dotColor: Color,
    val label: String,
    val showSpinner: Boolean,
    val clickableRetry: Boolean
)
