package com.ebookmaker.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadrilateralTest {

    @Test
    fun area_calculatesCorrectly() {
        // Rectangle 100x200 -> Area 20000
        val quad = Quadrilateral(
            topLeft = VisionPoint(0f, 0f),
            topRight = VisionPoint(100f, 0f),
            bottomRight = VisionPoint(100f, 200f),
            bottomLeft = VisionPoint(0f, 200f)
        )
        assertEquals(20000f, quad.area, 0.01f)
        assertEquals(100f, quad.width, 0.01f)
        assertEquals(200f, quad.height, 0.01f)
    }

    @Test
    fun movementThreshold_detectsStability() {
        val quad1 = Quadrilateral(
            topLeft = VisionPoint(10f, 10f),
            topRight = VisionPoint(100f, 10f),
            bottomRight = VisionPoint(100f, 100f),
            bottomLeft = VisionPoint(10f, 100f)
        )
        val quad2 = Quadrilateral(
            topLeft = VisionPoint(12f, 11f),
            topRight = VisionPoint(101f, 12f),
            bottomRight = VisionPoint(99f, 98f),
            bottomLeft = VisionPoint(11f, 101f)
        )
        assertTrue(quad1.isMaxMovementLessThan(quad2, 5f))

        val movedQuad = Quadrilateral(
            topLeft = VisionPoint(50f, 50f),
            topRight = VisionPoint(150f, 50f),
            bottomRight = VisionPoint(150f, 150f),
            bottomLeft = VisionPoint(50f, 150f)
        )
        assertFalse(quad1.isMaxMovementLessThan(movedQuad, 5f))
    }
}
