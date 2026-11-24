package com.helpi.conversation.session

import com.helpi.conversation.observation.NoResultCause
import com.helpi.conversation.vision.FrameObservation
import com.helpi.conversation.vision.FramingEvaluator
import com.helpi.conversation.vision.LandmarkFrame
import com.helpi.conversation.vision.SamplingProfile
import com.helpi.conversation.vision.SegmentEvent
import com.helpi.conversation.vision.SegmenterConfig
import com.helpi.conversation.vision.SignSegmenter

/** Modo seña: separa el flujo de cuadros en segmentos y los manda a clasificar. */
internal class SignCapture(
    private val loop: SessionLoop,
    private val data: SessionData,
    private val outlet: RecognitionOutlet,
    private val sentence: SentenceChannel,
    private val recognition: SignRecognition
) {
    private var segmenter = SignSegmenter(SegmenterConfig.defaults())
    private val frames = SegmentFrames()

    /** Cuadros de la ventana en curso, pre-roll incluido. */
    val bufferedFrames: Int get() = frames.size

    /** Tasa de captura que pide el estado del segmentador. */
    val samplingProfile: SamplingProfile
        get() = when {
            segmenter.isCapturing || segmenter.isStartCandidate -> SamplingProfile.ACTIVE
            segmenter.isArmed -> SamplingProfile.ARMED
            else -> SamplingProfile.IDLE
        }

    /** Procesa un cuadro y avanza la oración si no hay ninguna seña en curso. */
    fun process(frame: LandmarkFrame, observation: FrameObservation) {
        frames.add(CapturedFrame.of(frame), segmenter.isCapturing)
        handle(segmenter.process(observation))
        if (!recognition.busy && !segmenter.isCapturing) {
            sentence.closeAfterPause(frame.timestampMs)
        }
    }

    /** Olvida el segmento en curso. */
    fun reset() {
        segmenter = SignSegmenter(SegmenterConfig.defaults())
        frames.clear()
    }

    private fun handle(event: SegmentEvent) {
        when (event.type) {
            SegmentEvent.Type.ARMED -> data.visualState = VisualChannelState.ARMADO
            SegmentEvent.Type.STARTED -> onStarted(event.segmentStartMs)
            SegmentEvent.Type.ENDED -> onEnded(event.segmentStartMs, event.segmentEndMs)
            SegmentEvent.Type.ABORTED -> onAborted(event.abortReason)
            SegmentEvent.Type.NONE -> waitForRest()
        }
    }

    private fun onStarted(startMs: Long) {
        outlet.segmentStarted(startMs)
        data.visionMetrics =
            data.visionMetrics.copy(segmentsStarted = data.visionMetrics.segmentsStarted + 1)
        data.visualState = VisualChannelState.CAPTURANDO_SENA
        frames.startFrom(startMs)
    }

    private fun onEnded(startMs: Long, endMs: Long) {
        data.visionMetrics =
            data.visionMetrics.copy(segmentsCompleted = data.visionMetrics.segmentsCompleted + 1)
        data.visualState = VisualChannelState.REARMANDO
        recognition.classify(startMs, endMs, frames.take(startMs, endMs))
    }

    private fun onAborted(reason: SegmentEvent.AbortReason) {
        when (reason) {
            SegmentEvent.AbortReason.TOO_SHORT -> outlet.noResult(NoResultCause.SEGMENTO_CORTO)
            SegmentEvent.AbortReason.TOO_LONG -> outlet.noResult(NoResultCause.SEGMENTO_LARGO)
            SegmentEvent.AbortReason.TRACKING_LOST ->
                outlet.noResult(NoResultCause.SEGUIMIENTO_PERDIDO)
            SegmentEvent.AbortReason.NONE -> Unit
        }
        data.visionMetrics =
            data.visionMetrics.copy(segmentsAborted = data.visionMetrics.segmentsAborted + 1)
        data.visualState = VisualChannelState.REARMANDO
        frames.discardCurrent()
        if (reason == SegmentEvent.AbortReason.TRACKING_LOST && outlet.reachesConversation) {
            data.visualState = VisualChannelState.CALIDAD_INSUFICIENTE
            data.conversation.systemNote(
                "No vi bien tus manos. Dejá las manos quietas un momento y volvé a intentar.",
                loop.now()
            )
        }
    }

    /** Con el encuadre resuelto, el paso siguiente es dejar las manos quietas. */
    private fun waitForRest() {
        if (data.visualState == VisualChannelState.BUSCANDO_ENCUADRE &&
            data.framing == FramingEvaluator.Issue.OK
        ) {
            data.visualState = VisualChannelState.ESPERANDO_REPOSO
        }
    }
}
