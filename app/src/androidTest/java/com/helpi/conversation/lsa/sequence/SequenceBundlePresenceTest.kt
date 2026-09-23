package com.helpi.conversation.lsa.sequence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke del paquete experimental. No habilita el modo frase: solamente
 * verifica sus artefactos cuando el entrenamiento/export remoto los entregue.
 */
@RunWith(AndroidJUnit4::class)
class SequenceBundlePresenceTest {

    @Test
    fun paqueteExperimentalSeValidaComoUnidad() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        when (val result = SequenceModelBundle.load(context)) {
            is SequenceModelBundleResult.Ready -> {
                assertEquals("B,T,126", result.bundle.manifest.inputLayout)
                assertEquals("B,126,T", result.bundle.manifest.encoderInputLayout)
                assertEquals("B,256,T", result.bundle.manifest.decoderMemoryLayout)
            }
            is SequenceModelBundleResult.Missing -> {
                assumeTrue("paquete LSA-T ausente (${result.asset}): etapa 4 pendiente", false)
            }
            is SequenceModelBundleResult.Invalid -> {
                fail("paquete LSA-T inválido: ${result.cause}")
            }
        }
    }
}
