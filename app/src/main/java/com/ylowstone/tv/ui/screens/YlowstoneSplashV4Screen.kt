package com.ylowstone.tv.ui.screens

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ylowstone.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Splash Screen V4 — Ember Spotlight Curtain
 * Non-skippable, dilengkapi audio intro sintetis elegan 1,0 detik (auto-normalized).
 */
@Composable
fun SplashOverlayScreen(onFinished: () -> Unit) {
    // Kunci tombol Back selama animasi splash berjalan
    BackHandler(enabled = true) {}

    val quintOut = remember { CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f) }

    val emberAlpha = remember { Animatable(0f) }
    val emberScale = remember { Animatable(0.45f) }
    val curtainProgress = remember { Animatable(0f) }
    val auraAlpha = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val wordmarkOffsetY = remember { Animatable(18f) }
    val globalAlpha = remember { Animatable(1f) }

    val audioRef = remember { AtomicReference<AudioTrack?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            audioRef.getAndSet(null)?.let { track ->
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "liveDot")
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        // 0. Audio intro hangat & jernih tepat 1,0 detik
        launch(Dispatchers.IO) {
            val track = createEmberIntroAudioTrack()
            if (track != null) {
                audioRef.set(track)
                runCatching { track.play() }
                delay(1150)
                audioRef.getAndSet(null)?.let { t ->
                    runCatching {
                        t.stop()
                        t.release()
                    }
                }
            }
        }

        // 1. Inti cahaya ember menyala lebih dulu
        launch {
            emberAlpha.animateTo(0.9f, tween(360, easing = FastOutSlowInEasing))
        }
        launch {
            emberScale.animateTo(1.0f, tween(380, easing = quintOut))
        }

        // 2. Tirai simetris membuka huruf Y dari tengah
        delay(320)
        launch {
            curtainProgress.animateTo(1f, tween(820, easing = quintOut))
        }

        // 3. Aura amber lembut menyusul + ember inti meredup halus
        delay(100)
        launch {
            auraAlpha.animateTo(0.24f, tween(700, easing = FastOutSlowInEasing))
        }
        launch {
            delay(200)
            emberAlpha.animateTo(0.20f, tween(500))
        }

        // 4. Wordmark YlowstoneTV • muncul di bawah
        delay(340)
        launch {
            wordmarkAlpha.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }
        launch {
            wordmarkOffsetY.animateTo(0f, tween(550, easing = quintOut))
        }

        // 5. Tahan sebentar lalu fade-out mulus ke layar utama
        delay(1450)
        globalAlpha.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Hitam AMOLED murni #000000
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(globalAlpha.value)
        ) {
            // Pendaran aura amber di belakang huruf Y
            Canvas(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.Center)
                    .offset(y = (-24).dp)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFDE902C).copy(alpha = auraAlpha.value * ambientPulse),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension / 2f
                    )
                )

                // Inti cahaya ember di cekungan atas huruf Y
                val emberCenter = Offset(center.x, center.y - size.height * 0.12f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF5A537).copy(alpha = emberAlpha.value),
                            Color.Transparent
                        ),
                        center = emberCenter,
                        radius = (size.minDimension * 0.22f) * emberScale.value
                    ),
                    center = emberCenter,
                    radius = (size.minDimension * 0.22f) * emberScale.value
                )
            }

            // Huruf Y + Amber Sunstone dengan efek tirai simetris dari tengah
            Image(
                painter = painterResource(id = R.drawable.ylowstone_y_emblem),
                contentDescription = "YlowstoneTV Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(138.dp)
                    .align(Alignment.Center)
                    .offset(y = (-24).dp)
                    .drawWithContent {
                        val halfW = (size.width / 2f) * curtainProgress.value
                        clipRect(
                            left = (size.width / 2f) - halfW,
                            top = 0f,
                            right = (size.width / 2f) + halfW,
                            bottom = size.height
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
            )

            // Wordmark YlowstoneTV • di bagian bawah
            Image(
                painter = painterResource(id = R.drawable.ylowstone_wordmark),
                contentDescription = "YlowstoneTV",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 44.dp)
                    .width(156.dp)
                    .offset(y = wordmarkOffsetY.value.dp)
                    .alpha(wordmarkAlpha.value)
            )
        }
    }
}

private fun createEmberIntroAudioTrack(): AudioTrack? = runCatching {
    val sampleRate = 44100
    val durationSec = 1.0
    val numSamples = (sampleRate * durationSec).toInt()
    val raw = DoubleArray(numSamples)

    // Warm cinematic Dmaj9 chime — jernih di speaker HP, halus, selesai bersih di 1,0 detik
    val freqs  = doubleArrayOf(220.00, 293.66, 369.99, 440.00, 554.37, 659.25, 880.00)
    val amps   = doubleArrayOf(0.26,   0.30,   0.26,   0.28,   0.20,   0.18,   0.12)
    val decays = doubleArrayOf(1.6,    1.7,    1.9,    2.1,    2.5,    2.9,    3.6)

    var maxAbs = 1e-6
    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val attack  = if (t < 0.08) 0.5 * (1.0 - cos(PI * t / 0.08)) else 1.0
        val release = if (t > 0.72) 0.5 * (1.0 + cos(PI * (t - 0.72) / 0.28)) else 1.0

        var sample = 0.0
        for (k in freqs.indices) {
            sample += amps[k] * exp(-decays[k] * t) * sin(2.0 * PI * freqs[k] * t)
        }
        // Soft analog warmth agar terasa penuh tapi tidak pecah
        val shaped = tanh(sample * 1.35) * attack * release
        raw[i] = shaped
        val a = abs(shaped)
        if (a > maxAbs) maxAbs = a
    }

    // Normalisasi otomatis ke 92% volume maksimal
    val pcm = ShortArray(numSamples)
    val scale = 0.92 / maxAbs
    for (i in 0 until numSamples) {
        val v = (raw[i] * scale).coerceIn(-1.0, 1.0)
        pcm[i] = (v * Short.MAX_VALUE).toInt().toShort()
    }

    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(pcm.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

    track.write(pcm, 0, pcm.size)
    track.setVolume(1.0f)
    track
}.getOrNull()
