package com.helpi.conversation.lsa.sequence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequenceAcceptancePolicyTest {

    private val policy = SequenceAcceptancePolicy()

    @Test
    fun `rechaza candidato vacío aunque el decoder haya terminado`() {
        val decision = policy.evaluate(
            SequenceTranslationCandidate("", listOf(2), terminatedByEos = true),
        )

        assertFalse(decision.accepted)
    }

    @Test
    fun `rechaza salida truncada sin EOS`() {
        val decision = policy.evaluate(
            SequenceTranslationCandidate("hola", listOf(4), terminatedByEos = false),
        )

        assertFalse(decision.accepted)
    }

    @Test
    fun `acepta solo un candidato completo y no vacío`() {
        val decision = policy.evaluate(
            SequenceTranslationCandidate("hola", listOf(4, 2), terminatedByEos = true),
        )

        assertTrue(decision.accepted)
    }
}
