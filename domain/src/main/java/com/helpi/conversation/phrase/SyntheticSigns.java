package com.helpi.conversation.phrase;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Señas que no clasifica el modelo sino el detector de señalamiento: los
 * pronombres y las zonas del cuerpo tocadas con el índice. Usan índices
 * negativos para no chocar nunca con una clase del catálogo y son las mismas
 * para todas las lenguas.
 */
public final class SyntheticSigns {

    /** Señalarse el pecho con el índice. */
    public static final int YO = -1;
    /** Señalar hacia la cámara, es decir, al interlocutor. */
    public static final int USTED = -2;
    /** Señalar hacia un costado: una tercera persona. */
    public static final int EL_ELLA = -3;
    /** Tocarse la cabeza con el índice. */
    public static final int CABEZA = -10;
    /** Tocarse la garganta con el índice. */
    public static final int GARGANTA = -11;
    /** Tocarse el abdomen con el índice. */
    public static final int ABDOMEN = -12;

    private static final Map<Integer, LexicalEntry> ENTRIES = new HashMap<>();

    static {
        add(new LexicalEntry(YO, "YO", SignCategory.PERSONA, "yo", null, false, null, null));
        add(new LexicalEntry(USTED, "USTED", SignCategory.PERSONA, "usted", null, false, null, null));
        add(new LexicalEntry(EL_ELLA, "EL_ELLA", SignCategory.PERSONA, "él o ella", null, false, null, null));
        add(new LexicalEntry(CABEZA, "CABEZA", SignCategory.CUERPO, "cabeza", Gender.FEMENINO, false, null, null));
        add(new LexicalEntry(GARGANTA, "GARGANTA", SignCategory.CUERPO, "garganta", Gender.FEMENINO, false,
                null, null));
        add(new LexicalEntry(ABDOMEN, "ABDOMEN", SignCategory.CUERPO, "abdomen", Gender.MASCULINO, false,
                null, null));
    }

    private static final Set<String> GLOSSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "YO", "USTED", "EL_ELLA", "CABEZA", "GARGANTA", "ABDOMEN")));

    private SyntheticSigns() {
    }

    /** Indica si un índice corresponde a una seña sintética y no a una clase del modelo. */
    public static boolean isSynthetic(int index) {
        return index < 0;
    }

    /** Entrada de una seña sintética; null si el índice no es una de ellas. */
    public static LexicalEntry entry(int index) {
        return ENTRIES.get(index);
    }

    private static void add(LexicalEntry entry) {
        ENTRIES.put(entry.index(), entry);
    }

    /** Glosas válidas como literales en las plantillas, aunque no estén en el catálogo. */
    public static Set<String> glosses() {
        return GLOSSES;
    }
}
