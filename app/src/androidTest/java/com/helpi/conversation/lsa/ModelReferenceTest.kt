package com.helpi.conversation.lsa

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * ETAPA 1 — Modelo sin cámara (bloqueante).
 *
 * fixture_android.json (androidTest/assets) contiene secuencias reales con
 * los scores que produjo el mismo .tflite en la máquina de entrenamiento.
 * Si este test pasa, el despliegue del modelo está resuelto y toda falla
 * posterior es de captura.
 *
 * Formato del fixture:
 * {
 *   "tolerance": 1e-3,
 *   "cases": [ { "name": "...", "input": [40*201 floats], "expected": [numClasses floats] } ]
 * }
 *
 * Sin artefactos reales (los provee helpi-ml) el test queda en "assumption
 * failed": no aprueba la etapa, la deja explícitamente pendiente.
 */
@RunWith(AndroidJUnit4::class)
class ModelReferenceTest {

    private val appContext: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext: Context =
        InstrumentationRegistry.getInstrumentation().context

    @Test
    fun salidasCoincidenConLaMaquinaDeEntrenamiento() {
        val bundleResult = ModelBundle.load(appContext)
        assumeTrue(
            "artefactos del modelo ausentes: etapa 1 pendiente, no aprobada",
            bundleResult is ModelBundleResult.Ready,
        )
        val bundle = (bundleResult as ModelBundleResult.Ready).bundle

        val fixtureJson = try {
            testContext.assets.open("fixture_android.json").use {
                String(it.readBytes(), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
        assumeTrue("fixture_android.json ausente: etapa 1 pendiente", fixtureJson != null)

        val fixture = JSONObject(fixtureJson!!)
        val tolerance = fixture.getDouble("tolerance").toFloat()
        assertTrue("tolerancia esperada 1e-3", abs(tolerance - 1e-3f) < 1e-9f)
        val cases = fixture.getJSONArray("cases")
        assertTrue("fixture sin casos", cases.length() > 0)

        SignClassifier(bundle).use { classifier ->
            for (c in 0 until cases.length()) {
                val case = cases.getJSONObject(c)
                val name = case.getString("name")
                val inputArr = case.getJSONArray("input")
                assertEquals("$name: tamaño de entrada", 40 * 201, inputArr.length())
                val input = FloatArray(inputArr.length()) { inputArr.getDouble(it).toFloat() }

                // el fixture debe tener valores reales, no ceros con forma correcta
                assertTrue("$name: entrada sin variación (¿buffer en ceros?)",
                    input.distinct().size > 10)

                val expectedArr = case.getJSONArray("expected")
                val expected = FloatArray(expectedArr.length()) { expectedArr.getDouble(it).toFloat() }
                assertEquals("$name: clases", bundle.manifest.numClasses, expected.size)

                val actual = classifier.classify(input)
                for (i in expected.indices) {
                    assertTrue(
                        "$name: clase $i esperada=${expected[i]} actual=${actual[i]}",
                        abs(expected[i] - actual[i]) <= tolerance,
                    )
                }

                // determinismo entre ejecuciones sucesivas
                val again = classifier.classify(input)
                for (i in actual.indices) {
                    assertTrue("$name: no determinista en clase $i",
                        abs(actual[i] - again[i]) <= tolerance)
                }
            }
        }
    }
}
