package com.helpi.conversation.lsa.sequence

/** Decisión estructural del motor experimental, independiente de Eva. */
data class SequenceAcceptanceDecision(
    val accepted: Boolean,
    val reason: String? = null,
)

/**
 * Compuerta conservadora para un decoder cuya confianza todavía no está
 * calibrada. No inventa una probabilidad: solo permite candidatos completos,
 * no vacíos y terminados con EOS. La confirmación humana sigue siendo
 * obligatoria después de esta compuerta.
 */
class SequenceAcceptancePolicy {
    fun evaluate(candidate: SequenceTranslationCandidate): SequenceAcceptanceDecision {
        if (candidate.text.isBlank() || candidate.tokenIds.isEmpty()) {
            return SequenceAcceptanceDecision(
                accepted = false,
                reason = "No pude formar una frase con esa captura.",
            )
        }
        if (!candidate.terminatedByEos) {
            return SequenceAcceptanceDecision(
                accepted = false,
                reason = "La frase quedó incompleta. Volvé a intentarlo.",
            )
        }
        return SequenceAcceptanceDecision(accepted = true)
    }
}
