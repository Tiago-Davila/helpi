package com.helpi.conversation.vision

/**
 * Perfiles de captura. La tasa se eleva antes de clasificar para conservar
 * movimiento rápido, y baja durante el reposo para no gastar batería ni CPU.
 */
enum class SamplingProfile(
    val targetFps: Int,
    val minimumIntervalMs: Long,
    val displayName: String,
) {
    IDLE(targetFps = 12, minimumIntervalMs = 83, displayName = "reposo"),
    ARMED(targetFps = 16, minimumIntervalMs = 62, displayName = "armado"),
    ACTIVE(targetFps = 24, minimumIntervalMs = 42, displayName = "captura"),
}

/**
 * Muestra de telemetría de captura. Se emite como máximo dos veces por
 * segundo: es suficiente para diagnosticar la cámara sin competir con ella.
 */
data class CameraCaptureMetrics(
    val samplingProfile: SamplingProfile,
    val targetFps: Int,
    val analyzerFps: Float,
    val analyzedFrames: Long,
    val rateLimitedFrames: Long,
)

/** Resultado acumulado del extractor MediaPipe para la sesión actual. */
data class LandmarkExtractionMetrics(
    val resultFps: Float,
    val submittedFrames: Long,
    val resultFrames: Long,
    val poseFrames: Long,
    val leftHandFrames: Long,
    val rightHandFrames: Long,
    val lastLatencyMs: Long,
)

/**
 * Diagnóstico visible de extremo a extremo. No contiene imágenes ni
 * coordenadas: solo contadores y tiempos locales de la sesión.
 */
data class VisionMetrics(
    val targetFps: Int = SamplingProfile.IDLE.targetFps,
    val cameraFps: Float = 0f,
    val mediaPipeFps: Float = 0f,
    val analyzedFrames: Long = 0,
    val rateLimitedFrames: Long = 0,
    val landmarkFrames: Long = 0,
    val poseFrames: Long = 0,
    val leftHandFrames: Long = 0,
    val rightHandFrames: Long = 0,
    val lastMediaPipeLatencyMs: Long = 0,
    val segmentsStarted: Long = 0,
    val segmentsCompleted: Long = 0,
    val segmentsAborted: Long = 0,
    val classifierRuns: Long = 0,
    val lastClassifierLatencyMs: Long = 0,
)
