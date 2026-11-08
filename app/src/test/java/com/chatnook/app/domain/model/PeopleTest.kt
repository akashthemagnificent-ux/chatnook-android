package com.chatnook.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleTest {

    private fun member(
        username: String,
        role: String? = null,
        position: Int = 0,
        owner: Boolean = false,
        displayName: String? = null,
    ) = Member(
        userId = "id-$username",
        username = username,
        displayName = displayName,
        avatarUrl = null,
        bannerUrl = null,
        badges = emptyList(),
        customStatus = null,
        nickname = null,
        roleName = role,
        roleColor = null,
        rolePosition = position,
        isOwner = owner,
    )

    @Test
    fun `owner comes first, then roles by position, then the rest`() {
        val groups = groupMembers(
            listOf(
                member("zed"),
                member("mod", role = "Moderator", position = 5),
                member("owner", owner = true),
                member("admin", role = "Admin", position = 9),
                member("amy"),
            ),
        )
        assertEquals(listOf("Owner", "Admin", "Moderator", "Members"), groups.map { it.title })
        assertEquals(listOf("owner"), groups[0].members.map { it.username })
        assertEquals(listOf("admin"), groups[1].members.map { it.username })
        assertEquals(listOf("amy", "zed"), groups[3].members.map { it.username })
    }

    @Test
    fun `a member with a role is not repeated in Members`() {
        val groups = groupMembers(listOf(member("mod", role = "Moderator", position = 1)))
        assertEquals(listOf("Moderator"), groups.map { it.title })
        assertEquals(1, groups.sumOf { it.members.size })
    }

    @Test
    fun `display name drives the alphabetical order`() {
        val groups = groupMembers(
            listOf(
                member("user_b", displayName = "Zoe"),
                member("user_a", displayName = "Alice"),
            ),
        )
        assertEquals(listOf("Alice", "Zoe"), groups.single().members.map { it.label })
    }

    @Test
    fun `an empty roster groups to nothing`() {
        assertEquals(emptyList<MemberGroup>(), groupMembers(emptyList()))
    }

    @Test
    fun `direct messages sort by newest and dedupe by channel`() {
        val dms = listOf(
            DmSummary("c1", "u1", "Alice", null, "hi", 1_000),
            DmSummary("c2", "u2", "Bob", null, "yo", 3_000),
            DmSummary("c1", "u1", "Alice", null, "hi again", 2_000),
        )
        val sorted = sortDms(dms)
        assertEquals(listOf("c2", "c1"), sorted.map { it.channelId })
        assertEquals("hi again", sorted[1].lastMessage)
    }

    @Test
    fun `conversations without messages fall back to the name order`() {
        val dms = listOf(
            DmSummary("c1", "u1", "bob", null, null, 0),
            DmSummary("c2", "u2", "Alice", null, null, 0),
        )
        assertEquals(listOf("Alice", "bob"), sortDms(dms).map { it.name })
    }
}
