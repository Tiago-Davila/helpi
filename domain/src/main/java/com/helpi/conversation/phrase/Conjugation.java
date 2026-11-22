package com.helpi.conversation.phrase;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Formas conjugadas de un verbo, escritas a mano en el léxico: el vocabulario
 * es cerrado y tiene irregulares (di, encuentro, me bañé), así que no se infieren.
 * Cada tiempo tiene la primera y la tercera persona del singular.
 */
public final class Conjugation {

    private final Map<VerbTense, String[]> forms;

    private Conjugation(Map<VerbTense, String[]> forms) {
        this.forms = forms;
    }

    /** Forma del verbo en el tiempo dado, en primera persona o en tercera. */
    public String form(VerbTense tense, boolean thirdPerson) {
        return forms.get(tense)[thirdPerson ? 1 : 0];
    }

    /** Lee {"pasado": [yo, él], "presente": [...], "futuro": [...]}; exige los tres tiempos. */
    static Conjugation parse(JSONObject o, int index) {
        Map<VerbTense, String[]> forms = new EnumMap<>(VerbTense.class);
        for (VerbTense tense : VerbTense.values()) {
            String key = tense.name().toLowerCase(Locale.ROOT);
            JSONArray pair = o.optJSONArray(key);
            if (pair == null || pair.length() != 2) {
                throw new IllegalArgumentException(
                        "conjugación sin " + key + " [primera, tercera] en el índice " + index);
            }
            String first = pair.getString(0);
            String third = pair.getString(1);
            if (first.trim().isEmpty() || third.trim().isEmpty()) {
                throw new IllegalArgumentException("conjugación vacía en el índice " + index);
            }
            forms.put(tense, new String[] {first, third});
        }
        return new Conjugation(forms);
    }
}
