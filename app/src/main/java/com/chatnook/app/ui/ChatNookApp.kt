package com.chatnook.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chatnook.app.ui.chat.ChannelListScreen
import com.chatnook.app.ui.chat.ChatScreen
import com.chatnook.app.ui.chat.ChatViewModel
import com.chatnook.app.ui.theme.ChatNookTheme

private const val ROUTE_CHANNELS = "channels"
private const val ROUTE_CHAT = "chat/{channelId}/{channelName}"

private fun chatRoute(channelId: String, channelName: String) =
    "chat/${Uri.encode(channelId)}/${Uri.encode(channelName)}"

@Composable
fun ChatNookApp() {
    ChatNookTheme {
        val navController = rememberNavController()
        val viewModel: ChatViewModel = viewModel()

        NavHost(navController = navController, startDestination = ROUTE_CHANNELS) {
            composable(ROUTE_CHANNELS) {
                ChannelListScreen(
                    viewModel = viewModel,
                    onOpenChannel = { id, name -> navController.navigate(chatRoute(id, name)) },
                )
            }
            composable(
                route = ROUTE_CHAT,
                arguments = listOf(
                    navArgument("channelId") { type = NavType.StringType },
                    navArgument("channelName") { type = NavType.StringType },
                ),
            ) { entry ->
                ChatScreen(
                    channelId = entry.arguments?.getString("channelId").orEmpty(),
                    channelName = entry.arguments?.getString("channelName").orEmpty(),
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel,
                )
            }
        }
    }
}
