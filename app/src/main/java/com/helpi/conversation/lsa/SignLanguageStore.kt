package com.helpi.conversation.lsa

import android.content.Context

/**
 * Lengua de señas elegida por la persona. Sobrevive al cierre de la app; LSA
 * si nunca se eligió o si el valor guardado ya no corresponde a una lengua.
 */
class SignLanguageStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): SignLanguage {
        val code = preferences.getString(KEY, null) ?: return SignLanguage.LSA
        return SignLanguage.values().firstOrNull { it.code() == code } ?: SignLanguage.LSA
    }

    fun save(language: SignLanguage) {
        preferences.edit().putString(KEY, language.code()).apply()
    }

    private companion object {
        const val PREFERENCES = "lengua_senas"
        const val KEY = "lengua"
    }
}
