package com.helpi.conversation.lsa

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferencias de reconocimiento que sobreviven al cierre de la app: la lengua
 * de señas elegida (LSA por defecto) y el umbral de confianza de cada lengua.
 */
class SignLanguageStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    )

    /** Lengua guardada; LSA si nunca se eligió o el valor guardado es desconocido. */
    fun language(): SignLanguage =
        SignLanguage.fromCode(prefs.getString(KEY_LANGUAGE, null)) ?: SignLanguage.LSA

    /** Guarda la lengua elegida. */
    fun setLanguage(language: SignLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code()).apply()
    }

    /** Umbral guardado para la lengua, o [default] si nunca se ajustó. */
    fun threshold(language: SignLanguage, default: Float): Float =
        prefs.getFloat(thresholdKey(language), default)

    /** Guarda el umbral de una lengua sin tocar el de las otras. */
    fun setThreshold(language: SignLanguage, value: Float) {
        prefs.edit().putFloat(thresholdKey(language), value).apply()
    }

    private fun thresholdKey(language: SignLanguage) = "threshold_${language.code()}"

    companion object {
        private const val PREFS = "helpi_reconocimiento"
        private const val KEY_LANGUAGE = "sign_language"
    }
}
