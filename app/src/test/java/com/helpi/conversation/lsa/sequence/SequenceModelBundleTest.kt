package com.helpi.conversation.lsa.sequence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SequenceModelBundleTest {

    @Test
    fun `manifiesto experimental valida contrato 75x126`() {
        val manifest = manifest()

        manifest.validateContract()

        assertEquals("lsa-t-seq2seq-v1", manifest.artifactVersion)
        assertEquals(64, manifest.maxTokens)
        assertEquals(5_981, manifest.vocabularySize)
    }

    @Test
    fun `manifiesto con muestreo float se rechaza`() {
        val invalid = manifest().copy(
            temporalDescription = "uniform linspace; zero-pad at end to 75 frames",
        )

        try {
            invalid.validateContract()
            throw AssertionError("se esperaba rechazar el muestreo float")
        } catch (_: IllegalArgumentException) {
            // esperado
        }
    }

    @Test
    fun `vocabulario decodifica y omite tokens especiales`() {
        val vocabulary = SequenceVocabulary.fromTokens(
            tokens = listOf("<PAD>", "<BOS>", "<EOS>", "<UNK>", "hola", "mundo"),
            bosId = 1,
            eosId = 2,
            padId = 0,
            unkId = 3,
        )

        assertEquals("hola mundo", vocabulary.decode(listOf(1, 4, 5, 2)))
        assertEquals(null, vocabulary.token(99))
        assertTrue(vocabulary.size == 6)
    }

    private fun manifest() = SequenceModelManifest(
        artifactVersion = "lsa-t-seq2seq-v1",
        encoderFile = "encoder_int8.tflite",
        encoderSha256 = "encoder",
        decoderFile = "decoder_int8.tflite",
        decoderSha256 = "decoder",
        vocabularyFile = "vocab.json",
        vocabularySha256 = "vocab",
        vocabularySize = 5_981,
        inputLayout = "B,T,126",
        encoderInputLayout = "B,126,T",
        decoderMemoryLayout = "B,256,T",
        temporalDescription = "integer floor sampling idx[i]=(i*(T-1))//(75-1); zero-pad at end to 75 frames",
        bosId = 1,
        eosId = 2,
        padId = 0,
        unkId = 3,
        maxTokens = 64,
    )
}
