package com.helpi.conversation.phrase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifica que modelo, catálogo, léxico y plantillas sean la misma unidad.
 * Un desfasaje de índices no tira ningún error: traduce mal. Por eso, ante
 * cualquier diferencia, el armado de frases se deshabilita.
 */
public final class PhraseCompatibility {

    private static final PhraseCompatibility COMPATIBLE = new PhraseCompatibility(null);

    private final String reason;

    private PhraseCompatibility(String reason) {
        this.reason = reason;
    }

    /** Indica si las cuatro piezas corresponden entre sí. */
    public boolean compatible() {
        return reason == null;
    }

    /** Motivo de la incompatibilidad, para diagnóstico; null si son compatibles. */
    public String reason() {
        return reason;
    }

    /** Compara versiones declaradas y, glosa por glosa, el léxico contra el catálogo. */
    public static PhraseCompatibility check(String modelVersion, String catalogVersion,
            List<String> catalogGlosses, Lexicon lexicon, TemplateSet templates) {
        if (!lexicon.modelVersion().equals(modelVersion)) {
            return incompatible("léxico para el modelo " + lexicon.modelVersion() + ", cargado " + modelVersion);
        }
        if (!lexicon.catalogVersion().equals(catalogVersion)) {
            return incompatible(
                    "léxico para el catálogo " + lexicon.catalogVersion() + ", cargado " + catalogVersion);
        }
        if (!templates.lexiconVersion().equals(lexicon.version())) {
            return incompatible(
                    "plantillas para el léxico " + templates.lexiconVersion() + ", cargado " + lexicon.version());
        }
        String glossMismatch = glossMismatch(catalogGlosses, lexicon);
        if (glossMismatch != null) {
            return incompatible(glossMismatch);
        }
        String missingLiteral = missingLiteral(lexicon, templates);
        if (missingLiteral != null) {
            return incompatible(missingLiteral);
        }
        return COMPATIBLE;
    }

    // Las versiones pueden coincidir por error humano; la glosa de cada índice no miente.
    private static String glossMismatch(List<String> catalogGlosses, Lexicon lexicon) {
        if (catalogGlosses.size() != lexicon.size()) {
            return "catálogo con " + catalogGlosses.size() + " glosas, léxico con " + lexicon.size();
        }
        for (int i = 0; i < lexicon.size(); i++) {
            String expected = catalogGlosses.get(i);
            String actual = lexicon.entry(i).gloss();
            if (!expected.equals(actual)) {
                return "índice " + i + ": catálogo " + expected + ", léxico " + actual;
            }
        }
        return null;
    }

    // Una glosa literal que ya no existe dejaría su plantilla muerta sin avisar.
    private static String missingLiteral(Lexicon lexicon, TemplateSet templates) {
        Set<String> glosses = new HashSet<>();
        for (int i = 0; i < lexicon.size(); i++) {
            glosses.add(lexicon.entry(i).gloss());
        }
        for (PhraseTemplate template : templates.templates()) {
            for (int slot = 0; slot < template.size(); slot++) {
                String literal = template.literalGloss(slot);
                if (literal != null && !glosses.contains(literal)) {
                    return "plantilla " + template.id() + " usa la glosa inexistente " + literal;
                }
            }
        }
        return null;
    }

    private static PhraseCompatibility incompatible(String reason) {
        return new PhraseCompatibility(reason);
    }
}
