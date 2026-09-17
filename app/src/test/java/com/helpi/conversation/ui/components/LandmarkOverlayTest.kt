package com.helpi.conversation.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LandmarkOverlayTest {

    @Test
    fun `fit center conserva aspecto y centra bandas laterales`() {
        val transform = overlayTransform(
            canvasWidth = 1000f,
            canvasHeight = 1000f,
            imageWidth = 480,
            imageHeight = 640,
            mirrorHorizontally = true,
        )!!

        assertEquals(1.5625f, transform.scale, 0.0001f)
        assertEquals(125f, transform.offsetX, 0.0001f)
        assertEquals(0f, transform.offsetY, 0.0001f)
    }

    @Test
    fun `camara frontal espeja x pero conserva y`() {
        val transform = overlayTransform(
            canvasWidth = 480f,
            canvasHeight = 640f,
            imageWidth = 480,
            imageHeight = 640,
            mirrorHorizontally = true,
        )!!

        val point = transform.project(0.25f, 0.40f)
        assertEquals(360f, point.x, 0.0001f)
        assertEquals(256f, point.y, 0.0001f)
    }

    @Test
    fun `dimensiones invalidas no producen transformacion`() {
        assertNull(overlayTransform(0f, 640f, 480, 640, true))
        assertNull(overlayTransform(480f, 640f, 0, 640, true))
    }
}
