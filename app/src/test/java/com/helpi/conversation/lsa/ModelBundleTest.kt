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
    /** Lenguas cuyos artefactos están en assets; ASL todavía no tiene modelo. */
    private val shipped = listOf(SignLanguage.LSA, SignLanguage.LSE)

    private fun assets(vararg languages: SignLanguage): MutableMap<String, ByteArray> =
        languages.flatMap { language ->
            val paths = ModelBundle.assetPaths(language)
            listOf(paths.manifest, paths.model, paths.catalog)
        }.associateWith { File("src/main/assets/$it").readBytes() }.toMutableMap()

    private fun changeManifest(
        assets: MutableMap<String, ByteArray>,
        language: SignLanguage,
        key: String,
        value: Any
    ) {
        val path = ModelBundle.assetPaths(language).manifest
        assets[path] =
            JSONObject(String(assets.getValue(path))).put(key, value).toString().toByteArray()
    }

    @Test
    fun realBundlesAreConsistent() {
        for (language in shipped) {
            val assets = assets(language)
            val result = ModelBundle.load(language) { assets[it] }
            assertTrue(language.name, result is ModelBundleResult.Ready)
            val manifest = (result as ModelBundleResult.Ready).bundle.manifest
            assertEquals(language.code(), manifest.language)
            assertTrue(language.name, manifest.license.isNotBlank())
            assertTrue(language.name, manifest.attribution.isNotBlank())
        }
    }

    @Test
    fun lsaRemainsTheDefaultLanguage() {
        val assets = assets(SignLanguage.LSA)
        val result = ModelBundle.load { assets[it] }
        assertTrue(result is ModelBundleResult.Ready)
        assertEquals("lsa", (result as ModelBundleResult.Ready).bundle.manifest.language)
    }

    @Test
    fun languageWithoutArtifactsIsMissing() {
        val assets = assets(*shipped.toTypedArray())
        assertEquals(
            ModelBundleResult.Missing("asl/manifest.json"),
            ModelBundle.load(SignLanguage.ASL) { assets[it] }
        )
    }

    @Test
    fun manifestOfAnotherLanguageIsRejected() {
        val assets = assets(SignLanguage.LSE)
        changeManifest(assets, SignLanguage.LSE, "language", "lsa")
        assertTrue(ModelBundle.load(SignLanguage.LSE) { assets[it] } is ModelBundleResult.Invalid)
    }

    @Test
    fun oneBrokenLanguageDoesNotAffectTheOthers() {
        val assets = assets(*shipped.toTypedArray())
        assets.remove(ModelBundle.assetPaths(SignLanguage.LSA).model)
        assertTrue(ModelBundle.load(SignLanguage.LSA) { assets[it] } is ModelBundleResult.Missing)
        assertTrue(ModelBundle.load(SignLanguage.LSE) { assets[it] } is ModelBundleResult.Ready)
    }

    @Test
    fun missingAssetsNeverProduceReady() {
        for (language in shipped) {
            for (path in assets(language).keys) {
                val assets = assets(language).apply { remove(path) }
                assertTrue(
                    path,
                    ModelBundle.load(language) { assets[it] } is ModelBundleResult.Missing
                )
            }
        }
    }

    @Test
    fun tamperedModelAndCatalogAreRejected() {
        for (language in shipped) {
            val paths = ModelBundle.assetPaths(language)
            for (path in listOf(paths.model, paths.catalog)) {
                val assets = assets(language)
                assets[path] = assets.getValue(path) + byteArrayOf(1)
                assertTrue(
                    path,
                    ModelBundle.load(language) { assets[it] } is ModelBundleResult.Invalid
                )
            }
        }
    }

    @Test
    fun dimensionsClassesVersionsAndMalformedManifestAreRejected() {
        val language = SignLanguage.LSA
        for ((key, value) in listOf(
            "frames" to 39,
            "coords" to 126,
            "numClasses" to 63,
            "contractVersion" to 2,
            "catalogVersion" to "wrong"
        )) {
            val assets = assets(language)
            changeManifest(assets, language, key, value)
            assertTrue(key, ModelBundle.load(language) { assets[it] } is ModelBundleResult.Invalid)
        }
        for (key in listOf("language", "license", "attribution")) {
            val assets = assets(language)
            val path = ModelBundle.assetPaths(language).manifest
            assets[path] = JSONObject(String(assets.getValue(path))).apply { remove(key) }
                .toString().toByteArray()
            assertTrue(key, ModelBundle.load(language) { assets[it] } is ModelBundleResult.Invalid)
        }
        val assets = assets(language)
        assets[ModelBundle.assetPaths(language).manifest] = "invalid".toByteArray()
        assertTrue(ModelBundle.load(language) { assets[it] } is ModelBundleResult.Invalid)
    }

    @Test
    fun emptyCatalogIsRejectedEvenWithMatchingHash() {
        val language = SignLanguage.LSA
        val assets = assets(language)
        val bytes = """{"version":"2.0","glosas":[]}""".toByteArray()
        assets[ModelBundle.assetPaths(language).catalog] = bytes
        changeManifest(
            assets,
            language,
            "catalogSha256",
            MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) }
        )
        assertTrue(ModelBundle.load(language) { assets[it] } is ModelBundleResult.Invalid)
    }
}
