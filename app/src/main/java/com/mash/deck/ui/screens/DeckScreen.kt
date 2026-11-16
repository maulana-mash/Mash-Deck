package com.mash.deck.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mash.deck.R
import com.mash.deck.model.DeckBackground
import com.mash.deck.model.DeckConfig
import com.mash.deck.network.ConnectionState
import com.mash.deck.ui.theme.*

@Composable
fun DeckScreen(
    serverName: String,
    connectionState: ConnectionState,
    config: DeckConfig,
    buttonBitmaps: Map<Int, Bitmap>,
    currentPage: Int = 1,
    totalPages: Int = 1,
    isPureFullscreen: Boolean = false,
    onKeyPress: (index: Int, pressed: Boolean) -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? Vibrator
                ?: context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(10)
            }
        } catch (_: Exception) {}
    }

    val bgResId = when (config.background) {
        DeckBackground.DEEP_INDIGO -> R.drawable.bg_mesh
        DeckBackground.CARBON_WEAVE -> R.drawable.bg_carbon
        DeckBackground.AURORA_GLOW -> R.drawable.bg_aurora
        DeckBackground.BRUSHED_TITANIUM -> R.drawable.bg_brushed
        DeckBackground.SOLID_DARK -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // High-end decorative background texture if chosen
        if (bgResId != null) {
            Image(
                painter = painterResource(id = bgResId),
                contentDescription = "Deck Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Ambient vignette overlay for deep contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x59000000))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isPureFullscreen) Modifier.statusBarsPadding() else Modifier.padding(top = 4.dp))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Elegant Muted Control Toolbar (Dark Slate-Blue Styling)
            val slateBlueBorder = Color(0x2E5B7898)
            val toolbarSlateBlue = Color(0xFF6B86A4)
            val textSlateNeutral = Color(0xFFCAD4DF)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Server Name & Dynamic Status Badge with subdued slate-blue glow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(RadiusPill))
                        .background(if (bgResId != null) Color(0xD9141820) else SurfaceCard)
                        .border(1.dp, slateBlueBorder, RoundedCornerShape(RadiusPill))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val (statusColor, glowColor) = when (connectionState) {
                        ConnectionState.CONNECTED -> Pair(Color(0xFF507294), Color(0x18507294))
                        ConnectionState.CONNECTING, ConnectionState.WAITING_FOR_PAIRING -> Pair(AccentYellow, Color(0x22F59E0B))
                        else -> Pair(AccentRed, Color(0x22EF4444))
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(glowColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = serverName,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 12.sp,
                        color = textSlateNeutral
                    )
                }

                // Quick Actions Toolbar Group with Muted Slate-Blue Accents & Stream Deck Page Indicator Template
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(RadiusPill))
                        .background(if (bgResId != null) Color(0xD9141820) else SurfaceCard)
                        .border(1.dp, slateBlueBorder, RoundedCornerShape(RadiusPill))
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    // Previous Page (<)
                    IconButton(onClick = onPrevPage, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Page",
                            tint = toolbarSlateBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Stream Deck PC Template Page Indicator Key (Rounded key showing current page)
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .widthIn(min = 22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF161A22))
                            .border(1.dp, Color(0x386B86A4), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$currentPage",
                            color = toolbarSlateBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Next Page (>)
                    IconButton(onClick = onNextPage, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Page",
                            tint = toolbarSlateBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(Color(0x245B7898))
                    )
                    IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = toolbarSlateBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = toolbarSlateBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(Color(0x245B7898))
                    )
                    IconButton(onClick = onDisconnect, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = "Disconnect",
                            tint = Color(0xFFA66262),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Adaptive Responsive Keypad Grid (Fit-to-Screen constraint math with breathing room)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val cols = maxOf(config.activeColumns, 1)
                val rows = maxOf(config.activeRows, 1)
                
                // Spacing between keys (generous margin so buttons don't stick to each other)
                val spacing = if (cols >= 8) 10.dp else if (cols >= 5) 14.dp else 18.dp

                // Generous outer margins around keypad so it's not glued to the screen edges
                val availableW = maxOf(maxWidth - 24.dp, 0.dp)
                val availableH = maxOf(maxHeight - 16.dp, 0.dp)

                // Prevent grid from overflowing height or width:
                val maxKeyW = maxOf((availableW - spacing * (cols - 1)) / cols, 10.dp)
                val maxKeyH = maxOf((availableH - spacing * (rows - 1)) / rows, 10.dp)
                val keySize = minOf(maxKeyW, maxKeyH)

                val gridW = keySize * cols + spacing * (cols - 1)
                val gridH = keySize * rows + spacing * (rows - 1)

                Column(
                    modifier = Modifier
                        .width(gridW)
                        .height(gridH),
                    verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (row in 0 until rows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(keySize),
                            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
                        ) {
                            for (col in 0 until cols) {
                                val keyIndex = row * cols + col
                                DeckKeyButton(
                                    index = keyIndex,
                                    bitmap = buttonBitmaps[keyIndex],
                                    keySize = keySize,
                                    hasBackground = bgResId != null,
                                    onPressChange = { isPressed ->
                                        if (isPressed) triggerHaptic()
                                        onKeyPress(keyIndex, isPressed)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeckKeyButton(
    index: Int,
    bitmap: Bitmap?,
    keySize: Dp,
    hasBackground: Boolean,
    modifier: Modifier = Modifier,
    onPressChange: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    // Emil Kowalski physics: 80ms scale down to 0.94f, instant release
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "ButtonScale"
    )

    // Concentric squircle curvature: dynamic R_outer ~24% of keySize (rounder, non-boxy)
    val outerRadius = maxOf(keySize * 0.24f, 18.dp)
    val innerRadius = maxOf(outerRadius - 3.5.dp, 6.dp)

    val defaultCardBg = if (hasBackground) Color(0xD918181B) else SurfaceCard
    val defaultBorder = if (hasBackground) Color(0x285B7898) else Color(0x1E5B7898)

    val bgColor by animateColorAsState(
        targetValue = if (isPressed) Color(0x383F5770) else defaultCardBg,
        animationSpec = tween(durationMillis = 80),
        label = "BgColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF6B86A4) else defaultBorder,
        animationSpec = tween(durationMillis = 80),
        label = "BorderColor"
    )

    Box(
        modifier = modifier
            .size(keySize)
            .scale(scale)
            .clip(RoundedCornerShape(outerRadius))
            .background(bgColor)
            .border(
                width = if (isPressed) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(outerRadius)
            )
            .pointerInput(index) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPressChange(true)
                        val released = tryAwaitRelease()
                        isPressed = false
                        onPressChange(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Key $index",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.5.dp)
                    .clip(RoundedCornerShape(innerRadius))
            )
        } else {
            // Refined empty slot with muted slate-blue index
            Text(
                text = "${index + 1}",
                color = Color(0x665B7898),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
