package com.chatnook.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dark is the default: the whole design is built dark-first. */
enum class ThemeMode { Dark, Light, System }

/**
 * The few settings that belong to this install rather than the account.
 *
 * SharedPreferences rather than DataStore: one string does not justify a new dependency,
 * and the file is read once at startup.
 */
object AppSettings {
    private const val FILE = "settings"
    private const val KEY_THEME = "theme_mode"

    private var prefs: SharedPreferences? = null

    private val _themeMode = MutableStateFlow(ThemeMode.Dark)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = stored
        val saved = stored.getString(KEY_THEME, ThemeMode.Dark.name)
        _themeMode.value = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.Dark
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME, mode.name)?.apply()
    }
}
