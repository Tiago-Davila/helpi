package com.helpi.conversation.lsa.sequence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SequenceCaptureBufferTest {

    @Test
    fun `captura explicita conserva cuadros y calcula cobertura`() {
        val buffer = SequenceCaptureBuffer(maxDurationMs = 1_000)
        val hand = FloatArray(63) { 0.25f }

        assertEquals(SequenceAppendResult.NOT_CAPTURING, buffer.append(0, hand, null))
        buffer.start(100)
        assertEquals(SequenceAppendResult.ACCEPTED, buffer.append(100, hand, null))
        assertEquals(SequenceAppendResult.ACCEPTED, buffer.append(200, null, null))
        val capture = buffer.stop(300)

        assertNotNull(capture)
        assertEquals(2, capture!!.frames.size)
        assertEquals(1, capture.validFrameCount)
        assertEquals(0.5f, capture.validFrameRatio, 0f)
        assertEquals(200L, capture.durationMs)
        assertFalse(buffer.isCapturing)
    }

    @Test
    fun `captura demasiado larga no agrega el cuadro excedido`() {
        val buffer = SequenceCaptureBuffer(maxDurationMs = 100)
        buffer.start(1_000)

        assertEquals(
            SequenceAppendResult.TOO_LONG,
            buffer.append(1_101, FloatArray(63), null),
        )
        assertEquals(0, buffer.frameCount)
        assertNull(buffer.stop())
    }

    @Test
    fun `cancelar borra el buffer y no deja inferencia pendiente`() {
        val buffer = SequenceCaptureBuffer()
        buffer.start(0)
        buffer.append(0, FloatArray(63), FloatArray(63))
        buffer.cancel()

        assertFalse(buffer.isCapturing)
        assertEquals(0, buffer.frameCount)
        assertTrue(buffer.stop() == null)
    }
}
