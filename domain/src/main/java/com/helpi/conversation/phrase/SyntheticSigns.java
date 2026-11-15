package com.helpi.conversation.phrase;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Señas que no clasifica el modelo sino el detector de señalamiento: los
 * pronombres. Usan índices negativos para no chocar nunca con una clase del
 * catálogo y son las mismas para todas las lenguas.
 */
public final class SyntheticSigns {

    /** Señalarse el pecho con el índice. */
    public static final int YO = -1;
    /** Señalar hacia la cámara, es decir, al interlocutor. */
    public static final int USTED = -2;
    /** Señalar hacia un costado: una tercera persona. */
    public static final int EL_ELLA = -3;

    private static final LexicalEntry[] ENTRIES = {
        new LexicalEntry(YO, "YO", SignCategory.PERSONA, "yo", null, false, null, null),
        new LexicalEntry(USTED, "USTED", SignCategory.PERSONA, "usted", null, false, null, null),
        new LexicalEntry(EL_ELLA, "EL_ELLA", SignCategory.PERSONA, "él o ella", null, false, null, null),
    };

    private static final Set<String> GLOSSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "YO", "USTED", "EL_ELLA")));

    private SyntheticSigns() {
    }

    /** Indica si un índice corresponde a una seña sintética y no a una clase del modelo. */
    public static boolean isSynthetic(int index) {
        return index < 0;
    }

    /** Entrada de una seña sintética; null si el índice no es una de ellas. */
    public static LexicalEntry entry(int index) {
        int position = -index - 1;
        if (position < 0 || position >= ENTRIES.length) {
            return null;
        }
        return ENTRIES[position];
    }

    /** Glosas válidas como literales en las plantillas, aunque no estén en el catálogo. */
    public static Set<String> glosses() {
        return GLOSSES;
    }
}
