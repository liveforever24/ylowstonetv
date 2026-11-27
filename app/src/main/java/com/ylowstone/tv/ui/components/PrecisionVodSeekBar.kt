package com.ylowstone.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.ui.theme.LocalYlowColors
import com.ylowstone.tv.util.FlagshipFeedback
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun formatPrecisionMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

/**
 * Seekbar Presisi Baru v2.5.1 (Khusus Film & Seri):
 * - Ketuk atau seret kanan/kiri pada garis/titik untuk melompat instan ke detik yang diinginkan.
 * - Tarik jari ke atas saat menggeser untuk masuk Mode Akurasi Tinggi (fine-scrubbing 0.22x +
 *   strip pratinjau skala detik detail + indikator ±detik).
 */
@Composable
fun PrecisionVodSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekCommitted: (Long) -> Unit,
    onDraggingStateChanged: (Boolean) -> Unit,
    showTimeRow: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    val view = LocalView.current
    val density = LocalDensity.current
    val safeDuration = durationMs.coerceAtLeast(1L)

    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var isFinePrecision by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var initialPosMs by remember { mutableLongStateOf(positionMs) }

    val fineThresholdPx = remember(density) { with(density) { 26.dp.toPx() } }

    val displayFraction = if (isScrubbing) {
        scrubFraction
    } else {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }
    val displayPosMs = if (isScrubbing) (scrubFraction * safeDuration).toLong() else positionMs
    val deltaSec = ((displayPosMs - initialPosMs) / 1000L).toInt()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)
    ) {
        // Strip Pratinjau Akurasi Tinggi saat jari ditarik ke atas ketika menggeser seekbar
        AnimatedVisibility(
            visible = isScrubbing && isFinePrecision,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.84f))
                    .border(1.dp, c.accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatPrecisionMs(displayPosMs),
                        color = Color.White,
                        fontSize = if (compact) 14.sp else 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (deltaSec != 0) {
                        Text(
                            text = if (deltaSec > 0) "+${deltaSec}s" else "${deltaSec}s",
                            color = c.accent,
                            fontSize = if (compact) 11.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // Penggaris bingkai/detik halus bergerak presisi mengikuti posisi scrub
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 18.dp else 24.dp)
                ) {
                    val centerX = size.width / 2f
                    val secPos = displayPosMs / 1000f
                    val pxPerSec = 18f
                    val halfTicks = (size.width / (2f * pxPerSec)).toInt() + 2
                    val baseSec = secPos.toInt()
                    for (offset in -halfTicks..halfTicks) {
                        val s = baseSec + offset
                        if (s < 0) continue
                        val x = centerX + (s - secPos) * pxPerSec
                        if (x < 0f || x > size.width) continue
                        val isMajor = s % 5 == 0
                        val tickH = if (isMajor) size.height * 0.78f else size.height * 0.42f
                        drawLine(
                            color = if (isMajor) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.34f),
                            start = Offset(x, size.height - tickH),
                            end = Offset(x, size.height),
                            strokeWidth = if (isMajor) 2.2f else 1.4f
                        )
                    }
                    // Jarum penunjuk tengah
                    drawLine(
                        color = c.accent,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, size.height),
                        strokeWidth = 3.2f
                    )
                }
            }
        }

        if (showTimeRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatPrecisionMs(displayPosMs),
                    color = if (isScrubbing) c.accent else Color.White.copy(alpha = 0.92f),
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatPrecisionMs(durationMs),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Area Sentuh: Ketuk langsung lompat, Seret kanan/kiri, Tarik ke atas untuk Akurasi Tinggi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 26.dp else 32.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(durationMs, fineThresholdPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val startY = down.position.y
                        initialPosMs = positionMs
                        val tapFrac = (down.position.x / barWidthPx).coerceIn(0f, 1f)
                        scrubFraction = tapFrac
                        isScrubbing = true
                        isFinePrecision = false
                        onDraggingStateChanged(true)

                        var prevX = down.position.x
                        var enteredFineHaptic = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                change.consume()
                                val target = (scrubFraction * safeDuration).toLong().coerceIn(0L, safeDuration)
                                isScrubbing = false
                                isFinePrecision = false
                                onDraggingStateChanged(false)
                                FlagshipFeedback.playTick(view)
                                onSeekCommitted(target)
                                break
                            }
                            change.consume()
                            val liftUpPx = startY - change.position.y
                            val nowFine = liftUpPx >= fineThresholdPx
                            if (nowFine && !enteredFineHaptic) {
                                enteredFineHaptic = true
                                FlagshipFeedback.playTick(view)
                            }
                            isFinePrecision = nowFine

                            val dx = change.position.x - prevX
                            prevX = change.position.x
                            if (abs(dx) > 0.05f) {
                                val sensitivity = if (nowFine) 0.22f else 1.0f
                                scrubFraction = (scrubFraction + (dx * sensitivity) / barWidthPx).coerceIn(0f, 1f)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isScrubbing) 5.5.dp else 3.5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.28f))
            )
            // Filled progress track
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayFraction)
                    .height(if (isScrubbing) 5.5.dp else 3.5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.accent)
            )
            // Thumb knob
            val thumbDp = when {
                isScrubbing && isFinePrecision -> 18.dp
                isScrubbing -> 16.dp
                compact -> 12.dp
                else -> 14.dp
            }
            Box(
                modifier = Modifier
                    .offset {
                        val thumbPx = thumbDp.toPx()
                        val x = (displayFraction * (barWidthPx - thumbPx)).roundToInt()
                        IntOffset(x, 0)
                    }
                    .size(thumbDp)
                    .shadow(5.dp, CircleShape)
                    .clip(CircleShape)
                    .background(c.accent)
                    .border(1.8.dp, Color.White, CircleShape)
            )
        }
    }
}
