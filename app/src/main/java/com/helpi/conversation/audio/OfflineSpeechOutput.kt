package com.helpi.conversation.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.io.Closeable
import java.util.Locale

/**
 * Salida de voz con el TTS nativo. Requisitos:
 * - voz en español que NO requiera red (todo corre en el dispositivo);
 * - preferencia es-AR y, si no hay, otra variante española anunciada;
 * - sin voz española offline, la salida queda inhabilitada con causa
 *   visible: no se usa voz online ni otro idioma automáticamente.
 *
 * Los callbacks de utterance vuelven como eventos al coordinador para el
 * arbitraje TTS–STT.
 */
class OfflineSpeechOutput(
    context: Context,
    private val onReady: (voiceDescription: String?) -> Unit,
    private val onSpoken: (messageId: Long) -> Unit,
    private val onFailed: (messageId: Long) -> Unit,
) : Closeable {

    private var tts: TextToSpeech? = null

    @Volatile
    private var available = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                onReady(null)
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            val voice = selectSpanishOfflineVoice(engine)
            if (voice == null) {
                onReady(null)
            } else {
                engine.voice = voice
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.toLongOrNull()?.let(onSpoken)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.toLongOrNull()?.let(onFailed)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.toLongOrNull()?.let(onFailed)
                    }
                })
                available = true
                onReady("${voice.name} (${voice.locale})")
            }
        }
    }

    val isAvailable: Boolean get() = available

    /** Pronuncia un mensaje; el id vuelve por onSpoken/onFailed. */
    fun speak(messageId: Long, text: String): Boolean {
        val engine = tts
        if (engine == null || !available) {
            onFailed(messageId)
            return false
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_ADD, null, messageId.toString())
        if (result != TextToSpeech.SUCCESS) {
            onFailed(messageId)
            return false
        }
        return true
    }

    fun stop() {
        tts?.stop()
    }

    override fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        available = false
    }

    private fun selectSpanishOfflineVoice(engine: TextToSpeech): Voice? {
        val voices = try {
            engine.voices ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
        val offlineSpanish = voices.filter {
            !it.isNetworkConnectionRequired && it.locale.language == "es"
        }
        // preferencia es-AR; si no, cualquier variante española offline
        return offlineSpanish.firstOrNull { it.locale == Locale("es", "AR") }
            ?: offlineSpanish.firstOrNull()
    }
}
