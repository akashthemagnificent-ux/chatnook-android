package com.chatnook.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chatnook.app.ui.home.HomeScreen
import com.chatnook.app.ui.theme.ChatNookTheme

private object Routes {
    const val HOME = "home"
}

@Composable
fun ChatNookApp() {
    ChatNookTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen()
            }
        }
    }
}
