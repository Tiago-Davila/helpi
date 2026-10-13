package com.helpi.conversation.lsa

import android.content.Context
import com.helpi.conversation.phrase.Lexicon
import com.helpi.conversation.phrase.PhraseBuilder
import com.helpi.conversation.phrase.PhraseCompatibility
import com.helpi.conversation.phrase.TemplateSet
import java.io.IOException

/**
 * Léxico y plantillas del armado de frases, validados contra el modelo y el
 * catálogo cargados. Si algo no corresponde, el armado se deshabilita y la
 * conversación muestra glosas sueltas; el reconocimiento sigue funcionando.
 *
 * Rutas esperadas en assets:
 *   lsa/lexico_senas.json
 *   lsa/plantillas_frases.json
 */
sealed class PhraseAssets {

    /** Crea el armador con el umbral vigente, que puede cambiar durante la sesión. */
    abstract fun builder(threshold: Float): PhraseBuilder

    class Ready internal constructor(
        private val lexicon: Lexicon,
        private val templates: TemplateSet
    ) : PhraseAssets() {
        /** Armado con plantillas: las piezas ya se verificaron como una unidad. */
        override fun builder(threshold: Float): PhraseBuilder =
            PhraseBuilder.enabled(lexicon, templates, threshold)
    }

    class Disabled internal constructor(val reason: String) : PhraseAssets() {
        /** Armado que solo devuelve glosas sueltas. */
        override fun builder(threshold: Float): PhraseBuilder =
            PhraseBuilder.disabled(reason, threshold)
    }

    companion object {
        private const val LEXICON_ASSET = "lsa/lexico_senas.json"
        private const val TEMPLATES_ASSET = "lsa/plantillas_frases.json"

        /** Carga desde los assets de la app, contra el modelo y el catálogo ya validados. */
        fun load(
            context: Context,
            manifest: ModelBundle.Manifest,
            catalog: SignCatalog
        ): PhraseAssets = load(manifest, catalog) { path -> readAsset(context, path) }

        /** Igual que [load], con la lectura inyectada para probar sin dispositivo. */
        internal fun load(
            manifest: ModelBundle.Manifest,
            catalog: SignCatalog,
            read: (String) -> ByteArray?
        ): PhraseAssets {
            val lexiconJson = read(LEXICON_ASSET)
            val templatesJson = read(TEMPLATES_ASSET)
            return when {
                lexiconJson == null -> Disabled("falta $LEXICON_ASSET")
                templatesJson == null -> Disabled("falta $TEMPLATES_ASSET")
                else -> parseAndCheck(manifest, catalog, lexiconJson, templatesJson)
            }
        }

        // Un JSON ilegible o desfasado deshabilita el armado, nunca la app.
        private fun parseAndCheck(
            manifest: ModelBundle.Manifest,
            catalog: SignCatalog,
            lexiconJson: ByteArray,
            templatesJson: ByteArray
        ): PhraseAssets {
            val (lexicon, templates) = try {
                Lexicon.parse(String(lexiconJson, Charsets.UTF_8)) to
                    TemplateSet.parse(String(templatesJson, Charsets.UTF_8))
            } catch (e: IllegalArgumentException) {
                return Disabled(e.message ?: "léxico o plantillas ilegibles")
            }
            val compatibility = PhraseCompatibility.check(
                manifest.modelVersion,
                manifest.catalogVersion,
                catalog.all(),
                lexicon,
                templates
            )
            return if (compatibility.compatible()) {
                Ready(lexicon, templates)
            } else {
                Disabled(compatibility.reason())
            }
        }

        private fun readAsset(context: Context, path: String): ByteArray? = try {
            context.assets.open(path).use { it.readBytes() }
        } catch (_: IOException) {
            null
        }
    }
}
