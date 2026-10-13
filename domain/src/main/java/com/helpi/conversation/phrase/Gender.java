package com.helpi.conversation.phrase;

/** Género gramatical de sustantivos; decide artículo y concordancia de adjetivos. */
public enum Gender {
    MASCULINO, FEMENINO;

    /** Traduce la marca "m" o "f" del léxico; cualquier otra cosa es un error de datos. */
    public static Gender fromJson(String mark) {
        switch (mark) {
            case "m":
                return MASCULINO;
            case "f":
                return FEMENINO;
            default:
                throw new IllegalArgumentException("género desconocido: " + mark);
        }
    }
}
