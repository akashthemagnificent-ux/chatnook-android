package com.chatnook.app.data.realtime

import android.util.Log
import com.chatnook.app.domain.model.TypingUser
import com.chatnook.app.domain.model.str
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Typing pings for one channel.
 *
 * Broadcast, not a table: the data is worthless five seconds later, so it never touches
 * Postgres. Receivers drop an entry [TTL_MS] after the last ping, because one lost
 * packet would otherwise leave someone "typing" forever.
 */
class TypingSession(
    client: SupabaseClient,
    channelId: String,
    private val myUserId: String,
) {
    private companion object {
        const val EVENT = "typing"
        const val TTL_MS = 5_000L
        const val SWEEP_MS = 1_000L
    }

    private val realtime = client.channel("typing-$channelId") {
        // Own pings would render "you are typing" to yourself.
        broadcast { receiveOwnBroadcasts = false }
    }

    val users: Flow<List<TypingUser>> = channelFlow {
        val seen = LinkedHashMap<String, Pair<TypingUser, Long>>()
        val events = realtime.broadcastFlow<JsonObject>(EVENT)
        realtime.subscribe()
        try {
            coroutineScope {
                launch {
                    events.collect { payload ->
                        val id = payload.str("user_id") ?: return@collect
                        val name = payload.str("name") ?: return@collect
                        seen[id] = TypingUser(id, name) to System.currentTimeMillis()
                        send(seen.values.map { it.first })
                    }
                }
                launch {
                    while (true) {
                        delay(SWEEP_MS)
                        val cutoff = System.currentTimeMillis() - TTL_MS
                        if (seen.values.removeAll { it.second < cutoff }) {
                            send(seen.values.map { it.first })
                        }
                    }
                }
            }
        } finally {
            runCatching { realtime.unsubscribe() }
        }
    }

    /** Best effort: a ping that loses a socket reconnect is not worth a retry. */
    suspend fun ping(name: String) {
        try {
            realtime.broadcast(
                EVENT,
                buildJsonObject {
                    put("user_id", myUserId)
                    put("name", name)
                },
            )
        } catch (e: Exception) {
            Log.d("ChatNookTyping", "ping failed: ${e.message}")
        }
    }
}
