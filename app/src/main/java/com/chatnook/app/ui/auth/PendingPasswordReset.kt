package com.chatnook.app.ui.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A recovery link signs the user in before the app can ask for a new password.
 * This carries that "ask now" flag from the deep link into the signed-in UI.
 */
object PendingPasswordReset {
    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    fun request() {
        _requested.value = true
    }

    fun consume() {
        _requested.value = false
    }
}
