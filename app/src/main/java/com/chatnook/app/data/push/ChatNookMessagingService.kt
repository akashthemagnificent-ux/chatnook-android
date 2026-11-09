package com.chatnook.app.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Data-only pushes: the server sends `data`, and the notification is built here so the
 * tap target and the suppression rule stay under the app's control.
 */
class ChatNookMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val userId = com.chatnook.app.data.Supabase.clientOrNull()
            ?.auth?.currentUserOrNull()?.id ?: return
        CoroutineScope(Dispatchers.IO).launch { PushRegistrar.save(userId, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val channelId = data["channel_id"] ?: return
        // Reading that channel right now: the message is already on screen.
        if (ActiveChat.channelId == channelId) return

        Notifications.show(
            context = this,
            channelId = channelId,
            channelName = data["channel_name"].orEmpty(),
            title = data["title"].orEmpty(),
            body = data["body"].orEmpty(),
            isDm = data["dm"] == "true",
        )
    }
}
