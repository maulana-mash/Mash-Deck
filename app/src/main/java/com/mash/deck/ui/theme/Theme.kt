package com.mash.deck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 1. Primitive Tokens
val CanvasDeep = Color(0xFF090A0E)
val Zinc950 = Color(0xFF0B0C11)
val Zinc900 = Color(0xFF12141C)
val Zinc850 = Color(0xFF171A24)
val Zinc800 = Color(0xFF1E2230)
val Zinc700 = Color(0xFF2E344A)
val Zinc600 = Color(0xFF4B556D)
val Zinc400 = Color(0xFF94A3B8)
val Zinc200 = Color(0xFFE2E8F0)
val Zinc50 = Color(0xFFF8FAFC)

val ElectricBlue = Color(0xFF2563EB)
val VividBlue = Color(0xFF3B82F6)
val SoftBlue = Color(0xFF60A5FA)
val EmeraldGreen = Color(0xFF10B981)
val AmberWarning = Color(0xFFF59E0B)
val CrimsonRed = Color(0xFFEF4444)

// 2. Semantic Design Tokens
val BackgroundDark = CanvasDeep
val SurfaceDark = Zinc900
val SurfaceCard = Zinc850
val SurfaceCardHover = Zinc800
val SurfaceInput = Color(0xFF0D0F15)
val SurfaceVariantDark = SurfaceCard

val BorderSubtle = Color(0x1AFFFFFF) // 10% white border
val BorderMedium = Color(0x33FFFFFF) // 20% white border
val BorderFocus = VividBlue
val BorderDark = BorderSubtle

val AccentBlue = ElectricBlue
val AccentBlueLight = VividBlue
val AccentGreen = EmeraldGreen
val AccentYellow = AmberWarning
val AccentRed = CrimsonRed

val TextPrimary = Zinc50
val TextSecondary = Zinc400
val TextMuted = Zinc600

// 3. Layout Radii Tokens
val RadiusXs = 6.dp
val RadiusSm = 8.dp
val RadiusMd = 12.dp
val RadiusLg = 16.dp
val RadiusXl = 22.dp
val RadiusPill = 999.dp

// 4. Material 3 Color Scheme
private val DarkColorScheme = darkColorScheme(
    primary = VividBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0x262563EB),
    onPrimaryContainer = SoftBlue,
    secondary = Zinc400,
    background = CanvasDeep,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderMedium,
    error = CrimsonRed,
    onError = Color.White
)

// 5. Typography Scale
val MashDeckTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
        color = TextMuted
    )
)

@Composable
fun MashDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MashDeckTypography,
        content = content
    )
}
