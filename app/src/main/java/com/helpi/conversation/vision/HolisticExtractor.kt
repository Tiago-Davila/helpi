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
) : Closeable {

    private val landmarker: HolisticLandmarker

    init {
        val options = HolisticLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAssetPath).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> onFrame(toLandmarkFrame(result)) }
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
        landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
    }

    override fun close() {
        landmarker.close()
    }

    private fun toLandmarkFrame(result: HolisticLandmarkerResult): LandmarkFrame {
        val ts = result.timestampMs()
        return LandmarkFrame(
            timestampMs = ts,
            leftHand = flatten(result.leftHandLandmarks(), 21),
            rightHand = flatten(result.rightHandLandmarks(), 21),
            pose = flatten(result.poseLandmarks(), 33),
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
