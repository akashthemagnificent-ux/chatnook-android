package com.chatnook.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Discord-inspired palette. Kept flat on purpose: every value is a token the
// Nitro theme engine will later override per-user.
object ChatNookColors {
    val Blurple = Color(0xFF5865F2)
    val BlurpleDark = Color(0xFF4752C4)
    val Pink = Color(0xFFEB459E)
    val Green = Color(0xFF3BA55D)
    val Yellow = Color(0xFFFAA61A)
    val Red = Color(0xFFED4245)

    // Dark surfaces, darkest to lightest (Discord's own ordering).
    val DarkBase = Color(0xFF1E1F22)
    val DarkSurface = Color(0xFF2B2D31)
    val DarkElevated = Color(0xFF313338)
    val DarkHover = Color(0xFF3F4147)

    val TextPrimary = Color(0xFFF2F3F5)
    val TextSecondary = Color(0xFFB5BAC1)
    val TextMuted = Color(0xFF949BA4)

    val Outline = Color(0xFF4E5058)

    val LightBase = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFF2F3F5)
    val LightElevated = Color(0xFFFFFFFF)
    val LightTextPrimary = Color(0xFF060607)
    val LightTextSecondary = Color(0xFF4E5058)
    val LightOutline = Color(0xFFDBDDE1)

    // Discord login gradient — used as the auth screen background.
    val ChatNookGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF5865F2), // Blurple
            Color(0xFF4752C4), // BlurpleDark
            Color(0xFF363F6E), // deeper
        ),
        startY = 0f,
        endY = Float.POSITIVE_INFINITY,
    )
}
