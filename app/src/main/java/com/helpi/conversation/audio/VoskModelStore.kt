package com.helpi.conversation.audio

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Instala el modelo Vosk español desde assets al almacenamiento privado.
 * Nunca descarga nada: si el asset no está, el canal A queda inhabilitado
 * con causa visible. El modelo no contiene datos de conversación.
 *
 * Asset esperado: assets/vosk/model-es/ (árbol descomprimido del modelo,
 * candidato inicial vosk-model-small-es-0.42, Apache 2.0).
 */
object VoskModelStore {

    private const val ASSET_DIR = "vosk/model-es"
    private const val MARKER = ".instalado"

    sealed class Result {
        data class Ready(val modelDir: File) : Result()
        data class Missing(val detail: String) : Result()
        data class Failed(val detail: String) : Result()
    }

    fun install(context: Context): Result {
        val target = File(context.filesDir, "vosk-model-es")
        val marker = File(target, MARKER)
        if (marker.exists()) {
            return Result.Ready(target)
        }
        val assets = context.assets
        val entries = try {
            assets.list(ASSET_DIR)
        } catch (e: IOException) {
            null
        }
        if (entries.isNullOrEmpty()) {
            return Result.Missing("assets/$ASSET_DIR no está empaquetado")
        }
        return try {
            target.deleteRecursively()
            copyDir(context, ASSET_DIR, target)
            marker.createNewFile()
            Result.Ready(target)
        } catch (e: IOException) {
            target.deleteRecursively()
            Result.Failed("instalación fallida: ${e.message}")
        }
    }

    private fun copyDir(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            // es un archivo
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        target.mkdirs()
        for (child in children) {
            copyDir(context, "$assetPath/$child", File(target, child))
        }
    }
}
