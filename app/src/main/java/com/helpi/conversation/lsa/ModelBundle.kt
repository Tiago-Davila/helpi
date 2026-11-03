package com.helpi.conversation.lsa

import android.content.Context
import com.helpi.conversation.keypoints.KeypointContract
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Unidad modelo-catálogo de una lengua. Ambos artefactos se versionan y
 * verifican JUNTOS contra el manifiesto de su carpeta; una incompatibilidad
 * inhabilita el canal visual en lugar de traducir mal en silencio.
 *
 * Rutas en assets, por lengua (ver [SignLanguage]):
 *   lsa/modelo_lsa.tflite  lsa/catalogo_senas.json  lsa/lsa-manifest.json
 *   asl/modelo_asl.tflite  asl/catalogo_senas.json  asl/manifest.json
 *   lse/modelo_lse.tflite  lse/catalogo_senas.json  lse/manifest.json
 */
sealed class ModelBundleResult {
    data class Ready(val bundle: ModelBundle) : ModelBundleResult()
    data class Missing(val asset: String) : ModelBundleResult()
    data class Invalid(val cause: String) : ModelBundleResult()
}

class ModelBundle private constructor(
    val model: ByteBuffer,
    val catalog: SignCatalog,
    val manifest: Manifest
) {
    data class Manifest(
        val language: SignLanguage,
        val modelVersion: String,
        val catalogVersion: String,
        val modelSha256: String,
        val catalogSha256: String,
        val numClasses: Int,
        /** true si la salida ya son probabilidades; false si son logits. */
        val outputsProbabilities: Boolean,
        val frames: Int,
        val coords: Int,
        /** Versión del contrato de keypoints con el que se entrenó el modelo. */
        val contractVersion: Int,
        /** Licencia del dataset de origen; la interfaz la muestra. */
        val license: String = "",
        /** Atribución del dataset de origen; la interfaz la muestra. */
        val attribution: String = ""
    )

    companion object {
        /** Carga y verifica el paquete de la lengua elegida desde assets. */
        fun load(context: Context, language: SignLanguage = SignLanguage.LSA): ModelBundleResult =
            load(language) { path -> readAsset(context, path) }

        /** Carga y verifica el paquete de una lengua con un lector de assets inyectable. */
        internal fun load(
            language: SignLanguage = SignLanguage.LSA,
            read: (String) -> ByteArray?
        ): ModelBundleResult {
            val manifestAsset = language.manifestAsset()
            val modelAsset = language.modelAsset()
            val catalogAsset = language.catalogAsset()
            val manifestJson = read(manifestAsset)
                ?: return ModelBundleResult.Missing(manifestAsset)
            val manifest = try {
                parseManifest(String(manifestJson, Charsets.UTF_8))
            } catch (e: Exception) {
                return ModelBundleResult.Invalid("manifiesto ilegible: ${e.message}")
            }

            incompatibility(manifest, language)?.let { return ModelBundleResult.Invalid(it) }

            val modelBytes = read(modelAsset)
                ?: return ModelBundleResult.Missing(modelAsset)
            val actualHash = sha256(modelBytes)
            if (!actualHash.equals(manifest.modelSha256, ignoreCase = true)) {
                return ModelBundleResult.Invalid("hash del modelo no coincide con el manifiesto")
            }

            val catalogJson = read(catalogAsset)
                ?: return ModelBundleResult.Missing(catalogAsset)
            val actualCatalogHash = sha256(catalogJson)
            if (!actualCatalogHash.equals(manifest.catalogSha256, ignoreCase = true)) {
                return ModelBundleResult.Invalid("hash del catálogo no coincide con el manifiesto")
            }
            val catalog = try {
                SignCatalog.parse(String(catalogJson, Charsets.UTF_8))
            } catch (e: Exception) {
                return ModelBundleResult.Invalid("catálogo ilegible: ${e.message}")
            }

            if (catalog.version != manifest.catalogVersion) {
                return ModelBundleResult.Invalid(
                    "versión de catálogo ${catalog.version} != manifiesto ${manifest.catalogVersion}"
                )
            }
            if (catalog.size != manifest.numClasses) {
                return ModelBundleResult.Invalid(
                    "catálogo con ${catalog.size} glosas, el modelo emite ${manifest.numClasses} clases"
                )
            }

            val direct = ByteBuffer.allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
            direct.put(modelBytes)
            direct.rewind()
            return ModelBundleResult.Ready(ModelBundle(direct, catalog, manifest))
        }

        /** Motivo por el que el manifiesto no sirve para esta lengua y esta app, o null. */
        private fun incompatibility(manifest: Manifest, language: SignLanguage): String? = when {
            // Un manifiesto copiado en la carpeta de otra lengua haría que sus
            // índices se leyeran con un catálogo ajeno.
            manifest.language != language ->
                "el manifiesto de ${language.code()}/ declara la lengua ${manifest.language.code()}"
            // v2 y v3 comparten forma 40x168: sin esta verificación un modelo
            // v2 pasaría en silencio y recibiría valores en otra escala.
            manifest.contractVersion != KeypointContract.CONTRACT_VERSION ->
                "contrato de keypoints v${manifest.contractVersion}, " +
                    "la app implementa v${KeypointContract.CONTRACT_VERSION}"
            manifest.frames != KeypointContract.FRAMES ||
                manifest.coords != KeypointContract.COORDS ->
                "contrato incompatible: ${manifest.frames}x${manifest.coords}, " +
                    "se esperaba ${KeypointContract.FRAMES}x${KeypointContract.COORDS}"
            else -> null
        }

        /** Lee el manifiesto; falla si falta un campo o la lengua es desconocida. */
        private fun parseManifest(json: String): Manifest {
            val o = JSONObject(json)
            val code = o.getString("language")
            return Manifest(
                language = requireNotNull(SignLanguage.fromCode(code)) {
                    "lengua desconocida: $code"
                },
                modelVersion = o.getString("modelVersion"),
                catalogVersion = o.getString("catalogVersion"),
                modelSha256 = o.getString("modelSha256"),
                catalogSha256 = o.getString("catalogSha256"),
                numClasses = o.getInt("numClasses"),
                outputsProbabilities = o.getBoolean("outputsProbabilities"),
                frames = o.getInt("frames"),
                coords = o.getInt("coords"),
                // Los manifiestos anteriores a v3 no declaraban versión: eran v2.
                contractVersion = o.optInt("contractVersion", 2),
                license = o.optString("license"),
                attribution = o.optString("attribution")
            )
        }

        /** Bytes de un asset, o null si no existe. */
        private fun readAsset(context: Context, path: String): ByteArray? = try {
            context.assets.open(path).use { it.readBytes() }
        } catch (_: IOException) {
            null
        }

        /** Huella hexadecimal en minúsculas. */
        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
