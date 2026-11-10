package com.chatnook.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * One place for durations so transitions feel like one app.
 *
 * Keep it restrained: navigation is a fade, not a slide, because a chat app is used
 * dozens of times an hour and long animations become a tax.
 */
object Motion {
    /** Anything leaving the screen. */
    const val FAST = 120

    /** Anything arriving. */
    const val STANDARD = 220

    val enter = tween<Float>(durationMillis = STANDARD, easing = FastOutSlowInEasing)
    val exit = tween<Float>(durationMillis = FAST, easing = FastOutSlowInEasing)
}
