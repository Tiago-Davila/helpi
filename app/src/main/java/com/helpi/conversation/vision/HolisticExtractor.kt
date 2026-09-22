package com.helpi.conversation.vision

import android.content.Context
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker.HolisticLandmarkerOptions
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
import java.io.Closeable

/**
 * Envuelve MediaPipe Tasks HolisticLandmarker en modo LIVE_STREAM: la misma
 * task que produjo el dataset de entrenamiento (mismo extractor en ambos
 * extremos). Entrega leftHand/rightHand explícitos: no se resuelve la mano
 * por handedness.
 *
 * Los callbacks llegan en workers internos de MediaPipe: deben ser breves y
 * publicar hacia el executor visual serial, sin ejecutar LiteRT adentro.
 */
class HolisticExtractor(
    context: Context,
    modelAssetPath: String = "vision/holistic_landmarker.task",
    private val onFrame: (LandmarkFrame) -> Unit,
    private val onError: (RuntimeException) -> Unit,
    private val onMetrics: (LandmarkExtractionMetrics) -> Unit = {},
) : Closeable {

    private val landmarker: HolisticLandmarker
    private var submittedFrames = 0L
    private var resultFrames = 0L
    private var poseFrames = 0L
    private var leftHandFrames = 0L
    private var rightHandFrames = 0L
    private var resultsSinceReport = 0L
    private var lastMetricsReportMs = 0L
    private val metricsLock = Any()

    init {
        val options = HolisticLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAssetPath).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, image ->
                val nowMs = android.os.SystemClock.elapsedRealtime()
                val frame = toLandmarkFrame(result, image.width, image.height)
                recordResult(frame, nowMs, nowMs - result.timestampMs())?.let(onMetrics)
                onFrame(frame)
            }
            .setErrorListener { e -> onError(e) }
            .build()
        landmarker = HolisticLandmarker.createFromOptions(context, options)
    }

    /**
     * Envía un cuadro. Los timestamps deben ser monotónicos.
     * La imagen NO debe espejarse: la preview puede verse espejada, pero la
     * entrada al extractor conserva la orientación de captura.
     */
    fun analyze(bitmap: android.graphics.Bitmap, timestampMs: Long) {
        synchronized(metricsLock) { submittedFrames++ }
        landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
    }

    override fun close() {
        landmarker.close()
    }

    private fun recordResult(
        frame: LandmarkFrame,
        nowMs: Long,
        latencyMs: Long,
    ): LandmarkExtractionMetrics? = synchronized(metricsLock) {
        resultFrames++
        if (frame.pose != null) poseFrames++
        if (frame.leftHand != null) leftHandFrames++
        if (frame.rightHand != null) rightHandFrames++
        resultsSinceReport++

        if (lastMetricsReportMs != 0L && nowMs - lastMetricsReportMs < METRICS_INTERVAL_MS) {
            return@synchronized null
        }
        val elapsedMs = if (lastMetricsReportMs == 0L) METRICS_INTERVAL_MS else nowMs - lastMetricsReportMs
        val snapshot = LandmarkExtractionMetrics(
            resultFps = resultsSinceReport * 1000f / elapsedMs,
            submittedFrames = submittedFrames,
            resultFrames = resultFrames,
            poseFrames = poseFrames,
            leftHandFrames = leftHandFrames,
            rightHandFrames = rightHandFrames,
            lastLatencyMs = latencyMs.coerceAtLeast(0L),
        )
        resultsSinceReport = 0L
        lastMetricsReportMs = nowMs
        snapshot
    }

    private companion object {
        const val METRICS_INTERVAL_MS = 500L
    }

    private fun toLandmarkFrame(
        result: HolisticLandmarkerResult,
        imageWidth: Int,
        imageHeight: Int,
    ): LandmarkFrame {
        val ts = result.timestampMs()
        return LandmarkFrame(
            timestampMs = ts,
            leftHand = flatten(result.leftHandLandmarks(), 21),
            rightHand = flatten(result.rightHandLandmarks(), 21),
            pose = flatten(result.poseLandmarks(), 33),
            imageWidth = imageWidth,
            imageHeight = imageHeight,
        )
    }

    private fun flatten(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        expected: Int,
    ): FloatArray? {
        if (landmarks.isEmpty()) return null // no detectado -> ceros en el contrato
        if (landmarks.size != expected) return null // resultado anómalo: se descarta
        val out = FloatArray(expected * 3)
        landmarks.forEachIndexed { i, lm ->
            out[i * 3] = lm.x()
            out[i * 3 + 1] = lm.y()
            out[i * 3 + 2] = lm.z()
        }
        return out
    }
}
