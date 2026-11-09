package com.chatnook.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.chatnook.app.data.Supabase
import com.chatnook.app.data.push.Notifications
import com.chatnook.app.data.push.PendingChat
import com.chatnook.app.ui.ChatNookApp
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {

    // Lint wants Fragment 1.3.0+; the only fragment on the classpath is an unused
    // 1.1.0 pulled in by play-services-basement, and this call is in an Activity.
    @SuppressLint("InvalidFragmentVersionForActivityResult")
    private val askForNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureChannel(this)
        askForNotificationsOnce()
        // Magic-link / recovery links land here as chatnook://auth?code=...
        Supabase.clientOrNull()?.handleDeeplinks(intent)
        routeFrom(intent)
        setContent { ChatNookApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Supabase.clientOrNull()?.handleDeeplinks(intent)
        routeFrom(intent)
    }

    /** A tapped push carries the channel to open; the UI navigates once it is ready. */
    private fun routeFrom(intent: Intent) {
        val channelId = intent.getStringExtra("channel_id") ?: return
        PendingChat.set(
            PendingChat.Route(
                channelId = channelId,
                channelName = intent.getStringExtra("channel_name").orEmpty(),
                isDm = intent.getBooleanExtra("dm", false),
            ),
        )
        intent.removeExtra("channel_id")
    }

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
