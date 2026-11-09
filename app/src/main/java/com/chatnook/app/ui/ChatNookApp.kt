package com.chatnook.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chatnook.app.data.push.PendingChat
import com.chatnook.app.data.push.PushRegistrar
import com.chatnook.app.ui.auth.AuthScreen
import com.chatnook.app.ui.auth.AuthViewModel
import com.chatnook.app.ui.chat.ChannelListScreen
import com.chatnook.app.ui.chat.ChatScreen
import com.chatnook.app.ui.chat.ChatViewModel
import com.chatnook.app.ui.chat.MembersScreen
import com.chatnook.app.ui.chat.ProfileScreen
import com.chatnook.app.ui.chat.ServerSettingsScreen
import com.chatnook.app.ui.theme.ChatNookTheme
import io.github.jan.supabase.auth.status.SessionStatus

private const val ROUTE_CHANNELS = "channels"
private const val ROUTE_CHAT = "chat/{channelId}/{channelName}?dm={dm}"
private const val ROUTE_MEMBERS = "members"
private const val ROUTE_SERVER = "server"
private const val ROUTE_PROFILE = "profile"

private fun chatRoute(channelId: String, channelName: String, isDm: Boolean = false) =
    "chat/$channelId/$channelName?dm=$isDm"

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

    // Attach this device's push token to the account, and follow a tapped notification.
    LaunchedEffect(Unit) { PushRegistrar.sync() }
    val pendingChat by PendingChat.route.collectAsStateWithLifecycle()
    LaunchedEffect(pendingChat) {
        val route = pendingChat ?: return@LaunchedEffect
        PendingChat.consume()
        navController.navigate(chatRoute(route.channelId, route.channelName, route.isDm)) {
            popUpTo(ROUTE_CHANNELS) { inclusive = false }
        }
    }

    NavHost(navController = navController, startDestination = ROUTE_CHANNELS) {
        composable(ROUTE_CHANNELS) {
            ChannelListScreen(
                viewModel = chatViewModel,
                onOpenChannel = { channelId, channelName ->
                    navController.navigate(chatRoute(channelId, channelName))
                },
                onOpenDm = { channelId, name ->
                    navController.navigate(chatRoute(channelId, name, isDm = true))
                },
                onOpenMembers = { navController.navigate(ROUTE_MEMBERS) },
                onOpenServerSettings = { navController.navigate(ROUTE_SERVER) },
                onOpenProfile = { navController.navigate(ROUTE_PROFILE) },
            )
        }
        composable(
            route = ROUTE_CHAT,
            arguments = listOf(navArgument("dm") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val channelId = entry.arguments?.getString("channelId").orEmpty()
            val channelName = entry.arguments?.getString("channelName").orEmpty()
            ChatScreen(
                viewModel = chatViewModel,
                channelId = channelId,
                channelName = channelName,
                isDm = entry.arguments?.getBoolean("dm") == true,
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_SERVER) {
            ServerSettingsScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_MEMBERS) {
            MembersScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onMessage = { channelId, name ->
                    navController.navigate(chatRoute(channelId, name, isDm = true))
                },
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
