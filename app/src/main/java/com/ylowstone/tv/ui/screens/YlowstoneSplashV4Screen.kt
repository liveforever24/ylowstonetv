package com.ylowstone.tv.ui.screens

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ylowstone.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Splash Screen V4 — Ember Spotlight Curtain
 * Non-skippable, audio custom splash_intro.mp3 dengan LoudnessEnhancer (+14 dB).
 */
@Composable
fun SplashOverlayScreen(onFinished: () -> Unit) {
    // Kunci tombol Back selama animasi splash berjalan
    BackHandler(enabled = true) {}

    val context = LocalContext.current
    val quintOut = remember { CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f) }

    val emberAlpha = remember { Animatable(0f) }
    val emberScale = remember { Animatable(0.45f) }
    val curtainProgress = remember { Animatable(0f) }
    val auraAlpha = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val wordmarkOffsetY = remember { Animatable(18f) }
    val globalAlpha = remember { Animatable(1f) }

    val playerRef = remember { AtomicReference<MediaPlayer?>(null) }
    val enhancerRef = remember { AtomicReference<LoudnessEnhancer?>(null) }

    val releaseAudio = remember {
        {
            enhancerRef.getAndSet(null)?.let { fx ->
                runCatching {
                    fx.enabled = false
                    fx.release()
                }
            }
            playerRef.getAndSet(null)?.let { mp ->
                runCatching {
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { releaseAudio() }
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
        // 0. Putar splash_intro.mp3 dengan penguat volume +14 dB
        launch(Dispatchers.IO) {
            runCatching {
                val mp = MediaPlayer.create(context, R.raw.splash_intro)
                if (mp != null) {
                    mp.setVolume(1.0f, 1.0f)
                    val fx = runCatching {
                        LoudnessEnhancer(mp.audioSessionId).apply {
                            setTargetGain(1400) // +14 dB (bisa naik/turun: 1000=+10dB, 1800=+18dB)
                            enabled = true
                        }
                    }.getOrNull()
                    enhancerRef.set(fx)
                    playerRef.set(mp)
                    mp.setOnCompletionListener { releaseAudio() }
                    mp.setOnErrorListener { _, _, _ ->
                        releaseAudio()
                        true
                    }
                    mp.start()
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
        releaseAudio()
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
