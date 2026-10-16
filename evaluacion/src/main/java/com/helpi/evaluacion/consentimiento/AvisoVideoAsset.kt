package com.helpi.evaluacion.consentimiento

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

data class AvisoVideoAsset(val archivo: File, val sha256: String)

object AvisoVideoAssetLoader {
    const val RUTA_ASSET = "evaluacion/aviso/aviso-v$AVISO_VERSION_ACTUAL.mp4"

    fun cargar(context: Context): AvisoVideoAsset? {
        val archivo = File(context.cacheDir, "aviso-v$AVISO_VERSION_ACTUAL.mp4")
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesCopiados = 0L
            context.assets.open(RUTA_ASSET).use { entrada ->
                FileOutputStream(archivo).use { salida ->
                    val buffer = ByteArray(TAMANO_BUFFER)
                    while (true) {
                        val leidos = entrada.read(buffer)
                        if (leidos < 0) break
                        digest.update(buffer, 0, leidos)
                        salida.write(buffer, 0, leidos)
                        bytesCopiados += leidos
                    }
                }
            }
            if (bytesCopiados == 0L) {
                archivo.delete()
                null
            } else {
                AvisoVideoAsset(archivo, digest.digest().toHexadecimal())
            }
        } catch (_: IOException) {
            archivo.delete()
            null
        }
    }

    fun hash(archivo: File): String? {
        if (!archivo.isFile) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            archivo.inputStream().use { entrada ->
                val buffer = ByteArray(TAMANO_BUFFER)
                while (true) {
                    val leidos = entrada.read(buffer)
                    if (leidos < 0) break
                    digest.update(buffer, 0, leidos)
                }
            }
            digest.digest().toHexadecimal()
        } catch (_: IOException) {
            null
        }
    }

    private fun ByteArray.toHexadecimal(): String = joinToString("") {
        "%02x".format(Locale.ROOT, it.toInt() and 0xff)
    }

    private const val TAMANO_BUFFER = 8 * 1024
}
