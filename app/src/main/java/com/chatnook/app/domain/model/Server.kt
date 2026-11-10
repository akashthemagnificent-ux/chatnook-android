package com.chatnook.app.domain.model

import kotlinx.serialization.json.JsonObject

/** An invite code for a guild, as listed in server settings. */
data class Invite(
    val code: String,
    val uses: Int,
    val maxUses: Int?,
    val createdAt: Long,
    val expiresAt: Long?,
) {
    /** Expired or fully used: redeeming it can only fail, so the UI greys it out. */
    val isDead: Boolean
        get() = (expiresAt != null && expiresAt <= System.currentTimeMillis()) ||
            (maxUses != null && uses >= maxUses)

    companion object {
        fun fromJson(json: JsonObject): Invite? {
            val code = json.str("code") ?: return null
            return Invite(
                code = code,
                uses = json.int("uses"),
                maxUses = json.int("max_uses").takeIf { json.str("max_uses") != null },
                createdAt = json.millis("created_at"),
                expiresAt = json.str("expires_at")?.let { iso ->
                    runCatching { kotlin.time.Instant.parse(iso).toEpochMilliseconds() }.getOrNull()
                },
            )
        }
    }
}

/**
 * The new channel order after moving [channelId] by [delta] places, or null when the move
 * would fall off either end. Positions are rewritten as 0..n-1 by `reorder_channels`.
 */
fun movedOrder(ids: List<String>, channelId: String, delta: Int): List<String>? {
    val from = ids.indexOf(channelId)
    val to = from + delta
    if (from < 0 || to !in ids.indices) return null
    return ids.toMutableList().apply { add(to, removeAt(from)) }
}

/** A banned account, as the moderation screen lists it. */
data class BannedUser(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val reason: String?,
    val bannedAt: Long,
) {
    companion object {
        fun fromJson(json: JsonObject): BannedUser? {
            val userId = json.str("user_id") ?: return null
            val profile = json["profile"] as? JsonObject
            return BannedUser(
                userId = userId,
                name = profile?.str("display_name")?.takeIf { it.isNotBlank() }
                    ?: profile?.str("username")
                    ?: userId.take(8),
                avatarUrl = profile?.str("avatar_url"),
                reason = json.str("reason"),
                bannedAt = json.millis("created_at"),
            )
        }
    }
}

/** One row of the server's moderation history. */
data class AuditEntry(
    val id: Long,
    val action: String,
    val actorName: String?,
    val targetName: String?,
    val detail: String?,
    val createdAt: Long,
) {
    companion object {
        private fun name(json: JsonObject?): String? = json?.let {
            it.str("display_name")?.takeIf { d -> d.isNotBlank() } ?: it.str("username")
        }

        fun fromJson(json: JsonObject): AuditEntry? {
            val id = json.long("id")
            val action = json.str("action") ?: return null
            return AuditEntry(
                id = id,
                action = action,
                actorName = name(json["actor"] as? JsonObject),
                targetName = name(json["target"] as? JsonObject),
                detail = json.str("detail"),
                createdAt = json.millis("created_at"),
            )
        }
    }
}
