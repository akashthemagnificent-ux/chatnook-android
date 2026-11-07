package com.chatnook.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePrepTest {

    @Test
    fun `small images are not upscaled`() {
        assertEquals(800 to 600, fitInside(800, 600))
        assertEquals(1600 to 900, fitInside(1600, 900))
    }

    @Test
    fun `large images fit the long edge`() {
        assertEquals(1600 to 1200, fitInside(4000, 3000))
        assertEquals(1200 to 1600, fitInside(3000, 4000))
    }

    @Test
    fun `degenerate sizes never produce a zero dimension`() {
        assertEquals(1 to 1, fitInside(0, 0))
        assertEquals(1 to 1, fitInside(-5, 100))
        assertEquals(1 to 1600, fitInside(1, 20000))
    }

    @Test
    fun `sample size keeps the decode at or above the target`() {
        assertEquals(1, sampleSize(1200, 900, 1600))
        assertEquals(2, sampleSize(3200, 2400, 1600))
        assertEquals(4, sampleSize(8000, 6000, 1600))
        val decoded = 8000 / sampleSize(8000, 6000, 1600)
        assertTrue(decoded >= 1600)
    }
}
