package com.helpi.conversation.phrase;

import java.util.Locale;

/**
 * Tiempo verbal de una oración. Lo fija la seña de tiempo (hoy, ayer, mañana);
 * sin ella rige el tiempo por defecto que declaran las plantillas.
 */
public enum VerbTense {
    PASADO, PRESENTE, FUTURO;

    /** Traduce el nombre usado en los JSON y rechaza tiempos desconocidos. */
    public static VerbTense fromJson(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tiempo verbal desconocido: " + name, e);
        }
    }
}
