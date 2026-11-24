package com.helpi.conversation.session

import com.helpi.conversation.lsa.sequence.RecognitionMode
import com.helpi.conversation.vision.FramingEvaluator
import com.helpi.conversation.vision.LandmarkFrame
import com.helpi.conversation.vision.ObservationFactory
import com.helpi.conversation.vision.SigningDistanceGuide

/** Entrada de la cámara: encuadre, distancia y reparto de cada cuadro al modo elegido. */
internal class VisualChannel(
    private val data: SessionData,
    private val signs: SignCapture,
    private val phrase: PhraseChannel,
    private val publish: () -> Unit
) {
    private val framingEvaluator = FramingEvaluator()
    private val distanceGuide = SigningDistanceGuide()
    private val observationFactory = ObservationFactory()

    fun process(frame: LandmarkFrame) {
        if (!data.activeVisionAvailable || !data.cameraEnabled || !data.isActive) return

        data.lastVisionTimestampMs = frame.timestampMs
        val observation = observationFactory.observe(frame)
        data.framing = framingEvaluator.evaluate(observation)
        data.signingDistance =
            distanceGuide.evaluate(frame.pose, frame.imageWidth, frame.imageHeight)
        // Ajusta la tasa sin tocar la ventana fija de 40 cuadros del modelo.
        when (data.recognitionMode) {
            RecognitionMode.SINGLE_SIGN -> {
                signs.process(frame, observation)
                data.samplingProfile = signs.samplingProfile
            }
            RecognitionMode.PHRASE_EXPERIMENTAL -> {
                phrase.process(frame)
                data.samplingProfile = phrase.samplingProfile
            }
        }
        publish()
    }

    /** Descarta el segmento y la clasificación en curso. */
    fun discardSegment() {
        data.visionRevision++
        signs.reset()
        observationFactory.reset()
    }

    /** Además del segmento, olvida la distancia medida. */
    fun discardCapture() {
        discardSegment()
        distanceGuide.reset()
        data.signingDistance = SigningDistanceGuide.State.UNKNOWN
    }
}
