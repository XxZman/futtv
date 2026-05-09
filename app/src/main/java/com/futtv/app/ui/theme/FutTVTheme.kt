package com.futtv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Fondos ───────────────────────────────────────────────────────────────────
val Background       = Color(0xFF08090F)   // negro con tinte azul
val Surface          = Color(0xFF0E1118)
val SurfaceVariant   = Color(0xFF161B27)
val CardBackground   = Color(0xFF131822)
val CardBackgroundFocused = Color(0xFF1B2438)

// ── Colores de acento ─────────────────────────────────────────────────────────
val PrimaryRed       = Color(0xFFFF1744)   // rojo brillante para LIVE / acción
val PrimaryRedDark   = Color(0xFFD50000)
val AccentBlue       = Color(0xFF2979FF)
val AccentBlueBright = Color(0xFF448AFF)
val AccentCyan       = Color(0xFF00E5FF)
val Gold             = Color(0xFFFFD740)
val LiveGreen        = Color(0xFF69F0AE)

// ── Texto ─────────────────────────────────────────────────────────────────────
val TextPrimary      = Color(0xFFFFFFFF)
val TextSecondary    = Color(0xFF8FA0B5)
val TextMuted        = Color(0xFF445064)

// ── Focus / UI ────────────────────────────────────────────────────────────────
val FocusBorder      = Color(0xFF2979FF)
val FocusGlow        = Color(0x552979FF)
val LiveBadge        = Color(0xFFFF1744)
val GlassOverlay     = Color(0x12FFFFFF)   // glassmorphism sutil
val DividerColor     = Color(0x10FFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary          = PrimaryRed,
    onPrimary        = Color.White,
    primaryContainer = PrimaryRedDark,
    secondary        = AccentBlue,
    onSecondary      = Color.White,
    background       = Background,
    onBackground     = TextPrimary,
    surface          = Surface,
    onSurface        = TextPrimary,
    surfaceVariant   = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error            = Color(0xFFCF6679)
)

@Composable
fun FutTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = FutTVTypography,
        content     = content
    )
}

// Tipografía escalada para TV (distancia ~3m)
val FutTVTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
    titleSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    bodyLarge = TextStyle(fontSize = 18.sp, color = TextSecondary),
    bodyMedium = TextStyle(fontSize = 16.sp, color = TextSecondary),
    bodySmall = TextStyle(fontSize = 14.sp, color = TextMuted),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
)
