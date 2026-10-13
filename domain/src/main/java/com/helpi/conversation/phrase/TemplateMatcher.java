package com.helpi.conversation.phrase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parte una oración en tramos. En cada posición elige la plantilla más
 * larga y, a igual largo, la más específica; si ninguna matchea, la seña
 * queda suelta. Nunca fuerza una plantilla.
 */
public final class TemplateMatcher {

    private final List<PhraseTemplate> templates;

    /** Usa las plantillas en el orden declarado, que es el último desempate. */
    public TemplateMatcher(TemplateSet templateSet) {
        this.templates = templateSet.templates();
    }

    /** Recorre la oración de izquierda a derecha y devuelve los tramos en orden. */
    public List<Segment> segment(List<LexicalEntry> signs) {
        List<Segment> segments = new ArrayList<>();
        int position = 0;
        while (position < signs.size()) {
            PhraseTemplate best = bestAt(signs, position);
            int length = best == null ? 1 : best.size();
            segments.add(new Segment(best, signs.subList(position, position + length)));
            position += length;
        }
        return Collections.unmodifiableList(segments);
    }

    // Más larga gana; luego más glosas literales; luego la primera del archivo.
    private PhraseTemplate bestAt(List<LexicalEntry> signs, int position) {
        PhraseTemplate best = null;
        for (PhraseTemplate candidate : templates) {
            if (matchesAt(candidate, signs, position) && beats(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean beats(PhraseTemplate candidate, PhraseTemplate best) {
        if (best == null) {
            return true;
        }
        if (candidate.size() != best.size()) {
            return candidate.size() > best.size();
        }
        return candidate.literalCount() > best.literalCount();
    }

    private static boolean matchesAt(PhraseTemplate template, List<LexicalEntry> signs, int position) {
        if (position + template.size() > signs.size()) {
            return false;
        }
        for (int slot = 0; slot < template.size(); slot++) {
            if (!template.slotAccepts(slot, signs.get(position + slot))) {
                return false;
            }
        }
        return true;
    }

    /** Tramo consecutivo de la oración; sin plantilla si ninguna lo cubrió. */
    static final class Segment {

        private final PhraseTemplate template;
        private final List<LexicalEntry> signs;

        Segment(PhraseTemplate template, List<LexicalEntry> signs) {
            this.template = template;
            this.signs = Collections.unmodifiableList(new ArrayList<>(signs));
        }

        /** Plantilla aplicada, o null si el tramo es una seña suelta. */
        PhraseTemplate template() {
            return template;
        }

        /** Señas del tramo, en el orden en que se hicieron. */
        List<LexicalEntry> signs() {
            return signs;
        }
    }
}
