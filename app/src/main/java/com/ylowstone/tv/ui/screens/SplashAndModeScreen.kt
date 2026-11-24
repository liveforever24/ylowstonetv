package com.ylowstone.tv.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.R
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.AppIconOption
import com.ylowstone.tv.model.DeviceMode
import com.ylowstone.tv.ui.components.YlowstoneLogoBadge
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashOverlayScreen(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.94f) }
    val progressAnim = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val ambientGlow by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        logoScale.animateTo(1f, tween(540, easing = FastOutSlowInEasing))
        progressAnim.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
        delay(180)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Fullscreen Portrait Wordmark Splash
        Image(
            painter = painterResource(id = R.drawable.splash_portrait),
            contentDescription = "YlowstoneTV",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(logoScale.value)
                .alpha(logoAlpha.value)
        )

        // Subtle understated breathing halo
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFFFA2D48).copy(alpha = ambientGlow * logoAlpha.value),
                            Color.Transparent
                        )
                    )
                )
        )

        // Minimalist hairline loading bar + credit anchored cleanly at bottom
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp)
                .alpha(logoAlpha.value)
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressAnim.value)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFB5C8D6), Color(0xFFFA2D48))
                            )
                        )
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "by ylowstone",
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp
            )
        }
    }
}

/**
 * Editorial First-Launch Welcome Pop-Up with Quote & circular X close button.
 */
@Composable
fun WelcomeQuotePopupScreen(
    appIcon: AppIconOption,
    strings: AppStrings,
    onDismissToGuide: () -> Unit
) {
    val c = LocalYlowColors.current
    val fade = remember { Animatable(0f) }
    val scale = remember { Animatable(0.95f) }

    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        scale.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .scale(scale.value)
                .alpha(fade.value)
                .clip(RoundedCornerShape(26.dp))
                .background(c.surface)
                .border(1.dp, c.glassBorder, RoundedCornerShape(26.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top Row: Brand Badge + Circular X Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                YlowstoneLogoBadge(iconOption = appIcon, size = 44.dp)

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.surfaceElevated)
                        .border(1.dp, c.glassBorder, CircleShape)
                        .clickable(onClick = onDismissToGuide),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = c.textPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Welcome Title
            Text(
                text = strings.welcomeTitle,
                color = c.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp
            )

            // Elegant 4-line Quote Block with Vertical Pixel Fog Accent Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceElevated.copy(alpha = 0.65f))
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(104.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.accent)
                )
                Text(
                    text = strings.welcomeQuote,
                    color = c.textPrimary.copy(alpha = 0.92f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 27.sp,
                    letterSpacing = 0.2.sp
                )
            }

            // Subtle minimal bottom cue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "— ylowstone",
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

private data class GuideSlide(
    val icon: ImageVector,
    val title: String,
    val desc: String
)

/**
 * Concise 4-step Onboarding Quick Guide for first-time users.
 */
@Composable
fun OnboardingTutorialScreen(
    strings: AppStrings,
    onFinish: () -> Unit
) {
    val c = LocalYlowColors.current
    val scope = rememberCoroutineScope()
    val slides = remember(strings) {
        listOf(
            GuideSlide(Icons.Rounded.PlaylistAdd, strings.guideStep1Title, strings.guideStep1Desc),
            GuideSlide(Icons.Rounded.Lock, strings.guideStep2Title, strings.guideStep2Desc),
            GuideSlide(Icons.Rounded.TouchApp, strings.guideStep3Title, strings.guideStep3Desc),
            GuideSlide(Icons.Rounded.PictureInPictureAlt, strings.guideStep4Title, strings.guideStep4Desc)
        )
    }
    val pagerState = rememberPagerState(pageCount = { slides.size })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar: Skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${pagerState.currentPage + 1} / ${slides.size}",
                color = c.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            TextButton(onClick = onFinish) {
                Text(strings.guideSkip, color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Swipeable Tutorial Cards
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val slide = slides[page]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(c.surface)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(24.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.accentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            slide.icon,
                            contentDescription = slide.title,
                            tint = c.accent,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Text(
                        text = slide.title,
                        color = c.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = slide.desc,
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Page Dots + Next / Start Button
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(slides.size) { idx ->
                    val active = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .height(7.dp)
                            .width(if (active) 24.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (active) c.accent else c.glassBorder)
                    )
                }
            }

            val isLast = pagerState.currentPage == slides.lastIndex
            Button(
                onClick = {
                    if (isLast) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (isLast) strings.guideStart else strings.guideNext,
                    color = c.onAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun DeviceModeSelectionScreen(
    currentMode: DeviceMode,
    appIcon: AppIconOption,
    strings: AppStrings,
    onSelectMode: (DeviceMode) -> Unit
) {
    val c = LocalYlowColors.current
    var picked by remember { mutableStateOf(currentMode) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            YlowstoneLogoBadge(iconOption = appIcon, size = 64.dp)
            Text(
                text = strings.chooseDeviceTitle,
                color = c.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = strings.chooseDeviceSubtitle,
                color = c.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            ModeChoiceCard(
                icon = Icons.Rounded.Smartphone,
                title = strings.smartphone,
                desc = strings.smartphoneDesc,
                selected = picked == DeviceMode.SMARTPHONE,
                onClick = { picked = DeviceMode.SMARTPHONE }
            )

            ModeChoiceCard(
                icon = Icons.Rounded.Tv,
                title = strings.androidTv,
                desc = strings.androidTvDesc,
                selected = picked == DeviceMode.TV,
                onClick = { picked = DeviceMode.TV }
            )

            Spacer(Modifier.height(6.dp))

            Button(
                onClick = { onSelectMode(picked) },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .tvFocusBorder(true, RoundedCornerShape(16.dp))
            ) {
                Text(strings.continueBtn, color = c.onAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ModeChoiceCard(
    icon: ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.accentContainer else c.surface)
            .border(
                width = if (selected) 1.8.dp else 1.dp,
                color = if (selected) c.accent else c.glassBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .tvFocusBorder(true, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) c.accent else c.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = if (selected) c.onAccent else c.textPrimary, modifier = Modifier.size(26.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
        }
    }
}
