package com.helpi.conversation.lsa

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ModelBundleTest {
    private fun assets(): MutableMap<String, ByteArray> =
        listOf("lsa-manifest.json", "modelo_lsa.tflite", "catalogo_senas.json")
            .associate { "lsa/$it" to File("src/main/assets/lsa/$it").readBytes() }.toMutableMap()

    private fun changeManifest(assets: MutableMap<String, ByteArray>, key: String, value: Any) {
        val path = "lsa/lsa-manifest.json"
        assets[path] =
            JSONObject(String(assets.getValue(path))).put(key, value).toString().toByteArray()
    }

    /** Paquete de LSA reubicado en la carpeta de otra lengua, con su manifiesto. */
    private fun bundleFor(language: SignLanguage): Map<String, ByteArray> {
        val lsa = assets()
        val manifest = JSONObject(String(lsa.getValue("lsa/lsa-manifest.json")))
            .put("language", language.code()).toString().toByteArray()
        return mapOf(
            language.manifestAsset() to manifest,
            language.modelAsset() to lsa.getValue("lsa/modelo_lsa.tflite"),
            language.catalogAsset() to lsa.getValue("lsa/catalogo_senas.json")
        )
    }

    @Test
    fun everyLanguageLoadsFromItsOwnFolder() {
        val assets = SignLanguage.values().fold(emptyMap<String, ByteArray>()) { all, language ->
            all + bundleFor(language)
        }
        for (language in SignLanguage.values()) {
            val result = ModelBundle.load(language) { assets[it] }
            assertTrue(language.code(), result is ModelBundleResult.Ready)
            assertEquals(language, (result as ModelBundleResult.Ready).bundle.manifest.language)
        }
    }

    @Test
    fun manifestOfAnotherLanguageInTheWrongFolderIsInvalid() {
        val lse = bundleFor(SignLanguage.LSE)
        val misplaced = bundleFor(SignLanguage.ASL).toMutableMap().apply {
            put(SignLanguage.ASL.manifestAsset(), lse.getValue(SignLanguage.LSE.manifestAsset()))
        }
        assertTrue(
            ModelBundle.load(SignLanguage.ASL) { misplaced[it] } is ModelBundleResult.Invalid
        )
    }

    @Test
    fun manifestWithoutLanguageIsInvalid() {
        val assets = assets()
        val path = "lsa/lsa-manifest.json"
        assets[path] = JSONObject(String(assets.getValue(path))).apply { remove("language") }
            .toString().toByteArray()
        assertTrue(ModelBundle.load { assets[it] } is ModelBundleResult.Invalid)
    }

    @Test
    fun aMissingFolderOnlyAffectsItsLanguage() {
        val assets = bundleFor(SignLanguage.LSA) + bundleFor(SignLanguage.LSE)
        val asl = ModelBundle.load(SignLanguage.ASL) { assets[it] }
        assertEquals(
            ModelBundleResult.Missing(SignLanguage.ASL.manifestAsset()),
            asl
        )
        assertTrue(ModelBundle.load(SignLanguage.LSA) { assets[it] } is ModelBundleResult.Ready)
        assertTrue(ModelBundle.load(SignLanguage.LSE) { assets[it] } is ModelBundleResult.Ready)
    }

    @Test
    fun licenseAndAttributionAreExposed() {
        val assets = assets()
        val manifest = (ModelBundle.load { assets[it] } as ModelBundleResult.Ready).bundle.manifest
        assertEquals("CC BY-NC-SA 4.0", manifest.license)
        assertTrue(manifest.attribution.contains("LSA64"))
    }

    @Test
    fun realBundleIsConsistent() {
        val assets = assets()
        assertTrue(ModelBundle.load { assets[it] } is ModelBundleResult.Ready)
    }

    @Test
    fun missingAssetsNeverProduceReady() {
        for (path in assets().keys) {
            val assets = assets().apply { remove(path) }
            assertTrue(path, ModelBundle.load { assets[it] } is ModelBundleResult.Missing)
        }
    }

    @Test
    fun tamperedModelAndCatalogAreRejected() {
        for (path in listOf("lsa/modelo_lsa.tflite", "lsa/catalogo_senas.json")) {
            val assets = assets()
            assets[path] = assets.getValue(path) + byteArrayOf(1)
            assertTrue(path, ModelBundle.load { assets[it] } is ModelBundleResult.Invalid)
        }
    }

    @Test
    fun dimensionsClassesVersionsAndMalformedManifestAreRejected() {
        for ((key, value) in listOf(
            "frames" to 39,
            "coords" to 126,
            "numClasses" to 63,
            "contractVersion" to 2,
            "catalogVersion" to "wrong",
            "language" to "xyz"
        )) {
            val assets = assets()
            changeManifest(assets, key, value)
            assertTrue(key, ModelBundle.load { assets[it] } is ModelBundleResult.Invalid)
        }
        val assets = assets()
        assets["lsa/lsa-manifest.json"] = "invalid".toByteArray()
        assertTrue(ModelBundle.load { assets[it] } is ModelBundleResult.Invalid)
    }

    @Test
    fun emptyCatalogIsRejectedEvenWithMatchingHash() {
        val assets = assets()
        val bytes = """{"version":"2.0","glosas":[]}""".toByteArray()
        assets["lsa/catalogo_senas.json"] = bytes
        changeManifest(
            assets,
            "catalogSha256",
            MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) }
        )
        assertTrue(ModelBundle.load { assets[it] } is ModelBundleResult.Invalid)
    }
}
