package com.helpi.conversation.lsa.sequence

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Artefactos del traductor experimental LSA-T.
 *
 * Este bundle es deliberadamente independiente de [com.helpi.conversation.lsa.ModelBundle]:
 * Eva sigue usando sus assets y contrato 40x201. Ningún asset de este paquete se
 * carga como fallback del modelo estable.
 */
sealed class SequenceModelBundleResult {
    data class Ready(val bundle: SequenceModelBundle) : SequenceModelBundleResult()
    data class Missing(val asset: String) : SequenceModelBundleResult()
    data class Invalid(val cause: String) : SequenceModelBundleResult()
}

data class SequenceTensorSpec(
    val shape: List<Int>,
    val shapeSignature: List<Int>,
    val dtype: String,
)

data class SequenceModelManifest(
    val artifactVersion: String,
    val encoderFile: String,
    val encoderSha256: String,
    val decoderFile: String,
    val decoderSha256: String,
    val vocabularyFile: String,
    val vocabularySha256: String,
    val vocabularySize: Int,
    val inputLayout: String,
    val encoderInputLayout: String,
    val decoderMemoryLayout: String,
    val temporalDescription: String,
    val bosId: Int,
    val eosId: Int,
    val padId: Int,
    val unkId: Int,
    val maxTokens: Int,
) {
    companion object {
        fun parse(json: String): SequenceModelManifest {
            val root = JSONObject(json)
            val preprocessing = root.getJSONObject("preprocessing")
            val encoder = root.getJSONObject("encoder")
            val decoder = root.getJSONObject("decoder")
            val vocabulary = root.getJSONObject("vocabulary")
            val tokenIds = vocabulary.getJSONObject("specialTokenIds")

            return SequenceModelManifest(
                artifactVersion = root.getString("artifactVersion"),
                encoderFile = encoder.getString("file"),
                encoderSha256 = encoder.getString("sha256"),
                decoderFile = decoder.getString("file"),
                decoderSha256 = decoder.getString("sha256"),
                vocabularyFile = vocabulary.getString("file"),
                vocabularySha256 = vocabulary.getString("sha256"),
                vocabularySize = vocabulary.getInt("size"),
                inputLayout = preprocessing.getString("inputLayoutBeforeExport"),
                encoderInputLayout = encoder.getString("androidInputLayout"),
                decoderMemoryLayout = decoder.getString("androidMemoryLayout"),
                temporalDescription = preprocessing.getString("temporal"),
                bosId = tokenIds.getInt("bos"),
                eosId = tokenIds.getInt("eos"),
                padId = tokenIds.getInt("pad"),
                unkId = tokenIds.getInt("unk"),
                maxTokens = root.getJSONObject("decoderPolicy").getInt("maxTokens"),
            )
        }
    }

    fun validateContract() {
        require(artifactVersion.startsWith("lsa-t-")) {
            "artifactVersion no corresponde a LSA-T: $artifactVersion"
        }
        require(inputLayout == "B,T,126") {
            "entrada de preprocesamiento incompatible: $inputLayout"
        }
        require(encoderInputLayout == "B,126,T") {
            "entrada Android del encoder incompatible: $encoderInputLayout"
        }
        require(decoderMemoryLayout == "B,256,T") {
            "memoria Android del decoder incompatible: $decoderMemoryLayout"
        }
        require(temporalDescription.contains("integer", ignoreCase = true)) {
            "el manifiesto no declara muestreo temporal entero"
        }
        require(temporalDescription.contains("zero-pad", ignoreCase = true)) {
            "el manifiesto no declara padding cero"
        }
        require(maxTokens > 0) { "maxTokens debe ser positivo" }
        require(vocabularySize > 0) { "vocabulary.size debe ser positivo" }
    }
}

class SequenceVocabulary private constructor(
    private val tokens: List<String>,
    val bosId: Int,
    val eosId: Int,
    val padId: Int,
    val unkId: Int,
) {
    val size: Int get() = tokens.size

    fun token(id: Int): String? = tokens.getOrNull(id)

    fun decode(ids: List<Int>): String = ids
        .asSequence()
        .filter { it != bosId && it != eosId && it != padId && it != unkId }
        .mapNotNull { token(it) }
        .joinToString(" ")

    companion object {
        fun parse(
            json: String,
            bosId: Int,
            eosId: Int,
            padId: Int,
            unkId: Int,
        ): SequenceVocabulary {
            val array = JSONArray(json)
            require(array.length() > 0) { "vocabulario vacío" }
            val tokens = buildList {
                for (index in 0 until array.length()) {
                    val token = array.getString(index)
                    require(token.isNotBlank()) { "token vacío en índice $index" }
                    add(token)
                }
            }
            listOf(bosId, eosId, padId, unkId).forEach { id ->
                require(id in tokens.indices) { "token especial fuera del vocabulario: $id" }
            }
            return fromTokens(tokens, bosId, eosId, padId, unkId)
        }

        fun fromTokens(
            tokens: List<String>,
            bosId: Int,
            eosId: Int,
            padId: Int,
            unkId: Int,
        ): SequenceVocabulary {
            require(tokens.isNotEmpty()) { "vocabulario vacío" }
            tokens.forEachIndexed { index, token ->
                require(token.isNotBlank()) { "token vacío en índice $index" }
            }
            listOf(bosId, eosId, padId, unkId).forEach { id ->
                require(id in tokens.indices) { "token especial fuera del vocabulario: $id" }
            }
            return SequenceVocabulary(tokens.toList(), bosId, eosId, padId, unkId)
        }
    }
}

class SequenceModelBundle private constructor(
    val encoder: ByteBuffer,
    val decoder: ByteBuffer,
    val vocabulary: SequenceVocabulary,
    val manifest: SequenceModelManifest,
) {
    companion object {
        private const val DIR = "lsa_t"
        private const val MANIFEST_ASSET = "$DIR/android-manifest.json"
        private const val LEGACY_MANIFEST_ASSET = "$DIR/lsa-t-manifest.json"

        fun load(context: Context): SequenceModelBundleResult {
            val manifestPath = when {
                assetExists(context, MANIFEST_ASSET) -> MANIFEST_ASSET
                assetExists(context, LEGACY_MANIFEST_ASSET) -> LEGACY_MANIFEST_ASSET
                else -> return SequenceModelBundleResult.Missing(MANIFEST_ASSET)
            }
            val manifestBytes = readAsset(context, manifestPath)
                ?: return SequenceModelBundleResult.Missing(manifestPath)

            val manifest = try {
                SequenceModelManifest.parse(String(manifestBytes, Charsets.UTF_8)).also {
                    it.validateContract()
                }
            } catch (e: Exception) {
                return SequenceModelBundleResult.Invalid("manifiesto LSA-T ilegible: ${e.message}")
            }

            val encoderPath = "$DIR/${manifest.encoderFile}"
            val decoderPath = "$DIR/${manifest.decoderFile}"
            val vocabularyPath = "$DIR/${manifest.vocabularyFile}"
            val encoderBytes = readAsset(context, encoderPath)
                ?: return SequenceModelBundleResult.Missing(encoderPath)
            val decoderBytes = readAsset(context, decoderPath)
                ?: return SequenceModelBundleResult.Missing(decoderPath)
            val vocabularyBytes = readAsset(context, vocabularyPath)
                ?: return SequenceModelBundleResult.Missing(vocabularyPath)

            if (!sha256(encoderBytes).equals(manifest.encoderSha256, ignoreCase = true)) {
                return SequenceModelBundleResult.Invalid("hash del encoder LSA-T no coincide")
            }
            if (!sha256(decoderBytes).equals(manifest.decoderSha256, ignoreCase = true)) {
                return SequenceModelBundleResult.Invalid("hash del decoder LSA-T no coincide")
            }
            if (!sha256(vocabularyBytes).equals(manifest.vocabularySha256, ignoreCase = true)) {
                return SequenceModelBundleResult.Invalid("hash del vocabulario LSA-T no coincide")
            }

            val vocabulary = try {
                SequenceVocabulary.parse(
                    json = String(vocabularyBytes, Charsets.UTF_8),
                    bosId = manifest.bosId,
                    eosId = manifest.eosId,
                    padId = manifest.padId,
                    unkId = manifest.unkId,
                )
            } catch (e: Exception) {
                return SequenceModelBundleResult.Invalid("vocabulario LSA-T ilegible: ${e.message}")
            }
            if (vocabulary.size != manifest.vocabularySize) {
                return SequenceModelBundleResult.Invalid(
                    "vocabulario con ${vocabulary.size} tokens; " +
                        "el manifiesto declara ${manifest.vocabularySize}",
                )
            }

            return SequenceModelBundleResult.Ready(
                SequenceModelBundle(
                    encoder = directBuffer(encoderBytes),
                    decoder = directBuffer(decoderBytes),
                    vocabulary = vocabulary,
                    manifest = manifest,
                ),
            )
        }

        private fun assetExists(context: Context, path: String): Boolean =
            try {
                context.assets.open(path).use { true }
            } catch (_: IOException) {
                false
            }

        private fun readAsset(context: Context, path: String): ByteArray? =
            try {
                context.assets.open(path).use { it.readBytes() }
            } catch (_: IOException) {
                null
            }

        private fun directBuffer(bytes: ByteArray): ByteBuffer =
            ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(bytes)
                    rewind()
                }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
