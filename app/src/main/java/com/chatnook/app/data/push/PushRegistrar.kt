package com.chatnook.app.data.push

import com.chatnook.app.data.Supabase
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Keeps this device's FCM token attached to the signed-in user. Called after login and
 * on every cold start, because a token can be rotated while the app is not running.
 */
object PushRegistrar {

    suspend fun sync() {
        val client = Supabase.clientOrNull() ?: return
        val userId = client.auth.currentUserOrNull()?.id ?: return
        val token = withContext(Dispatchers.IO) {
            runCatching { Tasks.await(FirebaseMessaging.getInstance().token) }.getOrNull()
        } ?: return
        save(client, userId, token)
    }

    /** Used by the service when Firebase rotates the token in the background. */
    suspend fun save(userId: String, token: String) {
        val client = Supabase.clientOrNull() ?: return
        save(client, userId, token)
    }

    private suspend fun save(
        client: io.github.jan.supabase.SupabaseClient,
        userId: String,
        token: String,
    ) {
        runCatching {
            client.from("push_tokens").upsert(
                buildJsonObject {
                    put("user_id", userId)
                    put("token", token)
                    put("platform", "android")
                    put("updated_at", kotlin.time.Instant.fromEpochMilliseconds(System.currentTimeMillis()).toString())
                },
            ) { onConflict = "user_id,token" }
        }
    }
}
