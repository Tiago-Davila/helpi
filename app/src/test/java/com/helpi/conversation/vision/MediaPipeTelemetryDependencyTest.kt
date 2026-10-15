package com.helpi.conversation.vision

import com.google.android.datatransport.Encoding
import com.google.android.datatransport.cct.CCTDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MediaPipe Tasks referencia CCTDestination al crear cualquier task. Si la
 * clase falta (al excluir transport-backend-cct), la cámara no inicia y el
 * error solo aparece en el dispositivo. Este test lo detecta en la JVM.
 */
class MediaPipeTelemetryDependencyTest {

    @Test
    fun mediaPipeEncuentraElDestinoQueReferencia() {
        val loaded = Class.forName("com.google.android.datatransport.cct.CCTDestination")
        assertTrue(loaded.getField("INSTANCE").get(null) is CCTDestination)
        assertTrue(CCTDestination.supportedEncodings.contains(Encoding.of("proto")))
    }

    @Test
    fun elDestinoNoCoincideConElBackendDeTelemetria() {
        assertNotEquals("cct", CCTDestination.name)
        assertFalse(
            "transport-backend-cct volvió al classpath",
            runCatching {
                Class.forName("com.google.android.datatransport.cct.CctBackendFactory")
            }.isSuccess
        )
    }
}
