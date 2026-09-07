package com.helpi.conversation.keypoints

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test de contrato — bloqueante de CI.
 * Verifica el muestreo entero y el centrado contra los mismos valores de
 * referencia que usa helpi-ml.
 */
class KeypointContractTest {

    // ---- aplanado ----

    @Test
    fun `aplanado ubica cada bloque en su rango`() {
        val left = FloatArray(63) { 1f }
        val right = FloatArray(63) { 2f }
        val pose = FloatArray(75) { 3f }
        val frame = KeypointContract.flattenFrame(left, right, pose)
        assertEquals(201, frame.size)
        assertEquals(1f, frame[0], 0f)
        assertEquals(1f, frame[62], 0f)
        assertEquals(2f, frame[63], 0f)
        assertEquals(2f, frame[125], 0f)
        assertEquals(3f, frame[126], 0f)
        assertEquals(3f, frame[200], 0f)
    }

    @Test
    fun `orden intercalado por landmark, no por eje`() {
        // landmark 0 = (10, 20, 30), landmark 1 = (11, 21, 31)
        val left = FloatArray(63)
        left[0] = 10f; left[1] = 20f; left[2] = 30f
        left[3] = 11f; left[4] = 21f; left[5] = 31f
        val frame = KeypointContract.flattenFrame(left, null, null)
        assertArrayEquals(
            floatArrayOf(10f, 20f, 30f, 11f, 21f, 31f),
            frame.copyOfRange(0, 6),
            0f,
        )
    }

    @Test
    fun `no detectado rellena con ceros`() {
        val frame = KeypointContract.flattenFrame(null, null, null)
        assertTrue(frame.all { it == 0f })
    }

    @Test
    fun `pose de 33 landmarks descarta piernas`() {
        val pose = FloatArray(99) { i -> if (i >= 75) 99f else 5f }
        val frame = KeypointContract.flattenFrame(null, null, pose)
        assertEquals(5f, frame[200], 0f)
        assertFalse(frame.any { it == 99f })
    }

    // ---- centrado ----

    private fun frameWithShoulders(lx: Float, ly: Float, rx: Float, ry: Float): FloatArray {
        val f = FloatArray(201)
        f[KeypointContract.LEFT_SHOULDER_OFFSET] = lx
        f[KeypointContract.LEFT_SHOULDER_OFFSET + 1] = ly
        f[KeypointContract.LEFT_SHOULDER_OFFSET + 2] = 0.5f
        f[KeypointContract.RIGHT_SHOULDER_OFFSET] = rx
        f[KeypointContract.RIGHT_SHOULDER_OFFSET + 1] = ry
        f[KeypointContract.RIGHT_SHOULDER_OFFSET + 2] = 0.6f
        return f
    }

    @Test
    fun `offsets de hombros son 159 y 162`() {
        assertEquals(159, KeypointContract.LEFT_SHOULDER_OFFSET)
        assertEquals(162, KeypointContract.RIGHT_SHOULDER_OFFSET)
    }

    @Test
    fun `centrado resta punto medio a x e y pero no a z`() {
        val f = frameWithShoulders(0.4f, 0.3f, 0.6f, 0.5f) // centro = (0.5, 0.4)
        f[0] = 0.7f; f[1] = 0.9f; f[2] = 0.25f // landmark de mano
        val out = KeypointContract.centerFrame(f)
        assertEquals(0.7f - 0.5f, out[0], 1e-6f)
        assertEquals(0.9f - 0.4f, out[1], 1e-6f)
        assertEquals(0.25f, out[2], 0f) // z intacta
        // hombro izquierdo también centrado en x/y, z intacta
        assertEquals(0.4f - 0.5f, out[159], 1e-6f)
        assertEquals(0.3f - 0.4f, out[160], 1e-6f)
        assertEquals(0.5f, out[161], 0f)
    }

    @Test
    fun `landmarks ausentes conservan ceros despues de centrar`() {
        val f = frameWithShoulders(0.4f, 0.3f, 0.6f, 0.5f)
        val out = KeypointContract.centerFrame(f)
        // toda la mano izquierda estaba ausente
        for (i in 0 until 63) assertEquals(0f, out[i], 0f)
    }

    @Test
    fun `centrar no modifica la entrada`() {
        val f = frameWithShoulders(0.4f, 0.3f, 0.6f, 0.5f)
        val copy = f.copyOf()
        KeypointContract.centerFrame(f)
        assertArrayEquals(copy, f, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hombros ausentes no permiten centrar`() {
        KeypointContract.centerFrame(FloatArray(201))
    }

    // ---- muestreo temporal ----

    @Test
    fun `caso de referencia T=46 i=13 da 15, no 14`() {
        // 13*45/39 = 15 entero; linspace float da 14.999999999999998 -> 14
        val idx = KeypointContract.sampleIndices(46)
        assertEquals(15, idx[13])
    }

    @Test
    fun `T=40 es identidad`() {
        val idx = KeypointContract.sampleIndices(40)
        assertArrayEquals(IntArray(40) { it }, idx)
    }

    @Test
    fun `T=41 y T=39 y extremos`() {
        val idx41 = KeypointContract.sampleIndices(41)
        assertEquals(40, idx41.size)
        assertEquals(0, idx41.first())
        assertEquals(40, idx41.last())

        val idx1 = KeypointContract.sampleIndices(1)
        assertArrayEquals(intArrayOf(0), idx1)

        val idx39 = KeypointContract.sampleIndices(39)
        assertEquals(39, idx39.size) // T<N: identidad, el padding lo hace sampleFrames
    }

    @Test
    fun `muestreo entero exacto para un rango amplio de T`() {
        for (t in 40..300) {
            val idx = KeypointContract.sampleIndices(t)
            assertEquals(40, idx.size)
            assertEquals(0, idx.first())
            assertEquals(t - 1, idx.last())
            for (i in 0 until 40) {
                assertEquals((i.toLong() * (t - 1) / 39).toInt(), idx[i])
            }
            // monótono no decreciente
            for (i in 1 until 40) assertTrue(idx[i] >= idx[i - 1])
        }
    }

    @Test
    fun `T menor que 40 repite el ultimo cuadro`() {
        val frames = (0 until 10).map { v -> FloatArray(201) { v.toFloat() } }
        val out = KeypointContract.sampleFrames(frames)
        assertEquals(40, out.size)
        assertEquals(0f, out[0][0], 0f)
        assertEquals(9f, out[9][0], 0f)
        for (i in 10 until 40) assertEquals(9f, out[i][0], 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `secuencia vacia es invalida`() {
        KeypointContract.sampleFrames(emptyList())
    }

    // ---- tensor completo ----

    @Test
    fun `tensor completo tiene 40x201 y esta centrado`() {
        val frames = (0 until 46).map {
            val f = FloatArray(201)
            f[159] = 0.4f; f[160] = 0.3f; f[161] = 0.5f
            f[162] = 0.6f; f[163] = 0.5f; f[164] = 0.6f
            f[0] = 0.7f; f[1] = 0.9f; f[2] = 0.25f
            f
        }
        val tensor = KeypointContract.buildInputTensor(frames)
        assertEquals(40 * 201, tensor.size)
        assertEquals(0.2f, tensor[0], 1e-6f)     // x centrada
        assertEquals(0.5f, tensor[1], 1e-6f)     // y centrada
        assertEquals(0.25f, tensor[2], 0f)       // z intacta
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valores no finitos invalidan el tensor`() {
        val f = FloatArray(201)
        f[159] = 0.4f; f[160] = 0.3f; f[161] = 0.5f
        f[162] = 0.6f; f[163] = 0.5f; f[164] = 0.6f
        f[0] = Float.NaN; f[1] = 1f; f[2] = 1f
        KeypointContract.buildInputTensor(listOf(f))
    }
}
