package com.helpi.conversation.lsa

import android.content.Context
import com.helpi.conversation.keypoints.KeypointContract
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Unidad modelo-catálogo de una lengua de señas. Ambos artefactos se
 * versionan y verifican JUNTOS contra el manifiesto de su carpeta; una
 * incompatibilidad inhabilita el canal visual en lugar de traducir mal en
 * silencio. Cada lengua se carga por separado: la falla de una no afecta a
 * las demás.
 *
 * Rutas esperadas en assets (`<x>` = código de la lengua):
 *   lsa/modelo_lsa.tflite, lsa/catalogo_senas.json, lsa/lsa-manifest.json
 *   <x>/modelo_<x>.tflite, <x>/catalogo_senas.json, <x>/manifest.json
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
        /** Código de la lengua; debe coincidir con la carpeta del manifiesto. */
        val language: String,
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
        /** Licencia del dataset de origen; la interfaz debe mostrarla. */
        val license: String,
        /** Atribución del dataset de origen; la interfaz debe mostrarla. */
        val attribution: String
    )

    /** Rutas de los artefactos de una lengua dentro de assets. */
    internal data class AssetPaths(val manifest: String, val model: String, val catalog: String)

    companion object {
        fun load(context: Context, language: SignLanguage = SignLanguage.LSA): ModelBundleResult =
            load(language) { path -> readAsset(context, path) }

        // Deuda previa registrada en el baseline de detekt de develop: cada
        // validación corta la carga con su propio resultado.
        @Suppress("ReturnCount")
        internal fun load(
            language: SignLanguage = SignLanguage.LSA,
            read: (String) -> ByteArray?
        ): ModelBundleResult {
            val paths = assetPaths(language)
            val manifestJson = read(paths.manifest)
                ?: return ModelBundleResult.Missing(paths.manifest)
            val manifest = try {
                parseManifest(String(manifestJson, Charsets.UTF_8))
            } catch (e: Exception) {
                return ModelBundleResult.Invalid("manifiesto ilegible: ${e.message}")
            }
            manifestProblem(manifest, language)?.let { return ModelBundleResult.Invalid(it) }

            val modelBytes = read(paths.model)
                ?: return ModelBundleResult.Missing(paths.model)
            val actualHash = sha256(modelBytes)
            if (!actualHash.equals(manifest.modelSha256, ignoreCase = true)) {
                return ModelBundleResult.Invalid("hash del modelo no coincide con el manifiesto")
            }

            val catalogJson = read(paths.catalog)
                ?: return ModelBundleResult.Missing(paths.catalog)
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

        /** Lee solo el manifiesto de una lengua, sin cargar el modelo, para mostrar sus créditos. */
        fun readManifest(context: Context, language: SignLanguage): Manifest? =
            readManifest(language) { path -> readAsset(context, path) }

        /** Manifiesto legible de la lengua, o null si falta o no se puede leer. */
        internal fun readManifest(language: SignLanguage, read: (String) -> ByteArray?): Manifest? {
            val json = read(assetPaths(language).manifest) ?: return null
            return runCatching { parseManifest(String(json, Charsets.UTF_8)) }.getOrNull()
        }

        /** Motivo por el que el manifiesto no sirve para esta lengua y esta app, o null si sirve. */
        private fun manifestProblem(manifest: Manifest, language: SignLanguage): String? = when {
            // Un manifiesto copiado en la carpeta equivocada haría que la app
            // traduzca con el catálogo de otra lengua sin ningún error.
            manifest.language != language.code() ->
                "manifiesto de ${manifest.language} en la carpeta de ${language.code()}"
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

        /** LSA conserva su nombre de manifiesto histórico; las demás usan manifest.json. */
        internal fun assetPaths(language: SignLanguage): AssetPaths {
            val code = language.code()
            val manifestName =
                if (language == SignLanguage.LSA) "lsa-manifest.json" else "manifest.json"
            return AssetPaths(
                manifest = "$code/$manifestName",
                model = "$code/modelo_$code.tflite",
                catalog = "$code/catalogo_senas.json"
            )
        }

        private fun parseManifest(json: String): Manifest {
            val o = JSONObject(json)
            return Manifest(
                language = o.getString("language"),
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
                license = o.getString("license"),
                attribution = o.getString("attribution")
            )
        }

        private fun readAsset(context: Context, path: String): ByteArray? = try {
            context.assets.open(path).use { it.readBytes() }
        } catch (_: IOException) {
            null
        }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
