package com.helpi.conversation.keypoints

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * Test de contrato — bloqueante de CI.
 * Verifica el muestreo entero y la normalización v3 contra los mismos valores
 * de referencia que produce eva_contract.py (contract_v3_fixture.json).
 */
class KeypointContractTest {

    // ---- aplanado ----

    @Test
    fun `aplanado ubica cada bloque en su rango`() {
        val left = FloatArray(63) { 1f }
        val right = FloatArray(63) { 2f }
        val pose = FloatArray(99) { 3f }
        val frame = KeypointContract.flattenFrame(left, right, pose)
        assertEquals(168, frame.size)
        assertEquals(1f, frame[0], 0f)
        assertEquals(1f, frame[62], 0f)
        assertEquals(2f, frame[63], 0f)
        assertEquals(2f, frame[125], 0f)
        assertEquals(3f, frame[126], 0f)
        assertEquals(3f, frame[167], 0f)
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
    fun `pose de 33 landmarks conserva 11 a 24 y descarta cara y piernas`() {
        val pose = FloatArray(99) { coordinate -> (coordinate / 3).toFloat() }
        val frame = KeypointContract.flattenFrame(null, null, pose)
        val reducedPose = frame.copyOfRange(KeypointContract.POSE_OFFSET, KeypointContract.COORDS)
        assertEquals(11f, frame[126], 0f)
        assertEquals(24f, frame[167], 0f)
        assertFalse(reducedPose.any { it in 0f..10f })
        assertFalse(reducedPose.any { it >= 25f })
    }

    // ---- normalización v3 ----

    /**
     * Cuadro de una escena física en metros proyectada a una cámara:
     * hombros a ±0,20 m y un landmark de mano en (-0,25, 0,10, z=0,02).
     */
    private fun cameraFrame(width: Int, height: Int, pxPerMeter: Float, cx: Float, cy: Float): FloatArray {
        val f = FloatArray(KeypointContract.COORDS)
        fun put(offset: Int, x: Float, y: Float, z: Float) {
            f[offset] = (x * pxPerMeter + cx) / width
            f[offset + 1] = (y * pxPerMeter + cy) / height
            f[offset + 2] = z * pxPerMeter / width
        }
        put(KeypointContract.LEFT_SHOULDER_OFFSET, -0.20f, 0f, 0.05f)
        put(KeypointContract.RIGHT_SHOULDER_OFFSET, 0.20f, 0f, 0.06f)
        put(0, -0.25f, 0.10f, 0.02f)
        return f
    }

    @Test
    fun `offsets de hombros son 126 y 129`() {
        assertEquals(126, KeypointContract.LEFT_SHOULDER_OFFSET)
        assertEquals(129, KeypointContract.RIGHT_SHOULDER_OFFSET)
        assertEquals(3, KeypointContract.CONTRACT_VERSION)
    }

    @Test
    fun `mano queda en anchos de hombro y centrada`() {
        val out = KeypointContract.normalizeSequence(
            listOf(cameraFrame(1280, 720, 1000f, 640f, 360f)), 1280, 720,
        )[0]
        assertEquals(-0.625f, out[0], 1e-5f) // -0,25 m / 0,40 m
        assertEquals(0.25f, out[1], 1e-5f)
        assertEquals(0.05f, out[2], 1e-5f) // z escalada pero no centrada
        assertEquals(-0.5f, out[126], 1e-5f)
        assertEquals(0.5f, out[129], 1e-5f)
    }

    @Test
    fun `no depende de la distancia ni de la orientacion`() {
        val cerca = KeypointContract.normalizeSequence(listOf(cameraFrame(480, 640, 900f, 240f, 300f)), 480, 640)[0]
        val lejos = KeypointContract.normalizeSequence(listOf(cameraFrame(1920, 1080, 300f, 900f, 500f)), 1920, 1080)[0]
        assertArrayEquals(cerca, lejos, 1e-4f)
    }

    @Test
    fun `landmarks ausentes conservan ceros`() {
        val out = KeypointContract.normalizeSequence(listOf(cameraFrame(960, 540, 500f, 480f, 270f)), 960, 540)[0]
        for (i in 3 until 126) assertEquals(0f, out[i], 0f)
    }

    @Test
    fun `normalizar no modifica la entrada`() {
        val f = cameraFrame(960, 540, 500f, 480f, 270f)
        val copy = f.copyOf()
        KeypointContract.normalizeSequence(listOf(f), 960, 540)
        assertArrayEquals(copy, f, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sin hombros la secuencia es invalida`() {
        val frame = FloatArray(KeypointContract.COORDS)
        frame[0] = 0.25f
        frame[1] = 0.5f
        KeypointContract.buildInputTensor(listOf(frame), 960, 540)
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
        assertArrayEquals(IntArray(40), idx1)

        val idx39 = KeypointContract.sampleIndices(39)
        assertEquals(40, idx39.size)
        assertEquals(38, idx39.last())
        assertEquals((0 until 39).toList(), idx39.distinct())
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
    fun `T menor que 40 estira repitiendo cada cuadro de forma pareja`() {
        val frames = (0 until 10).map { v -> FloatArray(KeypointContract.COORDS) { v.toFloat() } }
        val out = KeypointContract.sampleFrames(frames)
        assertEquals(40, out.size)
        // T=10: cada cuadro exactamente 4 veces, en orden
        for (i in 0 until 40) assertEquals((i / 4).toFloat(), out[i][0], 0f)
        for (t in 1 until 40) {
            val counts = KeypointContract.sampleIndices(t).toList().groupingBy { it }.eachCount()
            assertEquals("T=$t usa todos los cuadros", t, counts.size)
            assertTrue("T=$t reparte parejo", counts.values.max() - counts.values.min() <= 1)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `secuencia vacia es invalida`() {
        KeypointContract.sampleFrames(emptyList())
    }

    // ---- tensor completo ----

    @Test
    fun `tensor completo tiene 40x168`() {
        val frames = (0 until 46).map { cameraFrame(960, 540, 500f, 480f, 270f) }
        val tensor = KeypointContract.buildInputTensor(frames, 960, 540)
        assertEquals(40 * 168, tensor.size)
        assertEquals(-0.625f, tensor[39 * 168], 1e-5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valores no finitos invalidan el tensor`() {
        val f = cameraFrame(960, 540, 500f, 480f, 270f)
        f[0] = Float.NaN
        KeypointContract.buildInputTensor(listOf(f), 960, 540)
    }

    // ---- paridad con Python ----

    @Test
    fun `coincide con el fixture de eva_contract py`() {
        val stream = javaClass.classLoader?.getResourceAsStream("contract_v3_fixture.json")
        assertNotNull("falta contract_v3_fixture.json en src/test/resources", stream)
        @Suppress("UNCHECKED_CAST")
        val fixture = MiniJson.parse(stream!!.bufferedReader().readText()) as Map<String, Any?>
        assertEquals(KeypointContract.CONTRACT_VERSION, (fixture["contractVersion"] as Double).toInt())
        val tolerance = (fixture["tolerancia"] as Double).toFloat()
        val cases = fixture["casos"] as List<*>
        assertTrue("fixture sin casos", cases.isNotEmpty())

        for (raw in cases) {
            @Suppress("UNCHECKED_CAST")
            val case = raw as Map<String, Any?>
            val name = case["nombre"] as String
            val width = (case["width"] as Double).toInt()
            val height = (case["height"] as Double).toInt()
            val frames = (case["cuadros"] as List<*>).map { frame ->
                (frame as List<*>).map { (it as Double).toFloat() }.toFloatArray()
            }
            if (case.containsKey("error")) {
                try {
                    KeypointContract.buildInputTensor(frames, width, height)
                    fail("$name: se esperaba secuencia inválida")
                } catch (_: IllegalArgumentException) {
                    // esperado
                }
                continue
            }
            val actual = KeypointContract.buildInputTensor(frames, width, height)
            val expected = (case["tensor"] as List<*>).flatMap { frame ->
                (frame as List<*>).map { (it as Double).toFloat() }
            }
            assertEquals("$name: tamaño", expected.size, actual.size)
            for (i in actual.indices) {
                if (abs(actual[i] - expected[i]) > tolerance) {
                    fail("$name: índice $i (cuadro ${i / 168}, coord ${i % 168}) " +
                        "Kotlin ${actual[i]} != Python ${expected[i]}")
                }
            }
        }
    }
}

/**
 * Parser JSON mínimo para el fixture: org.json de Android no está disponible
 * en los tests locales de JVM. Números -> Double, objetos -> Map, arreglos -> List.
 */
private object MiniJson {
    fun parse(text: String): Any? = Parser(text).run { value().also { skipWs() } }

    private class Parser(private val s: String) {
        private var i = 0

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skipWs()
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> if (c == '-' || c.isDigit()) num() else error("JSON inválido en $i: '$c'")
            }
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            i++
            skipWs()
            if (s[i] == '}') { i++; return out }
            while (true) {
                skipWs()
                val key = str()
                skipWs(); i++ // ':'
                out[key] = value()
                skipWs()
                if (s[i++] == '}') return out
            }
        }

        private fun arr(): List<Any?> {
            val out = ArrayList<Any?>()
            i++
            skipWs()
            if (s[i] == ']') { i++; return out }
            while (true) {
                out.add(value())
                skipWs()
                if (s[i++] == ']') return out
            }
        }

        private fun str(): String {
            val sb = StringBuilder()
            i++ // comilla inicial
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (val e = s[i]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                } else {
                    sb.append(s[i])
                }
                i++
            }
            i++
            return sb.toString()
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }
    }
}
