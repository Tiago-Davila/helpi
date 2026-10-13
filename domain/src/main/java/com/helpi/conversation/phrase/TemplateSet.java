package com.helpi.conversation.phrase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Conjunto de plantillas en el orden del archivo; ese orden desempata
 * plantillas igual de largas y específicas. Declara el léxico que asume.
 */
public final class TemplateSet {

    private final String version;
    private final String lexiconVersion;
    private final List<PhraseTemplate> templates;

    private TemplateSet(String version, String lexiconVersion, List<PhraseTemplate> templates) {
        this.version = version;
        this.lexiconVersion = lexiconVersion;
        this.templates = Collections.unmodifiableList(templates);
    }

    /** Versión propia del conjunto de plantillas. */
    public String version() {
        return version;
    }

    /** Versión del léxico sobre la que se escribieron las plantillas. */
    public String lexiconVersion() {
        return lexiconVersion;
    }

    /** Plantillas en el orden declarado. */
    public List<PhraseTemplate> templates() {
        return templates;
    }

    /** Lee plantillas_frases.json; cualquier plantilla inválida invalida el conjunto. */
    public static TemplateSet parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray array = root.getJSONArray("plantillas");
            List<PhraseTemplate> templates = new ArrayList<>(array.length());
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                PhraseTemplate template = parseTemplate(array.getJSONObject(i));
                if (!ids.add(template.id())) {
                    throw new IllegalArgumentException("plantilla repetida: " + template.id());
                }
                templates.add(template);
            }
            if (templates.isEmpty()) {
                throw new IllegalArgumentException("sin plantillas");
            }
            return new TemplateSet(root.getString("version"), root.getString("lexicoVersion"), templates);
        } catch (JSONException e) {
            throw new IllegalArgumentException("plantillas ilegibles: " + e.getMessage(), e);
        }
    }

    private static PhraseTemplate parseTemplate(JSONObject o) {
        JSONArray array = o.getJSONArray("patron");
        List<String> pattern = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            pattern.add(array.getString(i));
        }
        return new PhraseTemplate(o.getString("id"), pattern, o.getString("salida"));
    }
}
