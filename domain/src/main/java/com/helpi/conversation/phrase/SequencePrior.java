package com.helpi.conversation.phrase;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Contexto previo de la oración: pesos de transición entre categorías (bigrama).
 * El orden típico de LSA es tiempo/persona, verbo, objeto, y nadie dice MAÑANA HOY;
 * por eso la seña anterior ajusta cuánto se espera cada categoría. Solo desempata:
 * la compuerta de aceptación nunca acepta una adivinanza por el contexto.
 */
public final class SequencePrior {

    /** Peso mínimo admitido en el JSON. */
    private static final float MIN_WEIGHT = 0.25f;
    /** Peso máximo admitido en el JSON. */
    private static final float MAX_WEIGHT = 2.0f;

    private final String lexiconVersion;
    private final Map<SignCategory, Float> start;
    private final Map<SignCategory, Map<SignCategory, Float>> rows;

    private SequencePrior(String lexiconVersion, Map<SignCategory, Float> start,
            Map<SignCategory, Map<SignCategory, Float>> rows) {
        this.lexiconVersion = lexiconVersion;
        this.start = start;
        this.rows = rows;
    }

    /** Modelo sin información: todos los pesos valen 1. */
    public static SequencePrior neutral() {
        return new SequencePrior(null, new EnumMap<>(SignCategory.class),
                new EnumMap<>(SignCategory.class));
    }

    /** Lee contexto_oracion.json y rechaza pesos fuera de rango o categorías desconocidas. */
    public static SequencePrior parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            String lexVersion = root.getString("lexicoVersion");
            JSONObject transitions = root.getJSONObject("transiciones");
            Map<SignCategory, Float> start = new EnumMap<>(SignCategory.class);
            Map<SignCategory, Map<SignCategory, Float>> rows = new EnumMap<>(SignCategory.class);
            Iterator<String> keys = transitions.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Map<SignCategory, Float> row = parseRow(transitions.getJSONObject(key));
                if ("inicio".equals(key)) {
                    start = row;
                } else {
                    rows.put(SignCategory.fromJson(key), row);
                }
            }
            return new SequencePrior(lexVersion, start, rows);
        } catch (JSONException e) {
            throw new IllegalArgumentException("contexto de oración ilegible: " + e.getMessage(), e);
        }
    }

    // Lee una fila de pesos validando categoría y rango.
    private static Map<SignCategory, Float> parseRow(JSONObject o) throws JSONException {
        Map<SignCategory, Float> row = new EnumMap<>(SignCategory.class);
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            SignCategory category = SignCategory.fromJson(key);
            double value = o.getDouble(key);
            if (Double.isNaN(value) || value < MIN_WEIGHT || value > MAX_WEIGHT) {
                throw new IllegalArgumentException("peso fuera de [0.25, 2.0] para " + key + ": " + value);
            }
            row.put(category, (float) value);
        }
        return row;
    }

    /** Versión del léxico para la que se escribió el contexto; null en el neutral. */
    public String lexiconVersion() {
        return lexiconVersion;
    }

    /** Peso de pasar de la categoría anterior (null = inicio) a la candidata; 1 si no figura. */
    public float weight(SignCategory previous, SignCategory candidate) {
        Map<SignCategory, Float> row = previous == null ? start : rows.get(previous);
        if (row == null || candidate == null) {
            return 1f;
        }
        Float w = row.get(candidate);
        return w == null ? 1f : w;
    }

    /** Pesos por clase del modelo según la última seña aceptada de la oración. */
    public float[] weights(Lexicon lexicon, List<LexicalEntry> previous) {
        SignCategory last = previous == null || previous.isEmpty()
                ? null : previous.get(previous.size() - 1).category();
        float[] out = new float[lexicon.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = weight(last, lexicon.entry(i).category());
        }
        return out;
    }
}
