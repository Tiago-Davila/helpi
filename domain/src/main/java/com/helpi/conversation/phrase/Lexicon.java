package com.helpi.conversation.phrase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Léxico de las señas del catálogo, indexado igual que la salida del modelo.
 * Declara a qué modelo y catálogo corresponde para poder validar la unidad.
 */
public final class Lexicon {

    private final String version;
    private final String modelVersion;
    private final String catalogVersion;
    private final List<LexicalEntry> entries;

    private Lexicon(String version, String modelVersion, String catalogVersion,
            List<LexicalEntry> entries) {
        this.version = version;
        this.modelVersion = modelVersion;
        this.catalogVersion = catalogVersion;
        this.entries = Collections.unmodifiableList(entries);
    }

    /** Versión propia del léxico, a la que apuntan las plantillas. */
    public String version() {
        return version;
    }

    /** Versión del modelo para la que se escribió el léxico. */
    public String modelVersion() {
        return modelVersion;
    }

    /** Versión del catálogo para la que se escribió el léxico. */
    public String catalogVersion() {
        return catalogVersion;
    }

    /** Cantidad de entradas; debe coincidir con las clases del modelo. */
    public int size() {
        return entries.size();
    }

    /** Entrada de un índice de clase; null si el índice no existe. */
    public LexicalEntry entry(int classIndex) {
        if (classIndex < 0 || classIndex >= entries.size()) {
            return null;
        }
        return entries.get(classIndex);
    }

    /** Lee lexico_senas.json y rechaza datos incompletos en lugar de adivinar. */
    public static Lexicon parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray array = root.getJSONArray("entradas");
            List<LexicalEntry> entries = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                entries.add(parseEntry(array.getJSONObject(i), i));
            }
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("léxico vacío");
            }
            return new Lexicon(root.getString("version"), root.getString("modelVersion"),
                    root.getString("catalogVersion"), entries);
        } catch (JSONException e) {
            throw new IllegalArgumentException("léxico ilegible: " + e.getMessage(), e);
        }
    }

    // Las entradas van en orden de índice: así entry(i) es la clase i del modelo.
    private static LexicalEntry parseEntry(JSONObject o, int position) {
        int index = o.getInt("indice");
        if (index != position) {
            throw new IllegalArgumentException(
                    "índice " + index + " en la posición " + position + ": se esperaba contiguo");
        }
        SignCategory category = SignCategory.fromJson(o.getString("categoria"));
        Gender gender = o.has("genero") ? Gender.fromJson(o.getString("genero")) : null;
        if (gender == null && (category == SignCategory.PERSONA || category == SignCategory.OBJETO)) {
            throw new IllegalArgumentException("sustantivo sin género en el índice " + index);
        }
        String form = o.getString("forma");
        if (form.trim().isEmpty()) {
            throw new IllegalArgumentException("forma vacía en el índice " + index);
        }
        return new LexicalEntry(index, o.getString("glosa"), category, form, gender,
                o.optBoolean("plural", false), optString(o, "femenino"), optString(o, "articulo"));
    }

    private static String optString(JSONObject o, String key) {
        return o.has(key) ? o.getString(key) : null;
    }
}
