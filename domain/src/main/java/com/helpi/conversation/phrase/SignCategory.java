package com.helpi.conversation.phrase;

import java.util.Locale;

/**
 * Categoría gramatical de una seña; las plantillas se escriben sobre estas categorías.
 * TIEMPO es un adverbio que encabeza la oración (hoy, ayer, mañana). LETRA es una
 * letra del abecedario: las letras seguidas se unen en una palabra deletreada.
 * CUERPO es una parte del cuerpo y SINTOMA un malestar o estado; las usa el
 * vocabulario médico de LSE.
 */
public enum SignCategory {
    PERSONA, VERBO, OBJETO, CUALIDAD, LUGAR, EXPRESION, CONECTOR, TIEMPO, LETRA, CUERPO, SINTOMA;

    /** Traduce el nombre usado en los JSON y rechaza categorías desconocidas. */
    public static SignCategory fromJson(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("categoría desconocida: " + name, e);
        }
    }
}
