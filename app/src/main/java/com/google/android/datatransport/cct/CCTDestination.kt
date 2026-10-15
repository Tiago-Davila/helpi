package com.google.android.datatransport.cct

import com.google.android.datatransport.Encoding
import com.google.android.datatransport.runtime.EncodedDestination

/**
 * Reemplaza al destino de transport-backend-cct, que se excluye porque solo
 * envía telemetría. MediaPipe Tasks lo referencia de forma directa al crear
 * cualquier task (RemoteLoggingClient): sin esta clase, HolisticLandmarker
 * falla con NoClassDefFoundError y la app muestra "No se pudo iniciar la
 * cámara".
 *
 * El nombre no coincide con ningún backend registrado, así que
 * transport-runtime descarta los eventos sin enviarlos.
 */
object CCTDestination : EncodedDestination {
    // Ningún backend se registra con este nombre: los eventos no salen del dispositivo.
    override fun getName(): String = "helpi-sin-telemetria"

    // Sin endpoint ni clave: no hay a dónde enviar.
    override fun getExtras(): ByteArray? = null

    // MediaPipe pide "proto"; un formato no declarado aborta la creación de la task.
    override fun getSupportedEncodings(): Set<Encoding> = setOf(Encoding.of("proto"))
}
