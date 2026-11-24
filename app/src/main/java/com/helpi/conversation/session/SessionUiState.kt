package com.helpi.conversation.session

import com.helpi.conversation.chat.Turn
import com.helpi.conversation.lsa.SignLanguage
import com.helpi.conversation.lsa.sequence.PhraseCaptureState
import com.helpi.conversation.lsa.sequence.RecognitionMode
import com.helpi.conversation.lsa.sequence.SequenceTranslationCandidate
import com.helpi.conversation.vision.FramingEvaluator
import com.helpi.conversation.vision.SamplingProfile
import com.helpi.conversation.vision.SigningDistanceGuide
import com.helpi.conversation.vision.VisionMetrics

/** Destino de lo reconocido: la conversación o solo el observador (protocolo de evaluación). */
enum class DeliveryMode {
    CONVERSATION,
    OBSERVER_ONLY
}

/** Qué canales tiene la sesión y, si falta alguno, por qué. */
data class SessionCapabilities(
    val keyboard: Boolean = true,
    val vision: Boolean = false,
    val visionDetail: String = "",
    val phraseVision: Boolean = false,
    val phraseVisionDetail: String = "",
    val stt: Boolean = false,
    val sttDetail: String = "",
    val tts: Boolean = false,
    val ttsDetail: String = ""
)

/** Último intento clasificado; las predicciones son informativas, nunca confirmadas. */
data class RecognitionFeedback(
    val confidence: Float,
    val threshold: Float,
    val accepted: Boolean,
    val gloss: String? = null,
    val predictions: List<RecognitionPrediction> = emptyList()
)

data class RecognitionPrediction(val label: String, val confidence: Float)

/**
 * Oración del modo seña armada pero no publicada: espera que la persona
 * sorda la confirme, la edite o la descarte.
 */
data class SentenceCandidate(val turnId: Long, val text: String, val glosses: String)

/** Foto inmutable de la sesión que consume la interfaz. */
data class SessionUiState(
    val session: SessionState = SessionState.INICIO,
    val visual: VisualChannelState = VisualChannelState.NO_DISPONIBLE,
    val audio: AudioChannelState = AudioChannelState.NO_DISPONIBLE,
    val framing: FramingEvaluator.Issue = FramingEvaluator.Issue.SIN_PERSONA,
    val signingDistance: SigningDistanceGuide.State = SigningDistanceGuide.State.UNKNOWN,
    val turns: List<Turn> = emptyList(),
    val capabilities: SessionCapabilities = SessionCapabilities(),
    val confidenceThreshold: Float = ConfidenceThresholds.DEFAULT,
    val signLanguage: SignLanguage = SignLanguage.LSA,
    val lastRecognition: RecognitionFeedback? = null,
    val visionMetrics: VisionMetrics = VisionMetrics(),
    val samplingProfile: SamplingProfile = SamplingProfile.IDLE,
    val recognitionMode: RecognitionMode = RecognitionMode.SINGLE_SIGN,
    val phraseCapture: PhraseCaptureState = PhraseCaptureState.UNAVAILABLE,
    val phraseCandidate: SequenceTranslationCandidate? = null,
    val sentenceCandidate: SentenceCandidate? = null,
    val notice: String? = null,
    val microphoneEnabled: Boolean = true,
    val cameraEnabled: Boolean = true
)
