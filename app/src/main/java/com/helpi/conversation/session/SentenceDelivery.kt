package com.helpi.conversation.session

import com.helpi.conversation.chat.VoiceState
import com.helpi.conversation.lsa.PhraseAssets
import com.helpi.conversation.phrase.PhraseBuilder
import com.helpi.conversation.phrase.RecognizedSign
import java.util.ArrayDeque

/** Convierte las señas de una oración cerrada en frase y la publica, o la deja por confirmar. */
internal class SentenceDelivery(private val data: SessionData, private val voice: VoiceChannel) {
    /** Oraciones por confirmar, en el orden en que se cerraron; se muestra la primera. */
    private val pending = ArrayDeque<SentenceCandidate>()

    /** Léxico y plantillas de la lengua cargada. */
    var phraseAssets: PhraseAssets = withoutModel()

    val candidate: SentenceCandidate? get() = pending.firstOrNull()

    /** Sin modelo cargado no hay con qué armar frases: se muestran glosas sueltas. */
    fun forgetPhraseAssets() {
        phraseAssets = withoutModel()
    }

    /** Glosas de las señas, con los huecos que correspondan al umbral vigente. */
    fun glosses(signs: List<RecognizedSign>): String =
        phraseAssets.builder(data.thresholds.current).build(signs).glosses().joinToString(" ")

    /** Arma la frase, la publica con sus glosas y la ofrece a voz si no tiene huecos. */
    fun deliver(turnId: Long, signs: List<RecognizedSign>) {
        val result = phraseAssets.builder(data.thresholds.current).build(signs)
        val glosses = result.glosses().joinToString(" ")
        if (SentenceRules.needsConfirmation(data.signLanguage, signs)) {
            // La frase tiene interpretación: no llega al oyente sin que la persona la confirme.
            pending.addLast(SentenceCandidate(turnId, result.text(), glosses))
        } else {
            publishAndSpeak(turnId, result.text(), glosses)
        }
    }

    /** Publica la oración en espera, con el texto editado si la persona lo cambió. */
    fun confirm(editedText: String?) {
        val candidate = pending.pollFirst() ?: return
        val text = editedText?.trim()?.takeIf { it.isNotEmpty() } ?: candidate.text
        if (data.hasTurn(candidate.turnId)) {
            publishAndSpeak(candidate.turnId, text, candidate.glosses)
        }
    }

    /** Descarta la oración en espera: no se publica ni se vocaliza. */
    fun reject() {
        val candidate = pending.pollFirst() ?: return
        if (data.hasTurn(candidate.turnId)) data.conversation.markRejected(candidate.turnId)
        data.notice = "Frase descartada."
    }

    fun clear() = pending.clear()

    /** Publica la oración con sus glosas y la ofrece a voz si no tiene huecos. */
    private fun publishAndSpeak(turnId: Long, text: String, glosses: String) {
        data.conversation.publishSentence(turnId, text, glosses)
        if (PhraseBuilder.GAP in glosses.split(" ")) {
            data.conversation.updateVoice(turnId, VoiceState.NOT_SPOKEN)
        } else {
            voice.offer(turnId, text)
        }
    }

    private companion object {
        fun withoutModel(): PhraseAssets = PhraseAssets.Disabled("modelo LSA no cargado")
    }
}
