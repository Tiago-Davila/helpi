package com.helpi.conversation.phrase;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Una plantilla: un patrón de categorías (o glosas literales) y una salida
 * con marcadores. Fuera de los marcadores solo admite palabras de función,
 * para que ninguna plantilla diga algo que la persona no señó.
 */
public final class PhraseTemplate {

    /**
     * Marcadores {i}, {i.art}, {i.yo}, {i.concuerda:j}, {i.conj} (verbo en primera persona) y
     * {i.conj:j} (verbo en tercera persona, sujeto en la ranura j). El renderer usa los
     * mismos grupos: 1 ranura, 2 modificador, 3 sustantivo que concuerda, 4 sujeto del verbo.
     */
    static final Pattern PLACEHOLDER = Pattern.compile(
            "\\{(\\d+)(?:\\.(art|yo|concuerda:(\\d+)|conj(?::(\\d+))?))?\\}");

    /**
     * Palabras que una plantilla puede agregar: artículos, preposiciones, conjunciones,
     * cópulas y el posesivo de primera persona, que es la persona por defecto del armado.
     */
    static final Set<String> FUNCTION_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "el", "la", "los", "las", "de", "a", "en", "con", "y", "o", "para",
            "es", "son", "está", "están", "soy", "estoy", "mi")));

    private static final String GLOSS_PREFIX = "glosa:";

    private final String id;
    private final List<String> pattern;
    private final String output;

    /** Crea la plantilla y la rechaza si su salida no respeta las reglas de contenido. */
    public PhraseTemplate(String id, List<String> pattern, String output) {
        this.id = Objects.requireNonNull(id);
        this.pattern = Collections.unmodifiableList(pattern);
        this.output = Objects.requireNonNull(output);
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("plantilla " + id + " sin patrón");
        }
        for (String element : pattern) {
            validateElement(element);
        }
        validateOutput();
    }

    /** Identificador estable; se informa en el resultado cuando la plantilla se aplica. */
    public String id() {
        return id;
    }

    /** Indica si la salida conjuga algún verbo; exige conjugaciones en el léxico. */
    public boolean conjugates() {
        Matcher m = PLACEHOLDER.matcher(output);
        while (m.find()) {
            if (m.group(2) != null && m.group(2).startsWith("conj")) {
                return true;
            }
        }
        return false;
    }

    /** Cantidad de señas que cubre la plantilla. */
    public int size() {
        return pattern.size();
    }

    /** Salida con marcadores, tal como figura en el JSON. */
    public String output() {
        return output;
    }

    /** Cuántos elementos del patrón son glosas literales; desempata por especificidad. */
    public int literalCount() {
        int count = 0;
        for (String element : pattern) {
            if (element.startsWith(GLOSS_PREFIX)) {
                count++;
            }
        }
        return count;
    }

    /** Glosa literal de una ranura, o null si la ranura es una categoría. */
    public String literalGloss(int slot) {
        String element = pattern.get(slot);
        return element.startsWith(GLOSS_PREFIX) ? element.substring(GLOSS_PREFIX.length()) : null;
    }

    /** Indica si una seña puede ocupar la ranura dada, por glosa o por categoría. */
    public boolean slotAccepts(int slot, LexicalEntry entry) {
        String gloss = literalGloss(slot);
        if (gloss != null) {
            return gloss.equals(entry.gloss());
        }
        return SignCategory.fromJson(pattern.get(slot)) == entry.category();
    }

    // Una categoría inexistente o una glosa vacía nunca matchearía: es un error de datos.
    private void validateElement(String element) {
        if (element.startsWith(GLOSS_PREFIX)) {
            if (element.length() == GLOSS_PREFIX.length()) {
                throw new IllegalArgumentException("plantilla " + id + ": glosa literal vacía");
            }
        } else {
            SignCategory.fromJson(element);
        }
    }

    // Marcadores en rango y, fuera de ellos, solo palabras de función y puntuación.
    private void validateOutput() {
        Matcher m = PLACEHOLDER.matcher(output);
        while (m.find()) {
            requireSlot(m.group(1));
            if (m.group(3) != null) {
                requireSlot(m.group(3));
            }
            if (m.group(2) != null && m.group(2).startsWith("conj")) {
                requireVerb(m.group(1));
            }
            if (m.group(4) != null) {
                requireSlot(m.group(4));
            }
        }
        String literal = PLACEHOLDER.matcher(output).replaceAll(" ");
        if (literal.contains("{") || literal.contains("}")) {
            throw new IllegalArgumentException("plantilla " + id + ": marcador mal formado");
        }
        for (String word : literal.split("[^\\p{L}]+")) {
            if (!word.isEmpty() && !FUNCTION_WORDS.contains(word.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "plantilla " + id + " agrega una palabra de contenido: " + word);
            }
        }
    }

    // Solo un verbo tiene conjugación; una glosa literal no garantiza la categoría.
    private void requireVerb(String digits) {
        if (!"verbo".equals(pattern.get(Integer.parseInt(digits)))) {
            throw new IllegalArgumentException("plantilla " + id + ": conj sobre una ranura que no es verbo");
        }
    }

    private void requireSlot(String digits) {
        int slot = Integer.parseInt(digits);
        if (slot >= pattern.size()) {
            throw new IllegalArgumentException("plantilla " + id + ": ranura " + slot + " fuera de rango");
        }
    }
}
