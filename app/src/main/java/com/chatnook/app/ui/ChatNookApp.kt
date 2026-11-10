package com.chatnook.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chatnook.app.BuildConfig
import com.chatnook.app.R
import com.chatnook.app.data.diagnostics.CrashLog
import com.chatnook.app.data.push.PendingChat
import com.chatnook.app.data.settings.AppSettings
import com.chatnook.app.data.update.Update
import com.chatnook.app.data.update.UpdateCheck
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
import com.chatnook.app.ui.theme.Motion
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
        Box(modifier = Modifier.fillMaxSize()) {
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
        CrashReport()
        }
    }
}

/** Offers to share the trace of the previous run's crash, once. */
@Composable
private fun CrashReport() {
    val context = LocalContext.current
    var trace by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { trace = CrashLog.take(context) }
    val report = trace ?: return
    AlertDialog(
        onDismissRequest = { trace = null },
        title = { Text(stringResource(R.string.crash_title)) },
        text = { Text(stringResource(R.string.crash_body)) },
        confirmButton = {
            TextButton(
                onClick = {
                    trace = null
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "ChatNook crash report")
                                    putExtra(Intent.EXTRA_TEXT, report)
                                },
                                null,
                            ),
                        )
                    }
                },
            ) { Text(stringResource(R.string.crash_share)) }
        },
        dismissButton = {
            TextButton(onClick = { trace = null }) {
                Text(stringResource(R.string.crash_dismiss))
            }
        },
    )
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

    // One check per launch; a failure is silent and the app just runs.
    val context = LocalContext.current
    var update by remember { mutableStateOf<Update?>(null) }
    LaunchedEffect(Unit) {
        val found = UpdateCheck.check(BuildConfig.VERSION_NAME) ?: return@LaunchedEffect
        if (found.version != AppSettings.dismissedUpdate()) update = found
    }
    update?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                AppSettings.dismissUpdate(pending.version)
                update = null
            },
            title = { Text(stringResource(R.string.update_title)) },
            text = { Text(stringResource(R.string.update_body, pending.version)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        AppSettings.dismissUpdate(pending.version)
                        update = null
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(pending.pageUrl)),
                            )
                        }
                    },
                ) { Text(stringResource(R.string.update_now)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        AppSettings.dismissUpdate(pending.version)
                        update = null
                    },
                ) { Text(stringResource(R.string.update_later)) }
            },
        )
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_CHANNELS,
        enterTransition = { fadeIn(Motion.enter) },
        exitTransition = { fadeOut(Motion.exit) },
        popEnterTransition = { fadeIn(Motion.enter) },
        popExitTransition = { fadeOut(Motion.exit) },
    ) {
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
