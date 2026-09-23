package com.helpi.conversation.lsa.sequence

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.helpi.conversation.keypoints.SequenceKeypointContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.math.abs

/**
 * ETAPA 2 — contrato de keypoints sin modelo.
 *
 * Cuando se copie sequence_contract_fixture.json a androidTest/assets, este
 * test compara el tensor Kotlin con los bytes producidos por Python. Hasta
 * entonces queda explícitamente pendiente, igual que el fixture del modelo.
 */
@RunWith(AndroidJUnit4::class)
class SequenceContractFixtureTest {

    private val testContext: Context =
        InstrumentationRegistry.getInstrumentation().context

    @Test
    fun tensorKotlinCoincideConFixturePython() {
        val fixtureJson = try {
            testContext.assets.open("sequence_contract_fixture.json").use {
                String(it.readBytes(), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
        assumeTrue("sequence_contract_fixture.json ausente: etapa 2 pendiente", fixtureJson != null)

        val root = JSONObject(fixtureJson!!)
        assertEquals(75, root.getInt("frames"))
        assertEquals(126, root.getInt("coords"))
        val cases = root.getJSONArray("cases")
        assertTrue("fixture sin casos", cases.length() > 0)

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val name = case.getString("name")
            val rawShape = case.getJSONArray("rawShape")
            val rawFrames = rawShape.getInt(0)
            val raw = decodeFloats(case.getString("rawF32LeBase64"))
            assertEquals("$name: raw shape", rawFrames * 126, raw.size)

            val frames = List(rawFrames) { frame ->
                raw.copyOfRange(frame * 126, (frame + 1) * 126)
            }
            val expected = decodeFloats(case.getString("expectedInputF32LeBase64"))
            val actual = SequenceKeypointContract.buildInputTensor(frames)

            assertEquals("$name: tensor size", 75 * 126, actual.size)
            assertEquals("$name: expected size", actual.size, expected.size)
            for (floatIndex in actual.indices) {
                assertTrue(
                    "$name: float $floatIndex esperado=${expected[floatIndex]} actual=${actual[floatIndex]}",
                    abs(expected[floatIndex] - actual[floatIndex]) <= 1e-6f,
                )
            }

            val expectedIndices = case.getJSONArray("sampleIndices")
            val actualIndices = SequenceKeypointContract.sampleIndices(rawFrames)
            assertEquals("$name: índices", expectedIndices.length(), actualIndices.size)
            for (sample in actualIndices.indices) {
                assertEquals("$name: índice $sample", expectedIndices.getInt(sample), actualIndices[sample])
            }
        }
    }

    private fun decodeFloats(base64: String): FloatArray {
        val bytes = Base64.getDecoder().decode(base64)
        require(bytes.size % 4 == 0) { "fixture F32LE truncado" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also(buffer::get)
    }
}
