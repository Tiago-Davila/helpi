package com.helpi.conversation.session

import com.helpi.conversation.chat.Conversation
import com.helpi.conversation.lsa.SignLanguage
import com.helpi.conversation.lsa.sequence.RecognitionMode
import com.helpi.conversation.vision.FramingEvaluator
import com.helpi.conversation.vision.SamplingProfile
import com.helpi.conversation.vision.SigningDistanceGuide
import com.helpi.conversation.vision.VisionMetrics

/**
 * Estado mutable que comparten los canales de la sesión. Solo se toca desde
 * [SessionLoop]. La conversación vive en memoria y se descarta al cerrar.
 */
internal class SessionData(initialLanguage: SignLanguage) {
    val conversation = Conversation()
    val thresholds = ConfidenceThresholds(initialLanguage)
    var stateMachine = SessionStateMachine()
    var signLanguage = initialLanguage
    var capabilities = SessionCapabilities()
    var notice: String? = null
    var audioState = AudioChannelState.NO_DISPONIBLE
    var microphoneEnabled = true

    var visualState = VisualChannelState.NO_DISPONIBLE
    var cameraEnabled = true
    var recognitionMode = RecognitionMode.SINGLE_SIGN
    var framing = FramingEvaluator.Issue.SIN_PERSONA
    var signingDistance = SigningDistanceGuide.State.UNKNOWN
    var lastRecognition: RecognitionFeedback? = null
    var visionMetrics = VisionMetrics()
    var samplingProfile = SamplingProfile.IDLE
    var lastVisionTimestampMs = 0L

    /** Cambia cada vez que se descarta la captura: invalida la clasificación en curso. */
    var visionRevision = 0

    val isActive: Boolean
        get() = stateMachine.current() == SessionState.ACTIVA ||
            stateMachine.current() == SessionState.ACTIVA_LIMITADA

    /** La sesión ya pasó por la preparación y todavía no se cerró. */
    val isPrepared: Boolean
        get() = stateMachine.current() !in setOf(
            SessionState.INICIO,
            SessionState.CERRANDO,
            SessionState.CERRADA
        )

    /** Estado activo que corresponde a las capacidades disponibles. */
    val targetActiveState: SessionState
        get() = if (capabilities.vision && capabilities.stt && capabilities.tts) {
            SessionState.ACTIVA
        } else {
            SessionState.ACTIVA_LIMITADA
        }

    /** Si el motor visual elegido está disponible. */
    val activeVisionAvailable: Boolean
        get() = when (recognitionMode) {
            RecognitionMode.SINGLE_SIGN -> capabilities.vision
            RecognitionMode.PHRASE_EXPERIMENTAL -> capabilities.phraseVision
        }

    /** Si el resultado de una inferencia pedida en esa revisión todavía vale. */
    fun acceptsVisionResult(revision: Int): Boolean =
        isActive && cameraEnabled && revision == visionRevision

    /** Deja el canal visual buscando encuadre, o no disponible. */
    fun restartVisualState(available: Boolean) {
        visualState = if (available) {
            VisualChannelState.BUSCANDO_ENCUADRE
        } else {
            VisualChannelState.NO_DISPONIBLE
        }
    }

    fun hasTurn(id: Long): Boolean = runCatching { conversation.get(id) }.isSuccess

    /** Vuelve al estado inicial para preparar otra sesión después de un cierre. */
    fun resetAfterClose() {
        stateMachine = SessionStateMachine()
        visualState = VisualChannelState.NO_DISPONIBLE
        audioState = AudioChannelState.NO_DISPONIBLE
        capabilities = SessionCapabilities()
        lastRecognition = null
        recognitionMode = RecognitionMode.SINGLE_SIGN
        lastVisionTimestampMs = 0L
        notice = null
        microphoneEnabled = true
        cameraEnabled = true
    }
}
