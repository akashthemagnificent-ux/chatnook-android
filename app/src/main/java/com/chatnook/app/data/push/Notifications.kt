package com.chatnook.app.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chatnook.app.MainActivity
import com.chatnook.app.R

/** The channel currently on screen, so a push for it is redundant. */
object ActiveChat {
    @Volatile
    var channelId: String? = null
}

/** A tap on a push that still has to be routed once the UI is ready. */
object PendingChat {
    data class Route(val channelId: String, val channelName: String, val isDm: Boolean)

    private val _route = kotlinx.coroutines.flow.MutableStateFlow<Route?>(null)
    val route: kotlinx.coroutines.flow.StateFlow<Route?> = _route

    fun set(route: Route) {
        _route.value = route
    }

    fun consume() {
        _route.value = null
    }
}

object Notifications {
    private const val CHANNEL_ID = "messages"

    /** Safe to call on every start; creating an existing channel is a no-op. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notifications_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notifications_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun show(context: Context, channelId: String, channelName: String, title: String, body: String, isDm: Boolean) {
        // Android 13+ can deny this; a missing notification is better than a crash.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("channel_id", channelId)
            putExtra("channel_name", channelName)
            putExtra("dm", isDm)
        }
        val pending = PendingIntent.getActivity(
            context,
            channelId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(channelId.hashCode(), notification)
        }
    }
}
