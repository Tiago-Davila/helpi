package com.helpi.conversation.vision

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Arranque del extractor en un dispositivo real (bloqueante de CI).
 *
 * Si MediaPipe no puede crear la task, la app muestra "No se pudo iniciar la
 * cámara" sin ninguna otra pista. Las causas observadas aparecen al
 * actualizar dependencias o el sistema operativo: conflictos de protobuf o
 * Guava en runtime, librerías nativas que no cargan o el .task comprimido en
 * el APK. Ninguna de ellas falla en compilación ni en los tests JVM.
 */
@RunWith(AndroidJUnit4::class)
class HolisticExtractorStartupTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun creaElLandmarkerYProcesaUnCuadro() {
        val error = AtomicReference<RuntimeException?>()
        val frames = CountDownLatch(1)
        val extractor = HolisticExtractor(
            context = context,
            onFrame = { frames.countDown() },
            onError = { error.set(it) }
        )
        extractor.use {
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            it.analyze(bitmap, timestampMs = 1L)
            assertTrue(
                "el landmarker no devolvió resultado en ${TIMEOUT_S}s",
                frames.await(TIMEOUT_S, TimeUnit.SECONDS)
            )
        }
        assertNull("error del landmarker: ${error.get()}", error.get())
    }

    private companion object {
        const val TIMEOUT_S = 30L
    }
}
