package com.helpi.conversation.lsa

import com.helpi.conversation.phrase.RecognizedSign
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PhraseAssetsTest {
    private val catalog =
        SignCatalog.parse(File("src/main/assets/lsa/catalogo_senas.json").readText())

    private val manifest = ModelBundle.Manifest(
        modelVersion = "eva-lsa64-v3",
        catalogVersion = "2.0",
        modelSha256 = "",
        catalogSha256 = "",
        numClasses = 64,
        outputsProbabilities = false,
        frames = 40,
        coords = 168,
        contractVersion = 3
    )

    private fun assets(): MutableMap<String, ByteArray> =
        listOf("lexico_senas.json", "plantillas_frases.json")
            .associate { "lsa/$it" to File("src/main/assets/lsa/$it").readBytes() }.toMutableMap()

    private fun mujerSordo() = listOf(
        RecognizedSign(8, "Mujer", 0.95f),
        RecognizedSign(41, "Sordo", 0.95f)
    )

    @Test
    fun realAssetsEnableTemplates() {
        val assets = assets()
        val loaded = PhraseAssets.load(manifest, catalog) { assets[it] }
        assertTrue(loaded is PhraseAssets.Ready)
        val builder = loaded.builder(0.7f)
        assertTrue(builder.isEnabled)
        assertEquals("La mujer sorda.", builder.build(mujerSordo()).text())
    }

    @Test
    fun missingOrMalformedAssetsDisableTemplates() {
        for (path in assets().keys) {
            val missing = assets().apply { remove(path) }
            val withoutFile = PhraseAssets.load(manifest, catalog) { missing[it] }
            assertTrue(path, withoutFile is PhraseAssets.Disabled)

            val malformed = assets().apply { put(path, "{".toByteArray()) }
            val unreadable = PhraseAssets.load(manifest, catalog) { malformed[it] }
            assertTrue(path, unreadable is PhraseAssets.Disabled)
        }
    }

    @Test
    fun versionMismatchShowsLooseGlosses() {
        val assets = assets()
        val path = "lsa/lexico_senas.json"
        assets[path] = JSONObject(String(assets.getValue(path)))
            .put("catalogVersion", "1.0").toString().toByteArray()

        val loaded = PhraseAssets.load(manifest, catalog) { assets[it] }
        assertTrue(loaded is PhraseAssets.Disabled)
        val builder = loaded.builder(0.7f)
        assertFalse(builder.isEnabled)
        assertEquals("MUJER SORDO", builder.build(mujerSordo()).text())

        val otherModel = manifest.copy(modelVersion = "eva-lsa64-v4")
        assertTrue(PhraseAssets.load(otherModel, catalog) { assets()[it] } is PhraseAssets.Disabled)
    }
}
