package com.helpi.conversation.session

import android.content.Context
import com.helpi.conversation.audio.AudioArbiter
import com.helpi.conversation.audio.AudioArbiterListener
import com.helpi.conversation.audio.AudioArbiterState
import com.helpi.conversation.audio.OfflineSpeechOutput
import com.helpi.conversation.chat.VoiceState

/** Salida de voz: árbitro de audio, síntesis sin conexión y compuerta del micrófono. */
internal class VoiceChannel(
    private val context: Context,
    private val loop: SessionLoop,
    private val data: SessionData,
    private val input: SpeechInputChannel,
    private val publish: () -> Unit
) {
    private var output: OfflineSpeechOutput? = null
    private val arbiter = AudioArbiter(GateListener())

    val isIdle: Boolean get() = arbiter.state() == AudioArbiterState.IDLE

    /** Abre la síntesis; la disponibilidad llega después, por callback. */
    fun prepare() {
        val gen = loop.generation
        output = OfflineSpeechOutput(
            context,
            onReady = { desc -> loop.submit(gen) { onReady(desc) } },
            onSpoken = { id ->
                loop.submit(gen) {
                    if (data.hasTurn(id)) data.conversation.updateVoice(id, VoiceState.SPOKEN)
                    data.audioState = AudioChannelState.GUARDA_ACUSTICA
                    arbiter.ttsFinished(id, loop.now())
                    publish()
                }
            },
            onFailed = { id ->
                loop.submit(gen) {
                    if (data.hasTurn(id)) data.conversation.updateVoice(id, VoiceState.NOT_SPOKEN)
                    arbiter.ttsFailed(id, loop.now())
                    publish()
                }
            }
        )
    }

    private fun onReady(description: String?) {
        data.capabilities = data.capabilities.copy(
            tts = description != null,
            ttsDetail =
            description ?: "sin voz española offline: la persona oyente no recibirá audio"
        )
        val completes = data.stateMachine.current() == SessionState.ACTIVA_LIMITADA &&
            data.targetActiveState == SessionState.ACTIVA
        if (description != null && completes) data.stateMachine.transition(SessionState.ACTIVA)
        publish()
    }

    /** Pone un texto en la cola de voz. */
    fun enqueue(turnId: Long, text: String) {
        arbiter.enqueue(turnId, text, loop.now())
    }

    /** Ofrece un turno a voz; sin voz instalada lo marca como no pronunciado y devuelve false. */
    fun offer(turnId: Long, text: String): Boolean {
        if (data.capabilities.tts) {
            enqueue(turnId, text)
        } else {
            data.conversation.updateVoice(turnId, VoiceState.NOT_SPOKEN)
        }
        return data.capabilities.tts
    }

    fun cancel(turnId: Long): Boolean = arbiter.cancel(turnId)

    fun hearingSpeechActive(active: Boolean) = arbiter.hearingSpeechActive(active, loop.now())

    /** Vencimientos, guarda acústica y watchdog del árbitro. */
    fun tick() {
        arbiter.tick(loop.now())
    }

    fun stopSpeaking() {
        output?.stop()
    }

    /** Descarta la cola: lo pendiente queda como no pronunciado. */
    fun discardPending() {
        data.conversation.ordered()
            .filter { it.voiceState() == VoiceState.PENDING }
            .forEach { data.conversation.updateVoice(it.id(), VoiceState.NOT_SPOKEN) }
        arbiter.reset()
    }

    fun close() {
        output?.close()
        output = null
        arbiter.reset()
    }

    /** El árbitro pide abrir o cerrar el micrófono para que la voz no se transcriba a sí misma. */
    private inner class GateListener : AudioArbiterListener {
        override fun requestCloseSttGate() {
            val gen = loop.generation
            data.audioState = AudioChannelState.CERRANDO_ENTRADA_STT
            input.setGate(open = false)
            // confirmación explícita: la compuerta quedó cerrada
            loop.submit(gen) {
                if (data.isActive && arbiter.state() == AudioArbiterState.WAITING_GATE) {
                    data.audioState = AudioChannelState.TTS_HABLANDO
                    arbiter.sttGateClosed(loop.now())
                    publish()
                }
            }
        }

        override fun openSttGate() {
            val listening = data.microphoneEnabled && data.isActive
            if (listening) input.setGate(open = true)
            data.audioState = if (data.capabilities.stt && listening) {
                AudioChannelState.STT_ESCUCHANDO
            } else {
                AudioChannelState.STT_LISTO
            }
        }

        override fun speak(messageId: Long, text: String) {
            data.audioState = AudioChannelState.TTS_HABLANDO
            output?.speak(messageId, text)
        }

        override fun expired(messageId: Long) {
            if (data.hasTurn(messageId)) {
                data.conversation.updateVoice(messageId, VoiceState.NOT_SPOKEN)
            }
        }
    }
}
