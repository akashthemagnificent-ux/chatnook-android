package com.chatnook.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun `a higher patch wins`() {
        assertTrue(UpdateCheck.isNewer("v0.1.12", "0.1.9"))
        assertFalse(UpdateCheck.isNewer("v0.1.9", "0.1.12"))
    }

    @Test
    fun `a higher minor wins over any patch`() {
        assertTrue(UpdateCheck.isNewer("v0.2", "0.1.99"))
        assertFalse(UpdateCheck.isNewer("v0.1.99", "0.2"))
    }

    @Test
    fun `the same version is not newer`() {
        assertFalse(UpdateCheck.isNewer("v0.1.7", "0.1.7"))
        assertFalse(UpdateCheck.isNewer("0.1.7", "v0.1.7"))
    }

    @Test
    fun `a missing part counts as zero`() {
        assertFalse(UpdateCheck.isNewer("0.1.0", "0.1"))
        assertFalse(UpdateCheck.isNewer("0.1", "0.1.0"))
    }

    @Test
    fun `a suffix is ignored`() {
        assertFalse(UpdateCheck.isNewer("v0.1.5-beta", "0.1.5"))
        assertTrue(UpdateCheck.isNewer("v0.1.6-beta", "0.1.5"))
    }
}
