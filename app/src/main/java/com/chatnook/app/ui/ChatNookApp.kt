package com.chatnook.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chatnook.app.ui.auth.AuthScreen
import com.chatnook.app.ui.auth.AuthViewModel
import com.chatnook.app.ui.chat.ChannelListScreen
import com.chatnook.app.ui.chat.ChatScreen
import com.chatnook.app.ui.chat.ChatViewModel
import com.chatnook.app.ui.chat.ProfileScreen
import com.chatnook.app.ui.theme.ChatNookTheme
import io.github.jan.supabase.auth.status.SessionStatus

private const val ROUTE_CHANNELS = "channels"
private const val ROUTE_CHAT = "chat/{channelId}/{channelName}"
private const val ROUTE_PROFILE = "profile"

@Composable
fun ChatNookApp() {
    val authViewModel: AuthViewModel = viewModel()
    val sessionStatus by authViewModel.sessionStatus.collectAsStateWithLifecycle()

    ChatNookTheme {
        // Auth state drives navigation; there are no manual nav flags to keep in sync.
        when (sessionStatus) {
            is SessionStatus.Authenticated -> SignedInRoutes(
                authViewModel = authViewModel,
            )

            // Restoring a stored session: showing the auth form here would flash
            // the login screen on every cold start for signed-in users.
            is SessionStatus.Initializing -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> AuthScreen(viewModel = authViewModel)
        }
    }
}

@Composable
private fun SignedInRoutes(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    // One ChatViewModel for the whole signed-in session, so channel caches and the
    // live Realtime subscriptions survive navigating between screens.
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(navController = navController, startDestination = ROUTE_CHANNELS) {
        composable(ROUTE_CHANNELS) {
            ChannelListScreen(
                viewModel = chatViewModel,
                onOpenChannel = { channelId, channelName ->
                    navController.navigate("chat/$channelId/$channelName")
                },
                onOpenProfile = { navController.navigate(ROUTE_PROFILE) },
            )
        }
        composable(ROUTE_CHAT) { entry ->
            val channelId = entry.arguments?.getString("channelId").orEmpty()
            val channelName = entry.arguments?.getString("channelName").orEmpty()
            ChatScreen(
                viewModel = chatViewModel,
                channelId = channelId,
                channelName = channelName,
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_PROFILE) {
            ProfileScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onSignOut = authViewModel::signOut,
            )
        }
    }
}
