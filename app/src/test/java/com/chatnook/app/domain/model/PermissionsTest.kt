package com.chatnook.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {

    private fun role(
        id: String = "r1",
        permissions: Long,
        everyone: Boolean = false,
        position: Int = 1,
    ) = GuildRole(
        id = id,
        name = id,
        color = null,
        position = position,
        permissions = permissions,
        isEveryone = everyone,
    )

    private val everyone = role(
        id = "@everyone",
        permissions = PermissionBits.VIEW_CHANNEL or PermissionBits.SEND_MESSAGES,
        everyone = true,
        position = 0,
    )

    @Test
    fun `the owner holds every permission regardless of roles`() {
        val bits = resolvePermissions(isOwner = true, everyone = null, roles = emptyList())
        assertEquals(PermissionBits.ALL, bits)
        assertTrue(bits has PermissionBits.MANAGE_GUILD)
        assertTrue(bits has PermissionBits.ADMINISTRATOR)
    }

    @Test
    fun `a member without roles keeps only the everyone bits`() {
        val bits = resolvePermissions(isOwner = false, everyone = everyone, roles = emptyList())
        assertTrue(bits has PermissionBits.SEND_MESSAGES)
        assertFalse(bits has PermissionBits.MANAGE_CHANNELS)
    }

    @Test
    fun `roles are unioned`() {
        val bits = resolvePermissions(
            isOwner = false,
            everyone = everyone,
            roles = listOf(
                role("a", PermissionBits.MANAGE_CHANNELS),
                role("b", PermissionBits.CREATE_INVITE),
            ),
        )
        assertTrue(bits has PermissionBits.MANAGE_CHANNELS)
        assertTrue(bits has PermissionBits.CREATE_INVITE)
        assertTrue(bits has PermissionBits.SEND_MESSAGES)
    }

    @Test
    fun `the administrator bit short-circuits to everything`() {
        val bits = resolvePermissions(
            isOwner = false,
            everyone = everyone,
            roles = listOf(role("admin", PermissionBits.ADMINISTRATOR, position = 5)),
        )
        assertEquals(PermissionBits.ALL, bits)
    }

    @Test
    fun `an explicit everyone role in the assigned list is not counted twice`() {
        val bits = resolvePermissions(
            isOwner = false,
            everyone = role("@everyone", PermissionBits.VIEW_CHANNEL, everyone = true),
            roles = listOf(role("@everyone", PermissionBits.ADMINISTRATOR, everyone = true)),
        )
        assertFalse(bits has PermissionBits.ADMINISTRATOR)
        assertEquals(PermissionBits.VIEW_CHANNEL, bits)
    }

    @Test
    fun `missing everyone role grants nothing`() {
        assertEquals(0L, resolvePermissions(isOwner = false, everyone = null, roles = emptyList()))
    }

    @Test
    fun `every defined bit is inside ALL and no bit outside it is`() {
        val defined = listOf(
            PermissionBits.VIEW_CHANNEL,
            PermissionBits.SEND_MESSAGES,
            PermissionBits.MANAGE_MESSAGES,
            PermissionBits.MANAGE_CHANNELS,
            PermissionBits.MANAGE_ROLES,
            PermissionBits.CREATE_INVITE,
            PermissionBits.KICK_MEMBERS,
            PermissionBits.ADMINISTRATOR,
            PermissionBits.MENTION_EVERYONE,
            PermissionBits.ATTACH_FILES,
            PermissionBits.ADD_REACTIONS,
            PermissionBits.MANAGE_GUILD,
        )
        assertEquals(12, defined.distinct().size)
        assertTrue(defined.all { PermissionBits.ALL has it })
        assertFalse(PermissionBits.ALL has (1L shl 12))
    }

    @Test
    fun `toggling one permission leaves the rest alone`() {
        val start = PermissionBits.SEND_MESSAGES
        val granted = start.withPermission(PermissionBits.MANAGE_ROLES, true)
        assertTrue(granted has PermissionBits.MANAGE_ROLES)
        assertTrue(granted has PermissionBits.SEND_MESSAGES)

        val revoked = granted.withPermission(PermissionBits.SEND_MESSAGES, false)
        assertFalse(revoked has PermissionBits.SEND_MESSAGES)
        assertTrue(revoked has PermissionBits.MANAGE_ROLES)
    }
}
