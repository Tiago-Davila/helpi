package com.helpi.conversation.audio

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.Closeable
import java.io.File

/**
 * STT offline continuo con Vosk. Micrófono siempre adquirido mientras la
 * sesión está activa; la compuerta ([gate]) descarta la entrada durante el
 * TTS y su guarda acústica: ese audio no se decodifica ni se guarda.
 *
 * Eventos hacia el coordinador: parciales (reemplazan la misma burbuja),
 * finales y actividad de voz para el árbitro.
 */
class VoskSpeechSource(
    modelDir: File,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onSpeechActivity: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) : Closeable {

    private val model = Model(modelDir.absolutePath)
    private val recognizer = Recognizer(model, SAMPLE_RATE)
    private var service: SpeechService? = null

    @Volatile
    private var gateOpen = false

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            if (!gateOpen) return
            val text = extract(hypothesis, "partial") ?: return
            if (text.isNotBlank()) {
                onSpeechActivity(true)
                onPartial(text)
            }
        }

        override fun onResult(hypothesis: String?) {
            if (!gateOpen) return
            val text = extract(hypothesis, "text") ?: return
            onSpeechActivity(false)
            if (text.isNotBlank()) {
                onFinal(text)
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            onResult(hypothesis)
        }

        override fun onError(exception: Exception?) {
            onError("STT: ${exception?.message ?: "error desconocido"}")
        }

        override fun onTimeout() {
            onSpeechActivity(false)
        }
    }

    fun start() {
        if (service != null) return
        service = SpeechService(recognizer, SAMPLE_RATE).also {
            it.startListening(listener)
        }
        gateOpen = true
    }

    /**
     * Cierra la compuerta: la entrada se descarta y el estado del
     * reconocedor se reinicia para que nada cruce la frontera.
     * Devuelve cuando la compuerta está efectivamente cerrada
     * (confirmación para AudioArbiter.sttGateClosed).
     */
    fun closeGate() {
        gateOpen = false
        recognizer.reset()
    }

    /** Reabre la compuerta tras la guarda acústica, con buffer limpio. */
    fun openGate() {
        recognizer.reset()
        gateOpen = true
    }

    override fun close() {
        gateOpen = false
        service?.stop()
        service?.shutdown()
        service = null
        recognizer.close()
        model.close()
    }

    private fun extract(hypothesis: String?, field: String): String? =
        try {
            hypothesis?.let { JSONObject(it).optString(field, "") }
        } catch (_: Exception) {
            null
        }

    companion object {
        const val SAMPLE_RATE = 16000f
    }
}
