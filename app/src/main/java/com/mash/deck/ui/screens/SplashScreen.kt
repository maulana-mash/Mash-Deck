package com.mash.deck.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mash.deck.R
import com.mash.deck.model.DeckBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    background: DeckBackground,
    onFinished: () -> Unit
) {
    val bgResId = when (background) {
        DeckBackground.DEEP_INDIGO -> R.drawable.bg_mesh
        DeckBackground.CARBON_WEAVE -> R.drawable.bg_carbon
        DeckBackground.AURORA_GLOW -> R.drawable.bg_aurora
        DeckBackground.BRUSHED_TITANIUM -> R.drawable.bg_brushed
        DeckBackground.SOLID_DARK -> null
    }

    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.85f) }

    LaunchedEffect(Unit) {
        // Smooth entrance
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        launch {
            scaleAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        // Display hold
        delay(1100)

        // Smooth exit
        alphaAnim.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        )
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0E)),
        contentAlignment = Alignment.Center
    ) {
        // Background texture adhering to Settings faceplate preference
        if (bgResId != null) {
            Image(
                painter = painterResource(id = bgResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Ambient dimming vignette for contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
            )
        }

        // Ambient radial glow behind the logo
        Box(
            modifier = Modifier
                .size(160.dp)
                .alpha(alphaAnim.value * 0.45f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF5B7898).copy(alpha = 0.25f),
                            Color(0xFF405870).copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Centered Mash Deck App Logo
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "Mash Deck",
            modifier = Modifier
                .size(108.dp)
                .scale(scaleAnim.value)
                .alpha(alphaAnim.value)
        )
    }
}
