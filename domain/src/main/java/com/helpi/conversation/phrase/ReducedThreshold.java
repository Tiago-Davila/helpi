package com.helpi.conversation.phrase;

/**
 * Umbral reducido para las señas con pocos datos de entrenamiento: las letras y las de
 * tiempo (hoy, ayer, mañana). El modelo les da poca confianza aunque las reconozca, y sin
 * ellas no se puede deletrear ni encabezar una oración. Las demás señas usan el umbral general.
 */
public final class ReducedThreshold {

    /** Umbral de aceptación para letras y tiempo. */
    public static final float VALUE = 0.20f;

    private ReducedThreshold() {
    }

    /** Indica si la categoría se acepta con el umbral reducido. */
    public static boolean applies(SignCategory category) {
        return category == SignCategory.LETRA || category == SignCategory.TIEMPO;
    }

    /** Umbral que corresponde a una categoría; nunca más exigente que el general. */
    public static float forCategory(SignCategory category, float general) {
        return applies(category) ? Math.min(VALUE, general) : general;
    }

    /** Umbral de cada clase del modelo, en el orden de índices del léxico. */
    public static float[] perClass(Lexicon lexicon, float general) {
        float[] out = new float[lexicon.size()];
        for (int i = 0; i < out.length; i++) {
            LexicalEntry entry = lexicon.entry(i);
            out[i] = entry == null ? general : forCategory(entry.category(), general);
        }
        return out;
    }
}
