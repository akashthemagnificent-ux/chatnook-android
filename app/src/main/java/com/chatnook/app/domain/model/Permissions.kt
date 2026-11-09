package com.chatnook.app.domain.model

/**
 * Discord-style permission bits. The numeric values are stored in `roles.permissions`
 * and in the `my_permissions()` RPC, so they must never be renumbered — add new bits at
 * the end and bump [ALL] accordingly.
 */
object PermissionBits {
    const val VIEW_CHANNEL = 1L shl 0
    const val SEND_MESSAGES = 1L shl 1
    const val MANAGE_MESSAGES = 1L shl 2
    const val MANAGE_CHANNELS = 1L shl 3
    const val MANAGE_ROLES = 1L shl 4
    const val CREATE_INVITE = 1L shl 5
    const val KICK_MEMBERS = 1L shl 6
    const val ADMINISTRATOR = 1L shl 7
    const val MENTION_EVERYONE = 1L shl 8
    const val ATTACH_FILES = 1L shl 9
    const val ADD_REACTIONS = 1L shl 10
    const val MANAGE_GUILD = 1L shl 11

    /** Every bit defined above; administrator resolves to this. */
    const val ALL = (1L shl 12) - 1
}

/** A role as the UI needs it; `isEveryone` roles are implicit and cannot be assigned. */
data class GuildRole(
    val id: String,
    val name: String,
    val color: Int?,
    val position: Int,
    val permissions: Long,
    val isEveryone: Boolean,
    /** How many members hold this role, from the PostgREST count embed. */
    val memberCount: Int = 0,
) {
    val isAdmin: Boolean get() = permissions has PermissionBits.ADMINISTRATOR

    companion object {
        fun fromJson(json: kotlinx.serialization.json.JsonObject): GuildRole? {
            val id = json.str("id") ?: return null
            return GuildRole(
                id = id,
                name = json.str("name").orEmpty(),
                color = json.int("color").takeIf { it != 0 },
                position = json.int("position"),
                permissions = json.long("permissions"),
                isEveryone = json.boolean("is_everyone") ?: false,
                memberCount = (json["member_roles"] as? kotlinx.serialization.json.JsonArray)
                    ?.firstOrNull()
                    ?.let { (it as? kotlinx.serialization.json.JsonObject)?.int("count") }
                    ?: 0,
            )
        }
    }
}

/**
 * Discord's resolution order, without channel overwrites (none exist yet): the owner
 * bypasses everything, otherwise the union of the member's roles decides, and the
 * administrator bit short-circuits to every permission.
 */
fun resolvePermissions(
    isOwner: Boolean,
    everyone: GuildRole?,
    roles: List<GuildRole>,
): Long {
    if (isOwner) return PermissionBits.ALL
    var bits = everyone?.permissions ?: 0L
    for (role in roles) {
        if (role.isEveryone) continue
        bits = bits or role.permissions
    }
    return if (bits has PermissionBits.ADMINISTRATOR) PermissionBits.ALL else bits
}

/** Reads better than `bits and bit != 0L` at every call site. */
infix fun Long.has(bit: Long): Boolean = (this and bit) != 0L

/** Used by the role editor to flip a single switch without disturbing the others. */
fun Long.withPermission(bit: Long, enabled: Boolean): Long =
    if (enabled) this or bit else this and bit.inv()
