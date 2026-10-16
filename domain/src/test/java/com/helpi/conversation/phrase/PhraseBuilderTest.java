package com.helpi.conversation.phrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class PhraseBuilderTest {

    private static final float THRESHOLD = 0.7f;
    private static final float HIGH = 0.95f;
    private static final float LOW = 0.4f;

    private static final Lexicon LEXICON = Lexicon.parse(LsaAssets.read("lexico_senas.json"));
    private static final TemplateSet TEMPLATES = TemplateSet.parse(LsaAssets.read("plantillas_frases.json"));
    private static final PhraseBuilder BUILDER = PhraseBuilder.enabled(LEXICON, TEMPLATES, THRESHOLD);

    @Test
    public void tablaDeGlosasAFrase() {
        String[][] table = {
            // glosas (* = bajo el umbral), frase, glosas mostradas, plantillas aplicadas
            // cada plantilla
            {"Gracias", "Gracias.", "GRACIAS", "expresion"},
            {"Donde", "¿Dónde?", "DONDE", "donde"},
            {"Mujer Comprar Leche", "La mujer: comprar la leche.", "MUJER COMPRAR LECHE", "persona-verbo-objeto"},
            {"Hijo Correr", "El hijo: correr.", "HIJO CORRER", "persona-verbo"},
            {"Comprar Comida", "Comprar la comida.", "COMPRAR COMIDA", "verbo-objeto"},
            {"Leche Amargo", "La leche amarga.", "LECHE AMARGO", "objeto-cualidad"},
            {"Hombre Hambriento", "El hombre hambriento.", "HOMBRE HAMBRIENTO", "persona-cualidad"},
            // concordancia de género
            {"Mujer Sordo", "La mujer sorda.", "MUJER SORDO", "persona-cualidad"},
            {"Hombre Sordo", "El hombre sordo.", "HOMBRE SORDO", "persona-cualidad"},
            {"Agua Rojo", "El agua roja.", "AGUA ROJO", "objeto-cualidad"},
            {"Barco Verde", "El barco verde.", "BARCO VERDE", "objeto-cualidad"},
            // sin match: glosas tal cual
            {"Correr Lejos", "CORRER LEJOS", "CORRER LEJOS", ""},
            {"Argentina", "ARGENTINA", "ARGENTINA", ""},
            // tramos parciales
            {"Mujer Sordo Correr", "La mujer sorda. CORRER", "MUJER SORDO CORRER", "persona-cualidad"},
            {"Gracias Mujer Comprar Leche", "Gracias. La mujer: comprar la leche.",
                "GRACIAS MUJER COMPRAR LECHE", "expresion persona-verbo-objeto"},
            {"Hambriento", "HAMBRIENTO", "HAMBRIENTO", ""},
            // hueco por baja confianza: ninguna plantilla, la adivinanza no se muestra
            {"Mujer *Comprar Leche", "MUJER ¿? LECHE", "MUJER ¿? LECHE", ""},
            {"*Gracias", "¿?", "¿?", ""},
        };
        for (String[] row : table) {
            PhraseResult result = BUILDER.build(sentence(row[0]));
            assertEquals(row[0], row[1], result.text());
            assertEquals(row[0], row[2], String.join(" ", result.glosses()));
            assertEquals(row[0], row[3], String.join(" ", result.appliedTemplates()));
            assertEquals(row[0], !row[3].isEmpty(), result.templateApplied());
        }
    }

    @Test
    public void conVersionIncompatibleMuestraGlosasSueltas() {
        PhraseBuilder disabled = PhraseBuilder.disabled("léxico para otro catálogo", THRESHOLD);
        assertFalse(disabled.isEnabled());
        assertEquals("léxico para otro catálogo", disabled.disabledReason());

        PhraseResult result = disabled.build(sentence("Mujer Sordo"));
        assertEquals("MUJER SORDO", result.text());
        assertFalse(result.templateApplied());

        PhraseResult gap = disabled.build(sentence("Mujer *Sordo"));
        assertEquals("MUJER ¿?", gap.text());
    }

    @Test
    public void unIndiceQueNoCorrespondeASuGlosaNoUsaPlantillas() {
        // Mujer (8) con el índice de Rojo (1): un corrimiento que la versión no detectó
        PhraseResult result = BUILDER.build(Arrays.asList(
                new RecognizedSign(1, "Mujer", HIGH), new RecognizedSign(41, "Sordo", HIGH)));
        assertEquals("MUJER SORDO", result.text());
        assertFalse(result.templateApplied());

        PhraseResult unknown = BUILDER.build(Collections.singletonList(new RecognizedSign(99, "X", HIGH)));
        assertEquals("X", unknown.text());
    }

    @Test
    public void elArmadoHabilitadoInformaSuEstado() {
        assertTrue(BUILDER.isEnabled());
        assertNull(BUILDER.disabledReason());
    }

    @Test
    public void rechazaOracionVaciaYUmbralInvalido() {
        try {
            BUILDER.build(Collections.<RecognizedSign>emptyList());
            fail("debió rechazar la oración vacía");
        } catch (IllegalArgumentException expected) {
            // detectar el fin de la oración no es responsabilidad del armado
        }
        for (float threshold : new float[] {0f, 1f}) {
            try {
                PhraseBuilder.disabled("x", threshold);
                fail("debió rechazar el umbral " + threshold);
            } catch (IllegalArgumentException expected) {
                // mismo rango que SignAcceptancePolicy
            }
        }
    }

    // "Mujer *Comprar" -> Mujer con confianza alta, Comprar bajo el umbral.
    private static List<RecognizedSign> sentence(String glosses) {
        List<RecognizedSign> signs = new ArrayList<>();
        for (String token : glosses.split(" ")) {
            boolean low = token.startsWith("*");
            String gloss = low ? token.substring(1) : token;
            signs.add(new RecognizedSign(indexOf(gloss), gloss, low ? LOW : HIGH));
        }
        return signs;
    }

    private static int indexOf(String gloss) {
        for (int i = 0; i < LEXICON.size(); i++) {
            if (LEXICON.entry(i).gloss().equals(gloss)) {
                return i;
            }
        }
        throw new IllegalArgumentException("glosa fuera del léxico: " + gloss);
    }
}
