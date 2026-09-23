package com.helpi.conversation.lsa

import org.json.JSONObject

/**
 * Catálogo local de glosas. El clasificador devuelve un índice, no una
 * glosa: si el modelo se actualiza y el catálogo no, el índice pasa a
 * significar otra cosa y la app traduce mal sin arrojar ningún error.
 * Por eso el catálogo se valida contra el manifiesto (versión y clases).
 */
class SignCatalog private constructor(
    val version: String,
    private val glosses: List<String>,
) {
    val size: Int get() = glosses.size

    /** Glosa para un índice de clase; null si el índice no existe. */
    fun gloss(classIndex: Int): String? = glosses.getOrNull(classIndex)

    /** Texto determinístico autorizado: no se inventan frases. */
    fun displayText(classIndex: Int): String? =
        gloss(classIndex)?.let { "Seña reconocida: $it" }

    fun all(): List<String> = glosses

    companion object {
        /**
         * Parsea catalogo_senas.json:
         * { "version": "...", "glosas": ["...", ...] }
         */
        fun parse(json: String): SignCatalog {
            val obj = JSONObject(json)
            val version = obj.getString("version")
            val arr = obj.getJSONArray("glosas")
            require(arr.length() > 0) { "catálogo vacío" }
            val glosses = buildList {
                for (i in 0 until arr.length()) {
                    val g = arr.getString(i)
                    require(g.isNotBlank()) { "glosa vacía en índice $i" }
                    add(g)
                }
            }
            return SignCatalog(version, glosses)
        }
    }
}
