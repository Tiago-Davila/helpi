package com.helpi.conversation.session

import com.helpi.conversation.lsa.SignLanguage
import com.helpi.conversation.phrase.PhraseBuilder
import com.helpi.conversation.phrase.PhraseResult
import com.helpi.conversation.phrase.RecognizedSign
import com.helpi.conversation.phrase.SyntheticSigns
import com.helpi.conversation.vision.Pointing

/** Reglas puras de la oración del modo seña: qué entra, qué se confirma y qué se pronuncia. */
internal object SentenceRules {
    private val POINTED_SIGNS = mapOf(
        Pointing.Type.YO to SyntheticSigns.YO,
        Pointing.Type.USTED to SyntheticSigns.USTED,
        Pointing.Type.EL_ELLA to SyntheticSigns.EL_ELLA,
        Pointing.Type.CABEZA to SyntheticSigns.CABEZA,
        Pointing.Type.GARGANTA to SyntheticSigns.GARGANTA,
        Pointing.Type.ABDOMEN to SyntheticSigns.ABDOMEN
    )

    /** Texto que la voz dice para una oración: la frase, o nada si tiene huecos. */
    fun sentenceVoice(result: PhraseResult): String? =
        result.text().takeIf { PhraseBuilder.GAP !in result.glosses() }

    /**
     * Seña que entra a la oración por un segmento: el modelo gana si aceptó;
     * si no, un pronombre o una zona señalados; si tampoco, ninguna (hueco).
     */
    fun segmentSign(accepted: RecognizedSign?, pointing: Pointing): RecognizedSign? =
        accepted ?: POINTED_SIGNS[pointing.type()]
            ?.let { index -> SyntheticSigns.entry(index) }
            ?.let { entry -> RecognizedSign(entry.index(), entry.gloss(), 1f) }

    /**
     * Una oración se confirma antes de publicarse si contiene interpretación:
     * siempre en LSE (primera persona por defecto) y en cualquier lengua si
     * tiene una seña señalada (pronombre o zona). Una oración de LSA sin
     * señalamiento no.
     */
    fun needsConfirmation(language: SignLanguage, signs: List<RecognizedSign>): Boolean {
        val pointed = signs.any { SyntheticSigns.isSynthetic(it.classIndex()) }
        return language == SignLanguage.LSE || pointed
    }
}
