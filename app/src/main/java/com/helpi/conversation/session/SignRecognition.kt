package com.helpi.conversation.session

import com.helpi.conversation.keypoints.KeypointContract
import com.helpi.conversation.lsa.SignDecision
import com.helpi.conversation.observation.NoResultCause
import com.helpi.conversation.phrase.RecognizedSign
import com.helpi.conversation.vision.Pointing
import com.helpi.conversation.vision.PointingConfig
import com.helpi.conversation.vision.PointingDetector
import kotlin.math.roundToInt

/** Clasifica un segmento cerrado y lleva la decisión al observador y a la oración. */
internal class SignRecognition(
    private val loop: SessionLoop,
    private val data: SessionData,
    private val model: VisualModel,
    private val outlet: RecognitionOutlet,
    private val sentence: SentenceChannel,
    private val publish: () -> Unit
) {
    /** Un intento en curso, con lo que valía cuando se pidió. */
    private class Attempt(
        val model: VisualModel.Ready,
        val frames: List<CapturedFrame>,
        val endMs: Long,
        val turnId: Long?,
        val threshold: Float,
        val revision: Int
    ) {
        /** Inferencia sobre el tensor del contrato v3. */
        fun classify(contextWeights: FloatArray?): Result<SignDecision> = runCatching {
            val tensor = KeypointContract.buildInputTensor(
                frames.map { it.vector },
                frames.first().imageWidth,
                frames.first().imageHeight
            )
            model.classify(tensor, contextWeights)
        }
    }

    private val pointingDetector = PointingDetector(PointingConfig.defaults())

    /** Hay una clasificación en curso: no se admite otra ni se cierra la oración. */
    var busy = false
        private set

    /** Clasifica el segmento, o informa por qué no se puede. */
    fun classify(startMs: Long, endMs: Long, frames: List<CapturedFrame>) {
        val ready = model.ready()
        val problem = SegmentProblem.of(frames)
        when {
            ready == null -> outlet.noResult(NoResultCause.ERROR_MODELO)
            // sin cola ilimitada: el intento no se traduce tardíamente sin aviso
            busy -> note("Ese intento no se procesó: había otro en curso.")
            problem != null -> {
                outlet.noResult(problem.cause)
                problem.note?.let(::note)
            }
            else -> start(
                Attempt(
                    model = ready,
                    frames = frames,
                    endMs = endMs,
                    turnId = if (outlet.reachesConversation) sentence.turn(startMs) else null,
                    threshold = data.thresholds.current,
                    revision = data.visionRevision
                )
            )
        }
    }

    /** Suelta el modelo y olvida la clasificación en curso. */
    fun release() {
        model.release()
        busy = false
    }

    private fun start(attempt: Attempt) {
        val gen = loop.generation
        // El contexto se toma acá, en el executor serial: `busy` impide que otra
        // seña entre a la oración mientras esta se clasifica.
        val contextWeights = sentence.contextWeights()
        busy = true
        model.infer {
            val startedAt = loop.now()
            val result = attempt.classify(contextWeights)
            // Después de la inferencia y sobre los mismos cuadros, sin tocar el tensor.
            val pointing = detectPointing(attempt.frames)
            loop.submit(gen) { finish(attempt, result, pointing, startedAt) }
        }
    }

    private fun detectPointing(frames: List<CapturedFrame>): Pointing = runCatching {
        pointingDetector.detect(
            frames.map { it.vector },
            frames.map { it.head },
            LongArray(frames.size) { frames[it].timestampMs },
            frames.first().imageWidth,
            frames.first().imageHeight
        )
    }.getOrDefault(Pointing.NONE)

    private fun finish(
        attempt: Attempt,
        result: Result<SignDecision>,
        pointing: Pointing,
        startedAt: Long
    ) {
        busy = false
        data.visionMetrics = data.visionMetrics.copy(
            classifierRuns = data.visionMetrics.classifierRuns + 1,
            lastClassifierLatencyMs = (loop.now() - startedAt).coerceAtLeast(0L)
        )
        if (data.acceptsVisionResult(attempt.revision)) {
            result.fold(
                onSuccess = { decision -> apply(attempt, decision, pointing) },
                onFailure = { error -> fail(attempt, error) }
            )
        } else {
            attempt.turnId?.let(sentence::abandon)
        }
        publish()
    }

    private fun apply(attempt: Attempt, decision: SignDecision, pointing: Pointing) {
        val catalog = attempt.model.catalog
        val gloss = if (decision.accepted) catalog.gloss(decision.classIndex) else null
        data.lastRecognition = RecognitionFeedback(
            confidence = decision.confidence,
            threshold = attempt.threshold,
            accepted = decision.accepted,
            gloss = gloss,
            predictions = decision.predictions.mapNotNull { prediction ->
                catalog.gloss(prediction.classIndex)
                    ?.let { label -> RecognitionPrediction(label, prediction.confidence) }
            }
        )
        outlet.decision(decision, attempt.threshold, attempt.endMs) {
            val accepted = gloss?.let {
                RecognizedSign(decision.classIndex, it, decision.confidence)
            }
            val sign = SentenceRules.segmentSign(accepted, pointing)
            sentence.add(sign, attempt.endMs)
            if (sign == null && attempt.turnId != null) {
                noteUnrecognized(decision.confidence, attempt.threshold)
            }
        }
    }

    private fun fail(attempt: Attempt, error: Throwable) {
        outlet.noResult(NoResultCause.ERROR_MODELO)
        attempt.turnId?.let(sentence::abandon)
        data.notice =
            "No se pudo ejecutar el modelo LSA: ${error.message ?: "error desconocido"}"
    }

    private fun noteUnrecognized(confidence: Float, threshold: Float) {
        val confidencePercent = (confidence * PERCENT).roundToInt()
        val thresholdPercent = (threshold * PERCENT).roundToInt()
        data.conversation.systemNote(
            "No reconocí la seña. Volvé a intentarlo. " +
                "Confianza $confidencePercent %, umbral $thresholdPercent %.",
            loop.now()
        )
    }

    /** Nota del sistema en el chat; en el protocolo no se escribe nada. */
    private fun note(text: String) {
        if (outlet.reachesConversation) data.conversation.systemNote(text, loop.now())
    }

    private companion object {
        const val PERCENT = 100
    }
}
