package com.helpi.conversation.vision

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lastSentMs = AtomicLong(0)

    /** Intervalo mínimo entre cuadros analizados; perfil nominal 15 fps. */
    @Volatile
    var minAnalysisIntervalMs: Long = 66

    private var provider: ProcessCameraProvider? = null

    fun start(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider?) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider

                val preview = Preview.Builder().build().also {
                    if (surfaceProvider != null) it.surfaceProvider = surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    val last = lastSentMs.get()
                    if (now - last < minAnalysisIntervalMs) {
                        imageProxy.close() // descartar antes que acumular
                        return@setAnalyzer
                    }
                    lastSentMs.set(now)
                    try {
                        val bitmap = imageProxy.toBitmap()
                        // rotación de captura aplicada; sin espejado arbitrario
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val upright = if (rotation != 0) {
                            val m = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                            android.graphics.Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, m, false,
                            )
                        } else {
                            bitmap
                        }
                        onFrame(upright, now)
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
        provider?.unbindAll()
        provider = null
    }

    fun shutdown() {
        stop()
        analysisExecutor.shutdown()
    }
}
