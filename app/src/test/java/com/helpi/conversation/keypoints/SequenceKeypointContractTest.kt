package com.helpi.conversation.keypoints

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SequenceKeypointContractTest {

    @Test
    fun `aplanado de manos ausentes produce 126 ceros`() {
        val frame = SequenceKeypointContract.flattenHands(null, null)
        assertEquals(126, frame.size)
        assertTrue(frame.all { it == 0f })
    }

    @Test
    fun `aplanado conserva mano izquierda y derecha`() {
        val left = FloatArray(63) { 1f }
        val right = FloatArray(63) { 2f }
        val frame = SequenceKeypointContract.flattenHands(left, right)
        assertEquals(1f, frame.first(), 0f)
        assertEquals(1f, frame[62], 0f)
        assertEquals(2f, frame[63], 0f)
        assertEquals(2f, frame.last(), 0f)
    }

    @Test
    fun `muestreo entero de T=79 conserva el indice 37 correcto`() {
        val indices = SequenceKeypointContract.sampleIndices(79)
        assertEquals(75, indices.size)
        assertEquals(39, indices[37])
        assertEquals(78, indices.last())
    }

    @Test
    fun `secuencia corta se rellena con ceros`() {
        val first = FloatArray(126)
        first[0] = 1f
        first[1] = 2f
        val second = FloatArray(126)
        second[0] = 2f
        second[1] = 4f
        val frames = listOf(first, second)
        val tensor = SequenceKeypointContract.buildInputTensor(frames)
        assertEquals(75 * 126, tensor.size)
        assertTrue(tensor.copyOfRange(0, 252).any { it != 0f })
        for (i in 2 until 75) {
            assertTrue(tensor.copyOfRange(i * 126, (i + 1) * 126).all { it == 0f })
        }
    }

    @Test
    fun `z no se centra ni se escala`() {
        val frame = FloatArray(126)
        frame[0] = 2f
        frame[1] = 4f
        frame[2] = 0.75f
        val tensor = SequenceKeypointContract.buildInputTensor(listOf(frame))
        assertEquals(0.75f, tensor[2], 1e-6f)
        assertTrue(tensor[0].isFinite())
        assertTrue(tensor[1].isFinite())
    }

    @Test
    fun `entrada original no se modifica`() {
        val frame = FloatArray(126) { index -> index.toFloat() / 100f }
        val original = frame.copyOf()
        SequenceKeypointContract.buildInputTensor(listOf(frame))
        assertArrayEquals(original, frame, 0f)
    }
}
