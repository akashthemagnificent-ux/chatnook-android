package com.chatnook.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerTest {

    private val now = System.currentTimeMillis()

    private fun invite(
        uses: Int = 0,
        maxUses: Int? = null,
        expiresAt: Long? = null,
    ) = Invite(
        code = "ABC12345",
        uses = uses,
        maxUses = maxUses,
        createdAt = now,
        expiresAt = expiresAt,
    )

    @Test
    fun `an expired invite is dead`() {
        assertTrue(invite(expiresAt = now - 1_000).isDead)
        assertFalse(invite(expiresAt = now + 60_000).isDead)
        assertFalse(invite().isDead)
    }

    @Test
    fun `a used up invite is dead`() {
        assertTrue(invite(uses = 5, maxUses = 5).isDead)
        assertFalse(invite(uses = 4, maxUses = 5).isDead)
        assertFalse(invite(uses = 999).isDead)
    }

    @Test
    fun `moving a channel swaps it with its neighbour`() {
        val order = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), movedOrder(order, "a", 1))
        assertEquals(listOf("a", "c", "b"), movedOrder(order, "c", -1))
    }

    @Test
    fun `a move off either end changes nothing`() {
        val order = listOf("a", "b", "c")
        assertNull(movedOrder(order, "a", -1))
        assertNull(movedOrder(order, "c", 1))
        assertNull(movedOrder(order, "missing", 1))
    }
}
