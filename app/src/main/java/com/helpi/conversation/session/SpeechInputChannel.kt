package com.helpi.conversation.session

import android.Manifest
import android.content.Context
import com.helpi.conversation.audio.VoskModelStore
import com.helpi.conversation.audio.VoskSpeechSource
import com.helpi.conversation.chat.Speaker
import java.io.File

/** Canal de la persona oyente: voz a texto con Vosk y su turno en curso. */
internal class SpeechInputChannel(
    private val context: Context,
    private val loop: SessionLoop,
    private val data: SessionData,
    private val publish: () -> Unit
) {
    private var speech: VoskSpeechSource? = null
    private var partialTurnId = NO_TURN

    /** Avisa al árbitro que la persona oyente empezó o dejó de hablar. */
    var onSpeechActivity: (Boolean) -> Unit = {}

    /** Instala el modelo y abre el reconocedor. */
    fun prepare(): ChannelAvailability {
        if (!context.isGranted(Manifest.permission.RECORD_AUDIO)) {
            return ChannelAvailability(false, "permiso de micrófono denegado")
        }
        return when (val result = VoskModelStore.install(context)) {
            is VoskModelStore.Result.Ready -> open(result.modelDir)
            is VoskModelStore.Result.Missing -> ChannelAvailability(false, result.detail)
            is VoskModelStore.Result.Failed -> ChannelAvailability(false, result.detail)
        }
    }

    // Un modelo de voz dañado deja la sesión sin este canal, no la voltea.
    @Suppress("TooGenericExceptionCaught")
    private fun open(modelDir: File): ChannelAvailability = try {
        val gen = loop.generation
        speech = VoskSpeechSource(
            modelDir = modelDir,
            onPartial = { text -> loop.submit(gen) { onPartial(text) } },
            onFinal = { text -> loop.submit(gen) { onFinal(text) } },
            onSpeechActivity = { active ->
                loop.submit(gen) {
                    if (data.isActive && data.microphoneEnabled) {
                        onSpeechActivity(active)
                        if (active) data.audioState = AudioChannelState.STT_TRANSCRIBIENDO
                    }
                }
            },
            onError = { msg ->
                loop.submit(gen) {
                    data.notice = msg
                    publish()
                }
            }
        )
        ChannelAvailability(true)
    } catch (e: Exception) {
        ChannelAvailability(false, "Vosk no inicializó: ${e.message}")
    }

    fun start() {
        speech?.start()
    }

    fun stop() {
        speech?.stop()
    }

    /** Abre o cierra la compuerta sin detener el reconocedor. */
    fun setGate(open: Boolean) {
        if (open) speech?.openGate() else speech?.closeGate()
    }

    /** Marca como incompleto el turno que se estaba transcribiendo. */
    fun interruptPartial() {
        if (partialTurnId != NO_TURN) {
            data.conversation.markIncomplete(partialTurnId)
            partialTurnId = NO_TURN
        }
    }

    fun close() {
        speech?.close()
        speech = null
        partialTurnId = NO_TURN
    }

    private fun onPartial(text: String) {
        if (!data.isActive || !data.microphoneEnabled) return
        data.conversation.updatePartial(openTurn(), text)
        data.audioState = AudioChannelState.STT_TRANSCRIBIENDO
        publish()
    }

    private fun onFinal(text: String) {
        if (!data.isActive || !data.microphoneEnabled) return
        data.conversation.finalize(openTurn(), text)
        partialTurnId = NO_TURN
        data.audioState = AudioChannelState.STT_ESCUCHANDO
        publish()
    }

    /** Turno en transcripción, o uno nuevo si no había. */
    private fun openTurn(): Long {
        if (partialTurnId == NO_TURN) {
            partialTurnId = data.conversation.open(Speaker.HEARING, loop.now()).id()
        }
        return partialTurnId
    }

    private companion object {
        const val NO_TURN = -1L
    }
}
