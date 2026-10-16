package com.helpi.conversation.phrase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Convierte una oración cerrada en una frase. Ante cualquier duda (seña
 * bajo el umbral, versión incompatible, índice desconocido) devuelve las
 * glosas sueltas: preferimos una frase tosca a una frase equivocada.
 */
public final class PhraseBuilder {

    /** Marca de una seña que no superó el umbral; nunca se muestra la adivinanza. */
    public static final String GAP = "¿?";

    private final Lexicon lexicon;
    private final TemplateMatcher matcher;
    private final float threshold;
    private final String disabledReason;

    private PhraseBuilder(Lexicon lexicon, TemplateMatcher matcher, float threshold, String disabledReason) {
        if (threshold <= 0f || threshold >= 1f) {
            throw new IllegalArgumentException("umbral fuera de (0,1): " + threshold);
        }
        this.lexicon = lexicon;
        this.matcher = matcher;
        this.threshold = threshold;
        this.disabledReason = disabledReason;
    }

    /** Armado habilitado; quien lo crea ya verificó la compatibilidad de las piezas. */
    public static PhraseBuilder enabled(Lexicon lexicon, TemplateSet templates, float threshold) {
        return new PhraseBuilder(lexicon, new TemplateMatcher(templates), threshold, null);
    }

    /** Armado deshabilitado: solo muestra glosas sueltas y conserva el motivo. */
    public static PhraseBuilder disabled(String reason, float threshold) {
        return new PhraseBuilder(null, null, threshold, reason);
    }

    /** Indica si las plantillas pueden aplicarse. */
    public boolean isEnabled() {
        return disabledReason == null;
    }

    /** Motivo por el que el armado está deshabilitado; null si está habilitado. */
    public String disabledReason() {
        return disabledReason;
    }

    /** Arma la frase de una oración ya cerrada, en el orden en que se señó. */
    public PhraseResult build(List<RecognizedSign> sentence) {
        if (sentence.isEmpty()) {
            throw new IllegalArgumentException("oración vacía");
        }
        List<String> glosses = glosses(sentence);
        List<LexicalEntry> entries = isEnabled() ? entries(sentence) : null;
        if (entries == null) {
            return new PhraseResult(String.join(" ", glosses), glosses, Collections.<String>emptyList());
        }
        List<String> pieces = new ArrayList<>();
        List<String> applied = new ArrayList<>();
        for (TemplateMatcher.Segment segment : matcher.segment(entries)) {
            if (segment.template() == null) {
                pieces.add(notation(segment.signs().get(0).gloss()));
            } else {
                pieces.add(TemplateRenderer.render(segment.template(), segment.signs()));
                applied.add(segment.template().id());
            }
        }
        return new PhraseResult(String.join(" ", pieces), glosses, applied);
    }

    // Lo que se muestra debajo de la frase: glosa en mayúsculas o el hueco.
    private List<String> glosses(List<RecognizedSign> sentence) {
        List<String> glosses = new ArrayList<>(sentence.size());
        for (RecognizedSign sign : sentence) {
            glosses.add(sign.confidence() < threshold ? GAP : notation(sign.gloss()));
        }
        return glosses;
    }

    // null si alguna seña no permite usar plantillas: hueco o índice que no coincide.
    private List<LexicalEntry> entries(List<RecognizedSign> sentence) {
        List<LexicalEntry> entries = new ArrayList<>(sentence.size());
        for (RecognizedSign sign : sentence) {
            LexicalEntry entry = lexicon.entry(sign.classIndex());
            if (sign.confidence() < threshold || entry == null || !entry.gloss().equals(sign.gloss())) {
                return null;
            }
            entries.add(entry);
        }
        return entries;
    }

    // Glosa en mayúsculas: convención de glosado que marca lo que no se construyó.
    private static String notation(String gloss) {
        return gloss.toUpperCase(Locale.ROOT);
    }
}
