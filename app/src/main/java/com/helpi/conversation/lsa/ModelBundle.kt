package com.helpi.conversation.lsa

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Unidad modelo-catálogo. Ambos artefactos se versionan y verifican JUNTOS
 * contra lsa-manifest.json; una incompatibilidad inhabilita el canal visual
 * en lugar de traducir mal en silencio.
 *
 * Rutas esperadas en assets:
 *   lsa/modelo_lsa.tflite
 *   lsa/catalogo_senas.json
 *   lsa/lsa-manifest.json
 */
sealed class ModelBundleResult {
    data class Ready(val bundle: ModelBundle) : ModelBundleResult()
    data class Missing(val asset: String) : ModelBundleResult()
    data class Invalid(val cause: String) : ModelBundleResult()
}

class ModelBundle private constructor(
    val model: ByteBuffer,
    val catalog: SignCatalog,
    val manifest: Manifest,
) {
    data class Manifest(
        val modelVersion: String,
        val catalogVersion: String,
        val modelSha256: String,
        val numClasses: Int,
        /** true si la salida ya son probabilidades; false si son logits. */
        val outputsProbabilities: Boolean,
        val frames: Int,
        val coords: Int,
    )

    companion object {
        private const val DIR = "lsa"
        private const val MODEL_ASSET = "$DIR/modelo_lsa.tflite"
        private const val CATALOG_ASSET = "$DIR/catalogo_senas.json"
        private const val MANIFEST_ASSET = "$DIR/lsa-manifest.json"

        fun load(context: Context): ModelBundleResult {
            val manifestJson = readAsset(context, MANIFEST_ASSET)
                ?: return ModelBundleResult.Missing(MANIFEST_ASSET)
            val manifest = try {
                parseManifest(String(manifestJson, Charsets.UTF_8))
            } catch (e: Exception) {
                return ModelBundleResult.Invalid("manifiesto ilegible: ${e.message}")
            }

            if (manifest.frames != 40 || manifest.coords != 201) {
                return ModelBundleResult.Invalid(
                    "contrato incompatible: ${manifest.frames}x${manifest.coords}, se esperaba 40x201",
                )
            }

            val modelBytes = readAsset(context, MODEL_ASSET)
                ?: return ModelBundleResult.Missing(MODEL_ASSET)
            val actualHash = sha256(modelBytes)
            if (!actualHash.equals(manifest.modelSha256, ignoreCase = true)) {
                return ModelBundleResult.Invalid("hash del modelo no coincide con el manifiesto")
            }

            val catalogJson = readAsset(context, CATALOG_ASSET)
                ?: return ModelBundleResult.Missing(CATALOG_ASSET)
            val catalog = try {
                SignCatalog.parse(String(catalogJson, Charsets.UTF_8))
            } catch (e: Exception) {
                return ModelBundleResult.Invalid("catálogo ilegible: ${e.message}")
            }

            if (catalog.version != manifest.catalogVersion) {
                return ModelBundleResult.Invalid(
                    "versión de catálogo ${catalog.version} != manifiesto ${manifest.catalogVersion}",
                )
            }
            if (catalog.size != manifest.numClasses) {
                return ModelBundleResult.Invalid(
                    "catálogo con ${catalog.size} glosas, el modelo emite ${manifest.numClasses} clases",
                )
            }

            val direct = ByteBuffer.allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
            direct.put(modelBytes)
            direct.rewind()
            return ModelBundleResult.Ready(ModelBundle(direct, catalog, manifest))
        }

        private fun parseManifest(json: String): Manifest {
            val o = JSONObject(json)
            return Manifest(
                modelVersion = o.getString("modelVersion"),
                catalogVersion = o.getString("catalogVersion"),
                modelSha256 = o.getString("modelSha256"),
                numClasses = o.getInt("numClasses"),
                outputsProbabilities = o.getBoolean("outputsProbabilities"),
                frames = o.getInt("frames"),
                coords = o.getInt("coords"),
            )
        }

        private fun readAsset(context: Context, path: String): ByteArray? =
            try {
                context.assets.open(path).use { it.readBytes() }
            } catch (_: IOException) {
                null
            }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
