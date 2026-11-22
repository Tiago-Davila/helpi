package com.helpi.conversation.lsa

import android.content.Context
import com.helpi.conversation.phrase.Lexicon
import com.helpi.conversation.phrase.PhraseBuilder
import com.helpi.conversation.phrase.PhraseCompatibility
import com.helpi.conversation.phrase.RecognizedSign
import com.helpi.conversation.phrase.SequencePrior
import com.helpi.conversation.phrase.TemplateSet
import java.io.IOException

/**
 * Léxico y plantillas del armado de frases, validados contra el modelo y el
 * catálogo cargados. Si algo no corresponde, el armado se deshabilita y la
 * conversación muestra glosas sueltas; el reconocimiento sigue funcionando.
 *
 * Rutas esperadas en assets, en la carpeta de la lengua del manifiesto:
 *   <lengua>/lexico_senas.json
 *   <lengua>/plantillas_frases.json
 *   <lengua>/contexto_oracion.json (opcional: sin él no se aplica contexto previo)
 */
sealed class PhraseAssets {

    /** Crea el armador con el umbral vigente, que puede cambiar durante la sesión. */
    abstract fun builder(threshold: Float): PhraseBuilder

    /** Pesos del contexto previo para la próxima seña, o null si no hay contexto que aplicar. */
    abstract fun contextWeights(sentence: List<RecognizedSign>): FloatArray?

    class Ready internal constructor(
        private val lexicon: Lexicon,
        private val templates: TemplateSet,
        private val prior: SequencePrior?
    ) : PhraseAssets() {
        /** Armado con plantillas: las piezas ya se verificaron como una unidad. */
        override fun builder(threshold: Float): PhraseBuilder =
            PhraseBuilder.enabled(lexicon, templates, threshold)

        /** Pesos según la última seña aceptada; una seña que no corresponde al léxico anula el contexto. */
        override fun contextWeights(sentence: List<RecognizedSign>): FloatArray? {
            val entries = sentence.mapNotNull { sign ->
                lexicon.entry(sign.classIndex())?.takeIf { it.gloss() == sign.gloss() }
            }
            return prior?.takeIf { entries.size == sentence.size }?.weights(lexicon, entries)
        }
    }

    class Disabled internal constructor(val reason: String) : PhraseAssets() {
        /** Armado que solo devuelve glosas sueltas. */
        override fun builder(threshold: Float): PhraseBuilder =
            PhraseBuilder.disabled(reason, threshold)

        /** Sin léxico validado no se sabe la categoría de cada clase: sin contexto. */
        override fun contextWeights(sentence: List<RecognizedSign>): FloatArray? = null
    }

    companion object {
        private const val LEXICON_NAME = "lexico_senas.json"
        private const val TEMPLATES_NAME = "plantillas_frases.json"
        private const val CONTEXT_NAME = "contexto_oracion.json"

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
            // La carpeta sale del manifiesto ya validado: léxico y modelo son de la misma lengua.
            val lexiconAsset = "${manifest.language}/$LEXICON_NAME"
            val templatesAsset = "${manifest.language}/$TEMPLATES_NAME"
            val lexiconJson = read(lexiconAsset)
            val templatesJson = read(templatesAsset)
            return when {
                lexiconJson == null -> Disabled("falta $lexiconAsset")
                templatesJson == null -> Disabled("falta $templatesAsset")
                else -> parseAndCheck(
                    manifest,
                    catalog,
                    lexiconJson,
                    templatesJson,
                    read("${manifest.language}/$CONTEXT_NAME")
                )
            }
        }

        // Un JSON ilegible o desfasado deshabilita el armado, nunca la app.
        private fun parseAndCheck(
            manifest: ModelBundle.Manifest,
            catalog: SignCatalog,
            lexiconJson: ByteArray,
            templatesJson: ByteArray,
            contextJson: ByteArray?
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
                Ready(lexicon, templates, parsePrior(contextJson, lexicon.version()))
            } else {
                Disabled(compatibility.reason())
            }
        }

        // El contexto solo desempata: si falta, no se lee o es de otro léxico, se reconoce sin él.
        private fun parsePrior(json: ByteArray?, lexiconVersion: String): SequencePrior? {
            val prior = json?.let {
                try {
                    SequencePrior.parse(String(it, Charsets.UTF_8))
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            return prior?.takeIf { it.lexiconVersion() == lexiconVersion }
        }

        private fun readAsset(context: Context, path: String): ByteArray? = try {
            context.assets.open(path).use { it.readBytes() }
        } catch (_: IOException) {
            null
        }
    }
}
