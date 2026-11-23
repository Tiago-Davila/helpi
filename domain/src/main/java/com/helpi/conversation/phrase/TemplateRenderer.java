package com.helpi.conversation.phrase;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Completa la salida de una plantilla con las señas que la ocupan. Solo
 * resuelve gramática (artículo, concordancia, conjugación, contracción,
 * mayúscula): el contenido viene siempre de las señas.
 */
public final class TemplateRenderer {

    private TemplateRenderer() {
    }

    /** Devuelve el tramo en español para las señas que matchearon la plantilla, con verbos en el tiempo dado. */
    public static String render(PhraseTemplate template, List<LexicalEntry> slots, VerbTense tense) {
        if (slots.size() != template.size()) {
            throw new IllegalArgumentException(
                    "plantilla " + template.id() + " espera " + template.size() + " señas");
        }
        Matcher m = PhraseTemplate.PLACEHOLDER.matcher(template.output());
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(resolve(m, slots, tense)));
        }
        m.appendTail(out);
        return capitalize(contract(out.toString().replaceAll(" {2,}", " ").trim()));
    }

    // Un marcador vacío (artículo de un nombre propio) deja espacios que se colapsan después.
    private static String resolve(Matcher m, List<LexicalEntry> slots, VerbTense tense) {
        LexicalEntry entry = slots.get(Integer.parseInt(m.group(1)));
        if (m.group(2) == null) {
            return entry.form();
        }
        if ("yo".equals(m.group(2))) {
            return firstPerson(entry);
        }
        if (m.group(2).startsWith("conj")) {
            return conjugate(entry, tense, m.group(4) != null
                    && !speaksForSelf(slots.get(Integer.parseInt(m.group(4)))));
        }
        if (m.group(3) == null) {
            return article(entry);
        }
        return agree(entry, slots.get(Integer.parseInt(m.group(3))));
    }

    /** Verbo en el tiempo de la oración; la compatibilidad ya exigió que tenga conjugación. */
    static String conjugate(LexicalEntry verb, VerbTense tense, boolean thirdPerson) {
        if (verb.conjugation() == null) {
            throw new IllegalStateException("verbo sin conjugación: " + verb.gloss());
        }
        return verb.conjugation().form(tense, thirdPerson);
    }

    // El pronombre YO señalado es quien seña: su verbo va en primera persona, no en tercera.
    private static boolean speaksForSelf(LexicalEntry subject) {
        return subject.index() == SyntheticSigns.YO;
    }

    // «a el» y «de el» se contraen siempre: los nombres propios no llevan artículo en el léxico.
    private static String contract(String text) {
        return text.replaceAll("\\ba el\\b", "al").replaceAll("\\bde el\\b", "del");
    }

    /** Artículo definido del sustantivo; vacío si la palabra no tiene género, como «Argentina». */
    static String article(LexicalEntry noun) {
        if (noun.gender() == null) {
            return "";
        }
        if (noun.plural()) {
            return noun.gender() == Gender.FEMENINO ? "las" : "los";
        }
        if (noun.article() != null) {
            return noun.article();
        }
        return noun.gender() == Gender.FEMENINO ? "la" : "el";
    }

    /** Forma conjugada para quien seña; sin ella, la forma base sin conjugar. */
    static String firstPerson(LexicalEntry entry) {
        return entry.firstPersonForm() != null ? entry.firstPersonForm() : entry.form();
    }

    /** Forma del adjetivo en el género y número del sustantivo al que acompaña. */
    static String agree(LexicalEntry adjective, LexicalEntry noun) {
        String form = adjective.form();
        if (noun.gender() == Gender.FEMENINO && adjective.feminineForm() != null) {
            form = adjective.feminineForm();
        }
        return noun.plural() ? pluralize(form) : form;
    }

    // Regla regular del español; alcanza para los adjetivos del vocabulario cerrado.
    private static String pluralize(String word) {
        char last = Character.toLowerCase(word.charAt(word.length() - 1));
        return "aeiouáéó".indexOf(last) >= 0 ? word + "s" : word + "es";
    }

    // La mayúscula va en la primera letra, aunque el tramo empiece con «¿».
    private static String capitalize(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                return text.substring(0, i)
                        + text.substring(i, i + 1).toUpperCase(Locale.ROOT)
                        + text.substring(i + 1);
            }
        }
        return text;
    }
}
