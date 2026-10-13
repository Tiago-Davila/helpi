package com.helpi.conversation.phrase;

import java.util.Locale;

/** Categoría gramatical de una seña; las plantillas se escriben sobre estas categorías. */
public enum SignCategory {
    PERSONA, VERBO, OBJETO, CUALIDAD, LUGAR, EXPRESION, CONECTOR;

    /** Traduce el nombre usado en los JSON y rechaza categorías desconocidas. */
    public static SignCategory fromJson(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("categoría desconocida: " + name, e);
        }
    }
}
