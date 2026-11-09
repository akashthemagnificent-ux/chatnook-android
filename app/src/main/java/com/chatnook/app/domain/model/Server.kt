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
