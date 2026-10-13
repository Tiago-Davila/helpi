package com.helpi.conversation.phrase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Frase armada para una oración, junto con las glosas que la originaron,
 * para que la persona sorda pueda verificar qué se dijo en su nombre.
 */
public final class PhraseResult {

    private final String text;
    private final List<String> glosses;
    private final List<String> appliedTemplates;

    /** Crea el resultado; las listas se copian para que sea inmutable. */
    public PhraseResult(String text, List<String> glosses, List<String> appliedTemplates) {
        this.text = Objects.requireNonNull(text);
        this.glosses = Collections.unmodifiableList(new ArrayList<>(glosses));
        this.appliedTemplates = Collections.unmodifiableList(new ArrayList<>(appliedTemplates));
    }

    /** Frase en español que se muestra y se pronuncia. */
    public String text() {
        return text;
    }

    /** Glosas originales en mayúsculas; «¿?» donde una seña quedó bajo el umbral. */
    public List<String> glosses() {
        return glosses;
    }

    /** Ids de las plantillas aplicadas, en orden; vacía si no se aplicó ninguna. */
    public List<String> appliedTemplates() {
        return appliedTemplates;
    }

    /** Indica si al menos un tramo se armó con una plantilla. */
    public boolean templateApplied() {
        return !appliedTemplates.isEmpty();
    }
}
