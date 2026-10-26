package com.chatnook.app.data.repository

import com.chatnook.app.data.Supabase
import com.chatnook.app.domain.model.ChatChannel
import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.model.CustomEmoji
import com.chatnook.app.domain.model.Guild
import com.chatnook.app.domain.model.Profile
import com.chatnook.app.domain.repository.ChatRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.PostgresChangeFilter
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Supabase-backed [ChatRepository].
 *
 * Message flows fetch one page, then stay live through Realtime INSERT/UPDATE/DELETE on
 * that channel. The subscription lives as long as the flow has a collector, so switching
 * channels releases the old socket binding automatically.
 */
class SupabaseChatRepository : ChatRepository {

    private companion object {
        /** Discord-ish page size; enough to fill a phone screen twice. */
        const val PAGE_SIZE = 50L

        const val MESSAGE_COLUMNS =
            "id,channel_id,author_id,content,type,reply_to_id,created_at,edited_at," +
                "author:profiles(id,username,display_name,avatar_url)," +
                "reply:messages(content,author_id)," +
                "reactions(emoji,user_id)"

        const val PROFILE_COLUMNS =
            "id,username,display_name,avatar_url,banner_url,accent_color,theme," +
                "custom_status,badges,avatar_decoration_url,avatar_is_animated"
    }

    private fun userId(): String =
        Supabase.clientOrNull()?.auth?.currentUserOrNull()?.id
            ?: error("Not signed in")

    // ------------------------------------------------------------------ guilds

    override fun getGuilds(): Flow<List<Guild>> = flow {
        val client = Supabase.clientOrNull() ?: return@flow

        suspend fun load(): List<Guild> = client.from("guild_members")
            .select(Columns.raw("guild:guilds(id,name,icon_url,owner_id)")) {
                filter { eq("user_id", userId()) }
            }
            .decodeList<JsonObject>()
            .mapNotNull { (it["guild"] as? JsonObject)?.let(Guild::fromJson) }

        // A failed load must not kill the screen: an unapplied migration or a dropped
        // connection shows an empty state, not a crash.
        emit(runCatching { load() }.getOrDefault(emptyList()))

        // Membership rows carry no payload we can patch locally, so re-read the
        // whole (tiny) list whenever it changes.
        val channel = client.channel("guild-members-${userId()}")
        val events = channel.postgresChangeFlow<PostgresAction>("public") {
            table = "guild_members"
            filter("user_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, userId())
        }
        channel.subscribe()
        try {
            events.collect { emit(runCatching { load() }.getOrDefault(emptyList())) }
        } finally {
            runCatching { channel.unsubscribe() }
        }
    }

    override suspend fun createGuild(name: String): Guild {
        val client = Supabase.clientOrNull() ?: error("Supabase not configured")
        // RPC, not INSERT: guilds_read_member is false until the owner trigger runs,
        // so an insert with RETURNING would fail its own read policy.
        val id = client.postgrest
            .rpc("create_guild", buildJsonObject { put("guild_name", name.trim()) })
            .data.trim('"')

        return Guild(
            guildId = id,
            name = name.trim(),
            iconUrl = null,
            ownerId = userId(),
        )
    }

    override suspend fun createChannel(
        guildId: String,
        name: String,
        topic: String?,
    ): ChatChannel {
        val client = Supabase.clientOrNull() ?: error("Supabase not configured")
        val nextPosition = runCatching {
            client.from("channels")
                .select(Columns.raw("position")) { filter { eq("guild_id", guildId) } }
                .decodeList<JsonObject>()
                .size
        }.getOrDefault(0)

        return client.from("channels").insert(
            buildJsonObject {
                put("guild_id", guildId)
                put("name", name.trim())
                put("type", "text")
                put("position", nextPosition)
                topic?.takeIf { it.isNotBlank() }?.let { put("topic", it.trim()) }
            },
        ) { select() }.decodeSingle<JsonObject>().let(ChatChannel::fromJson)
    }

    override suspend fun createInvite(guildId: String): String {
        val client = Supabase.clientOrNull() ?: error("Supabase not configured")
        return client.postgrest
            .rpc("create_invite", buildJsonObject { put("gid", guildId) })
            .data.trim('"')
            .uppercase()
    }

    override suspend fun joinGuild(code: String): String {
        val client = Supabase.clientOrNull() ?: error("Supabase not configured")
        return client.postgrest
            .rpc("join_guild", buildJsonObject { put("invite_code", code.trim().uppercase()) })
            .data.trim('"')
    }

    // ---------------------------------------------------------------- channels

    override fun getChannels(guildId: String): Flow<List<ChatChannel>> = flow {
        val client = Supabase.clientOrNull() ?: return@flow
        suspend fun load(): List<ChatChannel> = client.from("channels")
            .select(Columns.raw("id,guild_id,name,topic,type,position")) {
                filter { eq("guild_id", guildId) }
                order("position", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
            .map(ChatChannel::fromJson)
            .filter { it.type == "text" }

        emit(runCatching { load() }.getOrDefault(emptyList()))

        val channel = client.channel("channels-$guildId")
        val events = channel.postgresChangeFlow<PostgresAction>("public") {
            table = "channels"
            filter("guild_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, guildId)
        }
        channel.subscribe()
        try {
            events.collect { emit(runCatching { load() }.getOrDefault(emptyList())) }
        } finally {
            runCatching { channel.unsubscribe() }
        }
    }

    // ---------------------------------------------------------------- messages

    private suspend fun loadMessages(channelId: String): List<ChatMessage> {
        val client = Supabase.clientOrNull() ?: return emptyList()
        return client.from("messages")
            .select(Columns.raw(MESSAGE_COLUMNS)) {
                filter { eq("channel_id", channelId) }
                order("created_at", Order.DESCENDING)
                limit(PAGE_SIZE)
            }
            .decodeList<JsonObject>()
            .map(ChatMessage::fromJson)
            .sortedBy { it.createdAt }
    }

    override fun getChannelMessages(channelId: String): Flow<List<ChatMessage>> = channelFlow {
        val client = Supabase.clientOrNull() ?: return@channelFlow

        val cache = LinkedHashMap<String, ChatMessage>()
        fun snapshot() = cache.values.toList()

        runCatching { loadMessages(channelId) }.getOrNull()
            ?.forEach { cache[it.messageId] = it }
        send(snapshot())

        val channel = client.channel("channel-$channelId")
        val messageFilter: PostgresChangeFilter.() -> Unit = {
            table = "messages"
            filter("channel_id", FilterOperator.EQ, channelId)
        }
        val events = merge(
            channel.postgresChangeFlow<PostgresAction.Insert>("public", messageFilter),
            channel.postgresChangeFlow<PostgresAction.Update>("public", messageFilter),
            channel.postgresChangeFlow<PostgresAction.Delete>("public", messageFilter),
        )
        // Reactions live on their own table and carry no channel_id, so they are
        // watched unfiltered and matched against the cache instead.
        val reactionEvents = channel.postgresChangeFlow<PostgresAction>("public") {
            table = "reactions"
        }

        // Realtime rows carry no embeds, so one Postgrest round trip per changed
        // message is what brings the author profile and reaction chips along.
        suspend fun refresh(id: String) {
            val fresh = runCatching {
                client.from("messages")
                    .select(Columns.raw(MESSAGE_COLUMNS)) { filter { eq("id", id) } }
                    .decodeSingleOrNull<JsonObject>()
            }.getOrNull() ?: return
            cache[id] = ChatMessage.fromJson(fresh)
            send(snapshot())
        }

        channel.subscribe()
        try {
            coroutineScope {
                launch {
                    reactionEvents.collect { action ->
                        val row = when (action) {
                            is PostgresAction.Insert -> action.record
                            is PostgresAction.Update -> action.record
                            is PostgresAction.Delete -> action.oldRecord
                            else -> null
                        } ?: return@collect
                        val id = row["message_id"]?.toString()?.trim('"') ?: return@collect
                        if (cache.containsKey(id)) refresh(id)
                    }
                }

                events.collect { action ->
                    val id = when (action) {
                        is PostgresAction.Insert -> action.record["id"]
                        is PostgresAction.Update -> action.record["id"]
                        is PostgresAction.Delete -> action.oldRecord["id"]
                        else -> null
                    }?.toString()?.trim('"') ?: return@collect

                    when (action) {
                        is PostgresAction.Insert -> if (!cache.containsKey(id)) refresh(id)
                        is PostgresAction.Update -> if (cache.containsKey(id)) refresh(id)
                        is PostgresAction.Delete -> if (cache.remove(id) != null) send(snapshot())
                        else -> Unit
                    }
                }
            }
        } finally {
            runCatching { channel.unsubscribe() }
        }
    }

    override suspend fun sendMessage(
        channelId: String,
        content: String,
        replyToId: String?,
    ): ChatMessage {
        val client = Supabase.clientOrNull() ?: error("Supabase not configured")
        val payload = buildJsonObject {
            put("channel_id", channelId)
            put("author_id", userId())
            put("content", content)
            replyToId?.let { put("reply_to_id", it) }
        }

        val row = client.from("messages")
            .insert(payload) { select() }
            .decodeSingle<JsonObject>()

        // The optimistic row has no author embed; the sender's own profile fills it in.
        val mine = runCatching { getProfile(userId()) }.getOrNull()
        return ChatMessage.fromJson(row).copy(
            senderName = mine?.authorLabel,
            senderAvatarUrl = mine?.avatarUrl,
        )
    }

    override suspend fun editMessage(messageId: String, content: String) {
        val client = Supabase.clientOrNull() ?: error("Supabase not configured")
        client.from("messages").update(
            buildJsonObject {
                put("content", content)
                put("edited_at", kotlin.time.Instant.fromEpochMilliseconds(
                    System.currentTimeMillis(),
                ).toString())
            },
        ) { filter { eq("id", messageId) } }
    }

    override suspend fun deleteMessage(messageId: String) {
        val client = Supabase.clientOrNull() ?: error("Supabase not configured")
        client.from("messages").delete { filter { eq("id", messageId) } }
    }

    override suspend fun toggleReaction(messageId: String, emoji: String) {
        val client = Supabase.clientOrNull() ?: error("Supabase not configured")
        val me = userId()
        val existing = client.from("reactions")
            .select(Columns.raw("emoji")) {
                filter {
                    eq("message_id", messageId)
                    eq("user_id", me)
                    eq("emoji", emoji)
                }
            }
            .decodeList<JsonObject>()

        if (existing.isEmpty()) {
            client.from("reactions").insert(
                buildJsonObject {
                    put("message_id", messageId)
                    put("user_id", me)
                    put("emoji", emoji)
                },
            )
        } else {
            client.from("reactions").delete {
                filter {
                    eq("message_id", messageId)
                    eq("user_id", me)
                    eq("emoji", emoji)
                }
            }
        }
    }

    // ---------------------------------------------------------------- profiles

    override fun getCustomEmojis(guildId: String): Flow<List<CustomEmoji>> = flow {
        val client = Supabase.clientOrNull() ?: return@flow
        suspend fun load(): List<CustomEmoji> = client.from("custom_emojis")
            .select(Columns.raw("id,name,url")) { filter { eq("guild_id", guildId) } }
            .decodeList<JsonObject>()
            .map(CustomEmoji::fromJson)

        // A failed load must not kill the screen (e.g. migration not applied yet).
        emit(runCatching { load() }.getOrDefault(emptyList()))

        val channel = client.channel("emojis-$guildId")
        val events = channel.postgresChangeFlow<PostgresAction>("public") {
            table = "custom_emojis"
            filter("guild_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, guildId)
        }
        channel.subscribe()
        try {
            events.collect { emit(runCatching { load() }.getOrDefault(emptyList())) }
        } finally {
            runCatching { channel.unsubscribe() }
        }
    }

    override suspend fun createCustomEmoji(
        guildId: String,
        name: String,
        url: String,
    ): CustomEmoji {
        val client = Supabase.clientOrNull() ?: error("Supabase not configured")
        // custom_emojis.name is unique per guild; lowercase keeps commands predictable.
        return client.from("custom_emojis").insert(
            buildJsonObject {
                put("guild_id", guildId)
                put("name", name.trim().lowercase())
                put("url", url)
                put("animated", url.substringBefore('?').endsWith(".gif"))
                put("created_by", userId())
            },
        ) { select() }.decodeSingle<JsonObject>().let(CustomEmoji::fromJson)
    }

    override suspend fun getProfile(userId: String): Profile? {
        val client = Supabase.clientOrNull() ?: return null
        return client.from("profiles")
            .select(Columns.raw(PROFILE_COLUMNS)) { filter { eq("id", userId) } }
            .decodeSingleOrNull<JsonObject>()
            ?.let(Profile::fromJson)
    }

    override fun getMyProfile(): Flow<Profile?> {
        val client = Supabase.clientOrNull() ?: return flowOf(null)
        return flow {
            val me = runCatching { userId() }.getOrNull() ?: return@flow
            emit(runCatching { getProfile(me) }.getOrNull())

            val channel = client.channel("profile-$me")
            val events = channel.postgresChangeFlow<PostgresAction.Update>("public") {
                table = "profiles"
                filter("id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, me)
            }
            channel.subscribe()
            try {
                events.collect { emit(runCatching { getProfile(me) }.getOrNull()) }
            } finally {
                runCatching { channel.unsubscribe() }
            }
        }
    }

    override suspend fun updateProfile(profile: Profile) {
        val client = Supabase.clientOrNull() ?: error("Supabase not configured")
        client.from("profiles").update(
            buildJsonObject {
                put("display_name", profile.displayName)
                put("avatar_url", profile.avatarUrl)
                put("banner_url", profile.bannerUrl)
                put("accent_color", profile.accentColor)
                put("custom_status", profile.customStatus)
                put("avatar_decoration_url", profile.avatarDecorationUrl)
                put("avatar_is_animated", profile.avatarIsAnimated)
                put(
                    "theme",
                    buildJsonObject {
                        profile.theme.preset?.let { put("preset", it) }
                        put(
                            "stops",
                            kotlinx.serialization.json.JsonArray(
                                profile.theme.stops.map { kotlinx.serialization.json.JsonPrimitive(it) },
                            ),
                        )
                    },
                )
                put(
                    "badges",
                    kotlinx.serialization.json.JsonArray(
                        profile.badges.map { kotlinx.serialization.json.JsonPrimitive(it) },
                    ),
                )
            },
        ) { filter { eq("id", profile.userId) } }
    }

    override suspend fun uploadImage(
        bucket: String,
        bytes: ByteArray,
        extension: String,
    ): String {
        val client = Supabase.clientOrNull() ?: error("Supabase not configured")
        // Storage policies only allow writes under the caller's own prefix.
        val path = "${userId()}/${System.currentTimeMillis()}.$extension"
        val api = client.storage.from(bucket)
        api.upload(path, bytes) {
            upsert = true
            contentType = io.ktor.http.ContentType.parse(
                when (extension.lowercase()) {
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                },
            )
        }
        return api.publicUrl(path)
    }
}
