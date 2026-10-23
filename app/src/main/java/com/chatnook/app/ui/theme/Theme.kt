package com.chatnook.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ChatNookDarkScheme = darkColorScheme(
    primary = ChatNookColors.Blurple,
    onPrimary = ChatNookColors.TextPrimary,
    primaryContainer = ChatNookColors.BlurpleDark,
    onPrimaryContainer = ChatNookColors.TextPrimary,
    secondary = ChatNookColors.Pink,
    onSecondary = ChatNookColors.TextPrimary,
    background = ChatNookColors.DarkBase,
    onBackground = ChatNookColors.TextPrimary,
    surface = ChatNookColors.DarkSurface,
    onSurface = ChatNookColors.TextPrimary,
    surfaceVariant = ChatNookColors.DarkElevated,
    onSurfaceVariant = ChatNookColors.TextSecondary,
    outline = ChatNookColors.Outline,
    error = ChatNookColors.Red,
)

private val ChatNookLightScheme = lightColorScheme(
    primary = ChatNookColors.Blurple,
    onPrimary = ChatNookColors.TextPrimary,
    secondary = ChatNookColors.Pink,
    background = ChatNookColors.LightBase,
    onBackground = ChatNookColors.LightTextPrimary,
    surface = ChatNookColors.LightElevated,
    onSurface = ChatNookColors.LightTextPrimary,
    surfaceVariant = ChatNookColors.LightSurface,
    onSurfaceVariant = ChatNookColors.LightTextSecondary,
    outline = ChatNookColors.LightOutline,
    error = ChatNookColors.Red,
)

@Composable
fun ChatNookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ChatNookDarkScheme else ChatNookLightScheme,
        typography = ChatNookTypography,
        content = content,
    )
}
