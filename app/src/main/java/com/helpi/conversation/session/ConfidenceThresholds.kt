package com.helpi.conversation.session

import com.helpi.conversation.lsa.SignLanguage

/** Umbral de cada lengua: el de una no se aplica al modelo de otra. */
internal class ConfidenceThresholds(language: SignLanguage) {
    private val byLanguage =
        SignLanguage.values().associateWith { it.defaultConfidenceThreshold() }.toMutableMap()

    /** Umbral vigente. */
    var current = byLanguage.getValue(language)
        private set

    /** Pasa a usar el umbral guardado de una lengua. */
    fun use(language: SignLanguage) {
        current = byLanguage.getValue(language)
    }

    /** Cambia el umbral de una lengua, dentro del rango permitido, y lo deja vigente. */
    fun set(language: SignLanguage, value: Float) {
        current = value.coerceIn(MIN, MAX)
        byLanguage[language] = current
    }

    companion object {
        /** Umbral inicial: el 50 % con el que se usa la app. */
        const val DEFAULT = 0.50f
        const val MIN = 0.50f
        const val MAX = 0.99f
    }
}
