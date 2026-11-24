package com.helpi.conversation.session

import com.helpi.conversation.chat.Speaker
import com.helpi.conversation.chat.TurnState
import com.helpi.conversation.phrase.RecognizedSign
import com.helpi.conversation.phrase.SentenceAssembler

/** Oración del modo seña en curso: junta señas en una burbuja hasta que una pausa la cierra. */
internal class SentenceChannel(
    private val data: SessionData,
    private val delivery: SentenceDelivery
) {
    private val sentence = SentenceAssembler(PAUSE_MS, PAUSE_AFTER_FAILURE_MS)

    /** Burbuja de la oración en curso; [NO_TURN] si todavía no hubo ningún intento. */
    private var turnId = NO_TURN

    /** Turno de la oración en curso, o una reserva nueva si es el primer intento. */
    fun turn(startMs: Long): Long {
        if (!hasOpenTurn()) turnId = data.conversation.open(Speaker.DEAF, startMs).id()
        return turnId
    }

    /** Suma una seña (si es null solo extiende la pausa) y muestra las glosas aceptadas, sin voz. */
    fun add(sign: RecognizedSign?, segmentEndMs: Long) {
        if (!hasOpenTurn()) return
        if (sign == null) sentence.noteFailure(segmentEndMs) else sentence.add(sign, segmentEndMs)
        if (sentence.isEmpty) {
            // sin señas aceptadas no queda burbuja vacía colgando
            abandon(turnId)
        } else {
            data.conversation.updatePartial(turnId, delivery.glosses(sentence.signs()))
        }
    }

    /** Un intento que no llegó a decisión solo se descarta si la oración seguía vacía. */
    fun abandon(abandonedId: Long) {
        if (!data.hasTurn(abandonedId)) return
        if (data.conversation.get(abandonedId).state() != TurnState.PENDING) return
        data.conversation.markRejected(abandonedId)
        if (abandonedId == turnId) turnId = NO_TURN
    }

    /** Pesos del contexto previo para la próxima seña. */
    fun contextWeights(): FloatArray? = delivery.phraseAssets.contextWeights(sentence.signs())

    /** Cierra la oración si pasó la pausa sin señas nuevas. */
    fun closeAfterPause(nowMs: Long) {
        if (sentence.pauseElapsed(nowMs)) close()
    }

    /** Entrega la oración en curso como frase; la reserva vacía queda para su intento. */
    fun close() {
        if (sentence.isEmpty || !hasOpenTurn()) {
            reset()
            return
        }
        val closedId = turnId
        turnId = NO_TURN
        delivery.deliver(closedId, sentence.close())
    }

    fun reset() {
        sentence.reset()
        turnId = NO_TURN
    }

    private fun hasOpenTurn(): Boolean = turnId >= 0 && data.hasTurn(turnId)

    companion object {
        private const val NO_TURN = -1L

        /** Sin señas nuevas durante este tiempo, la oración se da por terminada. */
        const val PAUSE_MS = 4_000L

        /** Tras una seña no reconocida se espera más, para dar tiempo a repetirla. */
        const val PAUSE_AFTER_FAILURE_MS = 6_000L
    }
}
