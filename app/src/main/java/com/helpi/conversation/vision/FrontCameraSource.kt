package com.helpi.conversation.vision

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Captura frontal con CameraX. La pantalla mira a la persona sorda; la
 * cámara frontal es una decisión tomada de accesibilidad, no se sustituye
 * por la trasera.
 *
 * Contención de cómputo: KEEP_ONLY_LATEST + intervalo mínimo entre envíos
 * al extractor (se descartan cuadros viejos antes que acumular retraso).
 * El video crudo nunca sale del dispositivo ni se almacena.
 */
class FrontCameraSource(
    private val context: Context,
    private val onFrame: (android.graphics.Bitmap, Long) -> Unit,
    private val onUnavailable: (String) -> Unit,
    private val onMetrics: (CameraCaptureMetrics) -> Unit = {},
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lastSentMs = AtomicLong(0)

    /** Perfil inicial: ahorrar recursos hasta que haya una seña candidata. */
    @Volatile
    private var samplingProfile = SamplingProfile.IDLE

    private var analyzedFrames = 0L
    private var rateLimitedFrames = 0L
    private var framesSinceReport = 0L
    private var lastMetricsReportMs = 0L

    private var provider: ProcessCameraProvider? = null
    private var bindingRevision = 0

    fun start(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider?) {
        val revision = ++bindingRevision
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (revision != bindingRevision) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider

                // Preview y análisis comparten relación de aspecto. Así los landmarks
                // normalizados se proyectan sobre la misma imagen, incluso con FIT_CENTER.
                val previewResolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val preview = Preview.Builder()
                    .setResolutionSelector(previewResolutionSelector)
                    .build().also {
                    if (surfaceProvider != null) it.surfaceProvider = surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    // El extractor trabaja con landmarks normalizados: 640×480 conserva
                    // detalle suficiente de manos y evita pedir una resolución nativa que
                    // reduzca la frecuencia efectiva en teléfonos modestos.
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    val last = lastSentMs.get()
                    if (now - last < samplingProfile.minimumIntervalMs) {
                        rateLimitedFrames++
                        reportMetricsIfDue(now)
                        imageProxy.close() // descartar antes que acumular
                        return@setAnalyzer
                    }
                    lastSentMs.set(now)
                    try {
                        val bitmap = imageProxy.toBitmap()
                        // La task devuelve coordenadas sobre esta imagen. Rotarla antes
                        // de enviarla preserva la misma geometría que usa la preview y
                        // el overlay, incluso en teléfonos en vertical.
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val upright = if (rotation != 0) {
                            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                            android.graphics.Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false,
                            )
                        } else {
                            bitmap
                        }
                        analyzedFrames++
                        framesSinceReport++
                        onFrame(upright, now)
                        reportMetricsIfDue(now)
                    } finally {
                        imageProxy.close()
                    }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis,
                )
            } catch (e: Exception) {
                onUnavailable("cámara frontal no disponible: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        bindingRevision++
        provider?.unbindAll()
        provider = null
    }

    fun shutdown() {
        stop()
        analysisExecutor.shutdown()
    }

    /**
     * Cambia la tasa sin reiniciar CameraX. Se llama desde el estado serial de
     * la sesión, que conoce si la persona está quieta, iniciando o haciendo una
     * seña. La fuente sigue usando KEEP_ONLY_LATEST para no acumular demora.
     */
    fun setSamplingProfile(profile: SamplingProfile) {
        samplingProfile = profile
    }

    private fun reportMetricsIfDue(nowMs: Long) {
        if (lastMetricsReportMs != 0L && nowMs - lastMetricsReportMs < METRICS_INTERVAL_MS) return
        val elapsedMs = if (lastMetricsReportMs == 0L) METRICS_INTERVAL_MS else nowMs - lastMetricsReportMs
        val fps = framesSinceReport * 1000f / elapsedMs
        onMetrics(
            CameraCaptureMetrics(
                samplingProfile = samplingProfile,
                targetFps = samplingProfile.targetFps,
                analyzerFps = fps,
                analyzedFrames = analyzedFrames,
                rateLimitedFrames = rateLimitedFrames,
            ),
        )
        framesSinceReport = 0L
        lastMetricsReportMs = nowMs
    }

    private companion object {
        const val METRICS_INTERVAL_MS = 500L
    }
}
