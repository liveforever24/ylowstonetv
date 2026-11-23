package com.ylowstone.tv.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tv
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

@Composable
fun SplashOverlayScreen(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.92f) }
    val progressAnim = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val ambientGlow by infiniteTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        logoScale.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        progressAnim.animateTo(1f, tween(780, easing = FastOutSlowInEasing))
        delay(180)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Subtle understated crimson ambient halo
        Box(
            modifier = Modifier
                .size(340.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFFFA2D48).copy(alpha = ambientGlow * logoAlpha.value),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(logoScale.value)
                .alpha(logoAlpha.value)
                .padding(horizontal = 32.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.splash_logo),
                contentDescription = "YlowstoneTV",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .widthIn(max = 350.dp)
                    .aspectRatio(2.4f)
            )
            Spacer(Modifier.height(22.dp))
            // Minimalist hairline specular loading bar
            Box(
                modifier = Modifier
                    .width(124.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressAnim.value)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.White.copy(alpha = 0.65f), Color(0xFFFA2D48))
                            )
                        )
                )
            }
        }

        Text(
            text = "by ylowstone",
            color = Color.White.copy(alpha = 0.36f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.6.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp)
                .alpha(logoAlpha.value)
        )
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
            YlowstoneLogoBadge(iconOption = appIcon, size = 66.dp)
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
                colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .tvFocusBorder(true, RoundedCornerShape(16.dp))
            ) {
                Text(strings.continueBtn, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) c.accent.copy(alpha = 0.14f) else c.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) c.accent else c.glassBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .tvFocusBorder(true, RoundedCornerShape(20.dp))
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
            Icon(icon, contentDescription = title, tint = if (selected) Color.White else c.textPrimary, modifier = Modifier.size(26.dp))
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
