package com.chatnook.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatnook.app.data.settings.AppSettings
import com.chatnook.app.data.settings.ThemeMode

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

/** Dark unless the person picked otherwise in Profile → Appearance. */
@Composable
fun ChatNookTheme(content: @Composable () -> Unit) {
    val mode by AppSettings.themeMode.collectAsStateWithLifecycle()
    val dark = when (mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) ChatNookDarkScheme else ChatNookLightScheme,
        typography = ChatNookTypography,
        content = content,
    )
}
