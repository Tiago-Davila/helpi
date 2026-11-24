package com.helpi.conversation.session

import android.content.Context
import com.helpi.conversation.lsa.sequence.SequenceModelBundle
import com.helpi.conversation.lsa.sequence.SequenceModelBundleResult
import com.helpi.conversation.lsa.sequence.SequenceTranslationCandidate
import com.helpi.conversation.lsa.sequence.SequenceTranslator
import java.util.concurrent.Executors

/** Modelo experimental de frases y su executor de inferencia. */
internal class PhraseTranslation(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private var translator: SequenceTranslator? = null
    private var busy = false

    /** Hay modelo y no está procesando otra frase. */
    val isFree: Boolean get() = translator != null && !busy

    /** Carga el paquete de frases; devuelve si hay canal y por qué no. */
    fun load(): ChannelAvailability = when (val result = SequenceModelBundle.load(context)) {
        is SequenceModelBundleResult.Ready -> open(result.bundle)
        is SequenceModelBundleResult.Missing ->
            ChannelAvailability(false, "modo frase pendiente: falta ${result.asset}")
        is SequenceModelBundleResult.Invalid -> ChannelAvailability(false, result.cause)
    }

    // LiteRT informa un paquete incompatible con excepciones de varios tipos.
    @Suppress("TooGenericExceptionCaught")
    private fun open(bundle: SequenceModelBundle): ChannelAvailability = try {
        translator = SequenceTranslator(bundle)
        ChannelAvailability(true)
    } catch (e: Exception) {
        translator = null
        ChannelAvailability(false, "paquete de frases incompatible: ${e.message}")
    }

    /** Traduce fuera del executor de la sesión; quien recibe el resultado llama a [finished]. */
    fun translate(tensor: FloatArray, onResult: (Result<SequenceTranslationCandidate>) -> Unit) {
        val loaded = translator ?: return
        busy = true
        executor.execute { onResult(runCatching { loaded.translate(tensor) }) }
    }

    fun finished() {
        busy = false
    }

    /** Suelta el modelo; el intérprete se cierra después de la inferencia en curso. */
    fun close() {
        translator?.let { closing -> executor.execute { closing.close() } }
        translator = null
        busy = false
    }

    fun shutdown() = executor.shutdown()
}
