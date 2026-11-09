package com.chatnook.app.domain.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** One guild member, resolved to the top role that decides which group they sit in. */
data class Member(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val badges: List<String>,
    val customStatus: String?,
    val nickname: String?,
    val roleName: String?,
    /** ARGB from the role, null when the role has no colour. */
    val roleColor: Int?,
    val rolePosition: Int,
    /** Every role id the member holds; used to preselect the assignment sheet. */
    val roleIds: List<String>,
    val isOwner: Boolean,
) {
    /** What the list and the composer call them. */
    val label: String
        get() = nickname?.takeIf { it.isNotBlank() }
            ?: displayName?.takeIf { it.isNotBlank() }
            ?: username

    companion object {
        /** Null when the profile embed is missing, which only happens if RLS hides it. */
        fun fromJson(json: JsonObject): Member? {
            val profile = json["profile"] as? JsonObject ?: return null
            val roles = (json["member_roles"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.get("role") as? JsonObject }
            val topRole = roles.maxByOrNull { it.int("position") }
            val id = profile.str("id").orEmpty()
            val ownerId = (json["guild"] as? JsonObject)?.str("owner_id")
            return Member(
                userId = id,
                username = profile.str("username").orEmpty(),
                displayName = profile.str("display_name"),
                avatarUrl = profile.str("avatar_url"),
                bannerUrl = profile.str("banner_url"),
                badges = profile.strList("badges"),
                customStatus = profile.str("custom_status"),
                nickname = json.str("nickname"),
                roleName = topRole?.str("name"),
                roleColor = topRole?.int("color")?.takeIf { it != 0 },
                rolePosition = topRole?.int("position") ?: -1,
                roleIds = roles.mapNotNull { it.str("id") },
                isOwner = ownerId != null && ownerId == id,
            )
        }
    }
}

/** A section of the member list. */
data class MemberGroup(val title: String, val members: List<Member>)

/**
 * Owner first, then roles by position, then everyone without a role; alphabetical
 * inside each group so the list does not reorder on every presence change.
 */
fun groupMembers(members: List<Member>): List<MemberGroup> {
    val groups = LinkedHashMap<String, List<Member>>()
    members.filter { it.isOwner }.takeIf { it.isNotEmpty() }?.let { groups["Owner"] = it }

    members.filter { !it.isOwner && it.roleName != null }
        .groupBy { it.roleName.orEmpty() }
        .entries
        .sortedByDescending { (_, list) -> list.maxOf { it.rolePosition } }
        .forEach { (name, list) -> groups[name] = list }

    members.filter { !it.isOwner && it.roleName == null }
        .takeIf { it.isNotEmpty() }
        ?.let { groups["Members"] = it }

    return groups.map { (title, list) -> MemberGroup(title, list.sortedBy { it.label.lowercase() }) }
}

/** A direct message conversation, as shown in the home list. */
data class DmSummary(
    val channelId: String,
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val lastMessage: String?,
    val lastAt: Long,
) {
    companion object {
        fun fromJson(json: JsonObject): DmSummary? {
            val channelId = json.str("channel_id") ?: return null
            val userId = json.str("user_id") ?: return null
            return DmSummary(
                channelId = channelId,
                userId = userId,
                name = json.str("name") ?: json.str("username") ?: userId.take(8),
                avatarUrl = json.str("avatar_url"),
                lastMessage = json.str("last_message"),
                lastAt = json.millis("last_at"),
            )
        }
    }
}

/** Newest conversation first; ties broken by name so the list never jitters. */
fun sortDms(dms: List<DmSummary>): List<DmSummary> =
    dms.sortedWith(compareByDescending<DmSummary> { it.lastAt }.thenBy { it.name.lowercase() })
        // Sort first, so a duplicate channel keeps its newest row rather than its first.
        .distinctBy { it.channelId }
