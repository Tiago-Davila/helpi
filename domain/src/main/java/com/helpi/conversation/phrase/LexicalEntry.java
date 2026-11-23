package com.helpi.conversation.phrase;

import java.util.Objects;

/**
 * Datos lingüísticos de una seña del catálogo. Solo describe palabras: no
 * decide cómo se combinan, eso lo hacen las plantillas.
 */
public final class LexicalEntry {

    private final int index;
    private final String gloss;
    private final SignCategory category;
    private final String form;
    private final Gender gender;
    private final boolean plural;
    private final String feminineForm;
    private final String article;
    private final String firstPersonForm;
    private final Conjugation conjugation;
    private final VerbTense tense;

    /** Crea una entrada sin datos verbales; género, forma femenina y artículo pueden faltar (null). */
    public LexicalEntry(int index, String gloss, SignCategory category, String form,
            Gender gender, boolean plural, String feminineForm, String article) {
        this(index, gloss, category, form, gender, plural, feminineForm, article, null, null, null);
    }

    /** Crea una entrada con forma en primera persona y sin conjugación; los opcionales pueden faltar. */
    public LexicalEntry(int index, String gloss, SignCategory category, String form,
            Gender gender, boolean plural, String feminineForm, String article,
            String firstPersonForm) {
        this(index, gloss, category, form, gender, plural, feminineForm, article, firstPersonForm, null, null);
    }

    /** Crea una entrada completa; la conjugación es de verbos y el tiempo verbal, de señas de tiempo. */
    public LexicalEntry(int index, String gloss, SignCategory category, String form,
            Gender gender, boolean plural, String feminineForm, String article,
            String firstPersonForm, Conjugation conjugation, VerbTense tense) {
        this.firstPersonForm = firstPersonForm;
        this.index = index;
        this.gloss = Objects.requireNonNull(gloss);
        this.category = Objects.requireNonNull(category);
        this.form = Objects.requireNonNull(form);
        this.gender = gender;
        this.plural = plural;
        this.feminineForm = feminineForm;
        this.article = article;
        this.conjugation = conjugation;
        this.tense = tense;
    }

    /** Índice de clase del modelo al que corresponde la entrada. */
    public int index() {
        return index;
    }

    /** Glosa del catálogo; sirve para detectar un corrimiento de índices. */
    public String gloss() {
        return gloss;
    }

    /** Categoría con la que la seña participa en las plantillas. */
    public SignCategory category() {
        return category;
    }

    /** Forma en español; en verbos es el infinitivo, por convención. */
    public String form() {
        return form;
    }

    /** Género de un sustantivo; null si la palabra no lleva artículo. */
    public Gender gender() {
        return gender;
    }

    /** Indica si el sustantivo es plural, para elegir los/las. */
    public boolean plural() {
        return plural;
    }

    /** Forma femenina de un adjetivo; null si el adjetivo es invariable. */
    public String feminineForm() {
        return feminineForm;
    }

    /** Artículo que reemplaza al regular, como «el» en «el agua»; null si no hay. */
    public String article() {
        return article;
    }

    /** Forma conjugada para quien seña, como «me duele»; null si la seña no la tiene. */
    public String firstPersonForm() {
        return firstPersonForm;
    }

    /** Formas conjugadas de un verbo; null si el léxico no las declara. */
    public Conjugation conjugation() {
        return conjugation;
    }

    /** Tiempo verbal que impone una seña de tiempo a la oración; null si no impone ninguno. */
    public VerbTense tense() {
        return tense;
    }
}
