package com.chatnook.app.domain.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Domain models mapped from the Supabase tables in
 * `supabase/migrations/0001_discord_parity.sql`.
 *
 * Parsing is hand-written against [JsonObject] instead of @Serializable DTOs so the
 * build does not need the kotlinx-serialization compiler plugin.
 */

/** ISO-8601 timestamptz -> epoch millis; 0 when the column is absent. */
internal fun JsonObject.millis(key: String): Long =
    this[key]?.jsonPrimitive?.contentOrNull
        ?.let { runCatching { kotlin.time.Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
        ?: 0L

internal fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.int(key: String): Int =
    this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

internal fun JsonObject.long(key: String): Long =
    this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

internal fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

internal fun JsonObject.strList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

/** One emoji on a message plus everyone who picked it. */
data class Reaction(val emoji: String, val userIds: List<String>) {
    companion object {
        /** Groups the flat `reactions` rows (message_id,user_id,emoji) embed into chips. */
        fun group(rows: JsonArray): List<Reaction> =
            rows.mapNotNull { it as? JsonObject }
                .mapNotNull { row ->
                    val emoji = row.str("emoji") ?: return@mapNotNull null
                    emoji to row.str("user_id").orEmpty()
                }
                .groupBy({ it.first }, { it.second })
                .map { (emoji, users) -> Reaction(emoji, users) }
    }
}

data class ChatMessage(
    val messageId: String,
    val channelId: String,
    val senderId: String,
    val content: String,
    val messageType: MessageType,
    val createdAt: Long,
    /** Raw server timestamp; keyset paging needs the exact string, not millis. */
    val createdAtIso: String? = null,
    val editedAt: Long? = null,
    val replyToId: String? = null,
    val senderName: String? = null,
    val senderAvatarUrl: String? = null,
    val reactions: List<Reaction> = emptyList(),
    /** Set when this message quotes another one; lets the chip render without a second query. */
    val replyToContent: String? = null,
    val replyToAuthorId: String? = null,
    val attachments: List<Attachment> = emptyList(),
    /** Local file shown while an upload is in flight, before the server URL exists. */
    val localImageUri: String? = null,
    /** Local delivery state; always [SendState.Sent] for rows that came from the server. */
    val sendState: SendState = SendState.Sent,
) {
    enum class MessageType { text, image, system, notification }

    enum class SendState { Pending, Sent, Failed }

    // A pending image has neither text nor a server attachment yet; it is not deleted.
    val isDeleted: Boolean
        get() = content.isEmpty() && attachments.isEmpty() && localImageUri == null &&
            messageType != MessageType.system

    companion object {
        fun fromJson(json: JsonObject): ChatMessage {
            val author = json["author"] as? JsonObject
            val reply = json["reply"] as? JsonObject
            return ChatMessage(
                messageId = json.str("id").orEmpty(),
                channelId = json.str("channel_id").orEmpty(),
                senderId = json.str("author_id").orEmpty(),
                content = json.str("content").orEmpty(),
                messageType = when (json.str("type")) {
                    "system" -> MessageType.system
                    else -> MessageType.text
                },
                createdAt = json.millis("created_at"),
                createdAtIso = json.str("created_at"),
                editedAt = json.str("edited_at")?.let { json.millis("edited_at") },
                replyToId = json.str("reply_to_id"),
                senderName = author?.str("display_name")?.takeIf { it.isNotBlank() }
                    ?: author?.str("username"),
                senderAvatarUrl = author?.str("avatar_url"),
                reactions = (json["reactions"] as? JsonArray)?.let(Reaction::group).orEmpty(),
                attachments = (json["attachments"] as? JsonArray)
                    ?.mapNotNull { it as? JsonObject }
                    ?.map(Attachment::fromJson)
                    .orEmpty(),
                replyToContent = reply?.str("content"),
                replyToAuthorId = reply?.str("author_id"),
            )
        }
    }
}

/** A locally prepared image waiting to be uploaded with a message. */
class OutgoingImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val filename: String,
) {
    val contentType: String = "image/jpeg"
    val extension: String = "jpg"
}

/** One image attached to a message. Dimensions are display hints, not guarantees. */
data class Attachment(
    val id: String,
    val url: String,
    val filename: String? = null,
    val contentType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
) {
    /** Height/width clamped to a sane range so a bad row cannot break the layout. */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) {
            (height.toFloat() / width).coerceIn(0.25f, 3f)
        } else {
            0.75f
        }

    companion object {
        fun fromJson(json: JsonObject) = Attachment(
            id = json.str("id").orEmpty(),
            url = json.str("url").orEmpty(),
            filename = json.str("filename"),
            contentType = json.str("content_type"),
            width = json.int("width"),
            height = json.int("height"),
        )
    }
}

/**
 * Nitro profile theme. Stops are hex strings so the server stores plain data and the
 * client decides how to render them; anything unparsable falls back to the flat accent.
 */
data class ProfileTheme(
    val preset: String? = null,
    val stops: List<String> = emptyList(),
    /** Optional overlay drawn above the gradient: [TEXTURES] or null. */
    val texture: String? = null,
) {
    val isGradient: Boolean get() = stops.size >= 2

    /** A hand-built theme, as opposed to one of the named presets. */
    val isCustom: Boolean get() = preset == null && isGradient

    companion object {
        val None = ProfileTheme()

        /** Textures the client knows how to draw; unknown values are dropped on read. */
        val TEXTURES = listOf("dots", "grid", "diagonal")

        /** A custom theme needs two or three readable stops, like Discord's editor. */
        const val MIN_STOPS = 2
        const val MAX_STOPS = 3

        fun fromJson(json: JsonObject?): ProfileTheme {
            if (json == null) return None
            return ProfileTheme(
                preset = json.str("preset"),
                stops = json.strList("stops").filter { HexColor.isValid(it) }.take(5),
                texture = json.str("texture")?.takeIf { it in TEXTURES },
            )
        }
    }
}

/** Replaces one stop without disturbing the others; used by the theme editor. */
fun ProfileTheme.withStop(index: Int, hex: String): ProfileTheme {
    if (!HexColor.isValid(hex) || index !in stops.indices) return this
    return copy(stops = stops.toMutableList().also { it[index] = hex }, preset = null)
}

/** Adds a stop when there is room, otherwise replaces the last one. */
fun ProfileTheme.withStopAdded(hex: String): ProfileTheme {
    if (!HexColor.isValid(hex)) return this
    val next = when {
        stops.size < ProfileTheme.MAX_STOPS -> stops + hex
        stops.isEmpty() -> listOf(hex)
        else -> stops.dropLast(1) + hex
    }
    return copy(stops = next, preset = null)
}

/** Never leaves fewer than [ProfileTheme.MIN_STOPS] stops; the editor hides it there. */
fun ProfileTheme.withoutStop(index: Int): ProfileTheme {
    if (index !in stops.indices || stops.size <= ProfileTheme.MIN_STOPS) return this
    return copy(stops = stops.filterIndexed { i, _ -> i != index }, preset = null)
}

/** Parses `#RRGGBB` / `#AARRGGBB` into an ARGB int; null when malformed. */
object HexColor {
    fun isValid(value: String): Boolean = parse(value) != null

    fun parse(value: String): Long? {
        val hex = value.trim().removePrefix("#")
        if (hex.length != 6 && hex.length != 8) return null
        if (hex.any { it.digitToIntOrNull(16) == null }) return null
        val rgb = hex.takeLast(6).toLong(16)
        val alpha = if (hex.length == 8) hex.take(2).toLong(16) shl 24 else 0xFF000000L
        return alpha or rgb
    }
}

data class Profile(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val accentColor: String?,
    val customStatus: String?,
    val badges: List<String>,
    val avatarDecorationUrl: String?,
    val avatarIsAnimated: Boolean,
    val theme: ProfileTheme,
) {
    val authorLabel: String get() = displayName.ifBlank { username }

    companion object {
        fun fromJson(json: JsonObject): Profile = Profile(
            userId = json.str("id").orEmpty(),
            username = json.str("username").orEmpty(),
            displayName = json.str("display_name").orEmpty(),
            avatarUrl = json.str("avatar_url"),
            bannerUrl = json.str("banner_url"),
            accentColor = json.str("accent_color")?.takeIf { HexColor.isValid(it) },
            customStatus = json.str("custom_status"),
            badges = json.strList("badges"),
            avatarDecorationUrl = json.str("avatar_decoration_url"),
            avatarIsAnimated = json["avatar_is_animated"]?.jsonPrimitive?.booleanOrNull ?: false,
            theme = ProfileTheme.fromJson(json["theme"] as? JsonObject),
        )
    }
}

data class Guild(
    val guildId: String,
    val name: String,
    val iconUrl: String?,
    val ownerId: String?,
) {
    companion object {
        fun fromJson(json: JsonObject): Guild = Guild(
            guildId = json.str("id").orEmpty(),
            name = json.str("name").orEmpty(),
            iconUrl = json.str("icon_url"),
            ownerId = json.str("owner_id"),
        )
    }
}

data class ChatChannel(
    val channelId: String,
    val guildId: String?,
    val name: String,
    /** Channel topic, shown as the subtitle in the list. */
    val description: String?,
    val type: String,
    /** Sort key inside the guild; reordering rewrites these as 0..n-1. */
    val position: Int = 0,
) {
    companion object {
        fun fromJson(json: JsonObject): ChatChannel = ChatChannel(
            channelId = json.str("id").orEmpty(),
            guildId = json.str("guild_id"),
            name = json.str("name").orEmpty(),
            description = json.str("topic"),
            type = json.str("type") ?: "text",
            position = json.int("position"),
        )
    }
}

/** Everything the Nitro profile card needs, derived from [Profile]. */
val Profile.gradientStops: List<Long>
    get() = theme.stops.mapNotNull(HexColor::parse).takeIf { it.size >= 2 } ?: emptyList()

/**
 * Guild-uploaded emoji. Reactions store the emoji string as-is: a URL for these,
 * a unicode glyph for stock ones, so a chip can tell them apart without a join.
 */
data class CustomEmoji(val emojiId: String, val name: String, val url: String) {
    /** Stable key used as the `emoji` value on reaction rows. */
    val code: String get() = url

    companion object {
        fun fromJson(json: JsonObject): CustomEmoji = CustomEmoji(
            emojiId = json.str("id").orEmpty(),
            name = json.str("name").orEmpty(),
            url = json.str("url").orEmpty(),
        )
    }
}

/** True when a reaction value is an uploaded emoji rather than a unicode glyph. */
val String.isCustomEmoji: Boolean get() = startsWith("http")
