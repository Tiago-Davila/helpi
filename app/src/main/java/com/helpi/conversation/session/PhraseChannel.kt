package com.helpi.conversation.session

import android.Manifest
import android.content.Context
import com.helpi.conversation.lsa.sequence.PhraseCaptureState
import com.helpi.conversation.lsa.sequence.RecognitionMode
import com.helpi.conversation.lsa.sequence.SequenceAcceptancePolicy
import com.helpi.conversation.lsa.sequence.SequenceAppendResult
import com.helpi.conversation.lsa.sequence.SequenceCapture
import com.helpi.conversation.lsa.sequence.SequenceCaptureBuffer
import com.helpi.conversation.lsa.sequence.SequenceTranslationCandidate
import com.helpi.conversation.vision.FramingEvaluator
import com.helpi.conversation.vision.LandmarkFrame
import com.helpi.conversation.vision.SamplingProfile

/** Modo frase experimental: captura explícita, traducción y candidato por confirmar. */
// Una operación por cada paso de la captura; partirlas separaría un mismo ciclo.
@Suppress("TooManyFunctions")
internal class PhraseChannel(
    private val context: Context,
    private val loop: SessionLoop,
    private val data: SessionData,
    private val publish: () -> Unit
) {
    /** Motivo por el que una captura no se traduce; [visual] si además cambia el canal. */
    private class CaptureProblem(val notice: String, val visual: VisualChannelState? = null)

    private val buffer = SequenceCaptureBuffer()
    private val policy = SequenceAcceptancePolicy()
    private val translation = PhraseTranslation(context)
    private var candidateStartMs = NO_START

    var state = PhraseCaptureState.UNAVAILABLE
        private set
    var candidate: SequenceTranslationCandidate? = null
        private set

    /** Tasa de captura que pide el modo frase. */
    val samplingProfile: SamplingProfile
        get() = if (state == PhraseCaptureState.CAPTURING) {
            SamplingProfile.ACTIVE
        } else {
            SamplingProfile.IDLE
        }

    /** Carga el modelo de frases; su ausencia nunca inhabilita a Eva. */
    fun prepare(): ChannelAvailability {
        if (!context.isGranted(Manifest.permission.CAMERA)) {
            return ChannelAvailability(false, "permiso de cámara denegado")
        }
        return translation.load().also { setReady(it.available) }
    }

    /** Agrega un cuadro a la captura, o acompaña el encuadre si no hay ninguna. */
    fun process(frame: LandmarkFrame) {
        if (state != PhraseCaptureState.CAPTURING) {
            if (data.visualState == VisualChannelState.BUSCANDO_ENCUADRE &&
                data.framing == FramingEvaluator.Issue.OK
            ) {
                data.visualState = VisualChannelState.ESPERANDO_REPOSO
            }
            return
        }
        when (buffer.append(frame.timestampMs, frame.leftHand, frame.rightHand)) {
            SequenceAppendResult.ACCEPTED -> data.visualState = VisualChannelState.CAPTURANDO_SENA
            SequenceAppendResult.TOO_LONG -> {
                buffer.cancel()
                state = PhraseCaptureState.READY
                data.visualState = VisualChannelState.REARMANDO
                data.notice = "La captura superó los 12 segundos. Empezá la frase de nuevo."
            }
            SequenceAppendResult.NOT_CAPTURING -> state = PhraseCaptureState.READY
        }
    }

    /** Inicia una captura de frase; nunca se activa automáticamente. */
    fun start() {
        if (!canCapture() || state != PhraseCaptureState.READY) return
        val start = visionNow()
        candidate = null
        candidateStartMs = NO_START
        buffer.start(start)
        state = PhraseCaptureState.CAPTURING
        data.samplingProfile = SamplingProfile.ACTIVE
        data.visualState = VisualChannelState.CAPTURANDO_SENA
        data.notice = null
        publish()
    }

    /** Termina la captura y deja el texto como candidato, sin publicarlo. */
    fun finish() {
        if (state != PhraseCaptureState.CAPTURING) return
        val capture = buffer.stop(visionNow())
        data.samplingProfile = SamplingProfile.IDLE
        val problem = problemOf(capture)
        if (capture == null || problem != null) {
            state = PhraseCaptureState.READY
            problem?.visual?.let { data.visualState = it }
            data.notice = problem?.notice
        } else {
            translate(capture)
        }
        publish()
    }

    private fun problemOf(capture: SequenceCapture?): CaptureProblem? = when {
        capture == null || capture.frames.size < MIN_FRAMES -> CaptureProblem(
            "La frase fue demasiado corta. Volvé a intentarlo.",
            VisualChannelState.REARMANDO
        )
        capture.validFrameRatio < MIN_VALID_RATIO -> CaptureProblem(
            "No vi suficientes manos durante la frase. Volvé a intentarlo.",
            VisualChannelState.CALIDAD_INSUFICIENTE
        )
        !translation.isFree -> CaptureProblem("La frase anterior todavía se está procesando.")
        else -> null
    }

    private fun translate(capture: SequenceCapture) {
        val tensor = runCatching { capture.buildInputTensor() }.getOrElse { error ->
            state = PhraseCaptureState.READY
            data.notice = "No se pudo preparar la frase: ${error.message ?: "entrada inválida"}"
            return
        }
        val gen = loop.generation
        val revision = data.visionRevision
        state = PhraseCaptureState.PROCESSING
        data.visualState = VisualChannelState.REARMANDO
        translation.translate(tensor) { result ->
            loop.submit(gen) {
                translation.finished()
                if (data.acceptsVisionResult(revision) && isSelected()) {
                    apply(result, capture.startTimestampMs)
                    publish()
                }
            }
        }
    }

    private fun apply(result: Result<SequenceTranslationCandidate>, startMs: Long) {
        result.fold(
            onSuccess = { translated ->
                val decision = policy.evaluate(translated)
                if (decision.accepted) {
                    candidate = translated
                    candidateStartMs = startMs
                    state = PhraseCaptureState.CANDIDATE
                } else {
                    state = PhraseCaptureState.READY
                    data.notice = decision.reason
                }
            },
            onFailure = { error ->
                state = PhraseCaptureState.READY
                data.notice = "No se pudo ejecutar el modelo de frases: " +
                    (error.message ?: "error desconocido")
            }
        )
    }

    /** Momento en que empezó la captura del candidato. */
    fun candidateStart(): Long = candidateStartMs.takeIf { it >= 0L } ?: loop.now()

    /** Olvida el candidato. */
    fun clearCandidate(available: Boolean) {
        candidate = null
        candidateStartMs = NO_START
        setReady(available)
    }

    /** Cancela la captura y olvida el candidato. */
    fun discard(available: Boolean) {
        buffer.cancel()
        clearCandidate(available)
    }

    /** La cámara falló: no se puede capturar. */
    fun markUnavailable() = setReady(available = false)

    /** Deja el canal listo para capturar, o no disponible. */
    private fun setReady(available: Boolean) {
        state = if (available) PhraseCaptureState.READY else PhraseCaptureState.UNAVAILABLE
    }

    fun isSelected(): Boolean = data.recognitionMode == RecognitionMode.PHRASE_EXPERIMENTAL

    fun close() {
        translation.close()
        discard(available = false)
    }

    fun shutdown() = translation.shutdown()

    private fun canCapture(): Boolean =
        data.isActive && data.cameraEnabled && data.capabilities.phraseVision && isSelected()

    /** Reloj de la cámara, o el de la sesión si todavía no llegó ningún cuadro. */
    private fun visionNow(): Long = data.lastVisionTimestampMs.takeIf { it > 0L } ?: loop.now()

    private companion object {
        const val NO_START = -1L
        const val MIN_FRAMES = 3
        const val MIN_VALID_RATIO = 0.10f
    }
}
