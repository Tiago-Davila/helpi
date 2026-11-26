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
    private static final float LOW = 0.1f;
    /** Bajo el umbral general pero sobre el reducido de letras y tiempo. */
    private static final float MEDIUM = 0.3f;

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
            {"Mujer Comprar Leche", "La mujer compró la leche.", "MUJER COMPRAR LECHE", "persona-verbo-objeto"},
            {"Hijo Correr", "El hijo corrió.", "HIJO CORRER", "persona-verbo"},
            {"Comprar Comida", "Compré la comida.", "COMPRAR COMIDA", "verbo-objeto"},
            {"Leche Amargo", "La leche es amarga.", "LECHE AMARGO", "objeto-cualidad"},
            {"Hombre Hambriento", "El hombre está hambriento.", "HOMBRE HAMBRIENTO", "persona-hambriento"},
            // concordancia de género
            {"Mujer Sordo", "La mujer es sorda.", "MUJER SORDO", "persona-sordo"},
            {"Hombre Sordo", "El hombre es sordo.", "HOMBRE SORDO", "persona-sordo"},
            {"Mujer Rojo", "La mujer roja.", "MUJER ROJO", "persona-cualidad"},
            {"Agua Rojo", "El agua es roja.", "AGUA ROJO", "objeto-cualidad"},
            {"Barco Verde", "El barco es verde.", "BARCO VERDE", "objeto-cualidad"},
            // primera persona por defecto, en pasado
            {"Encontrar", "Encontré.", "ENCONTRAR", "verbo"},
            {"Bañarse", "Me bañé.", "BAÑARSE", "verbo"},
            {"Dar Foto Mujer", "Di la foto a la mujer.", "DAR FOTO MUJER", "verbo-objeto-persona"},
            {"Llamar Hijo", "Llamé al hijo.", "LLAMAR HIJO", "verbo-persona"},
            {"Correr Lejos", "Corrí lejos.", "CORRER LEJOS", "verbo-lejos"},
            {"Aterrizar Argentina", "Aterricé en Argentina.", "ATERRIZAR ARGENTINA", "verbo-lugar"},
            {"Aterrizar Pais", "Aterricé en el país.", "ATERRIZAR PAIS", "verbo-lugar"},
            {"Hambriento", "Estoy hambriento.", "HAMBRIENTO", "hambriento"},
            {"Sordo", "Soy sordo.", "SORDO", "sordo"},
            // dos o tres verbos seguidos se unen con «y»
            {"Comprar Encontrar", "Compré y encontré.", "COMPRAR ENCONTRAR", "verbo-verbo"},
            {"Comprar Encontrar Leche", "Compré y encontré la leche.",
                "COMPRAR ENCONTRAR LECHE", "verbo-verbo-objeto"},
            {"Correr Bailar Bañarse", "Corrí, bailé y me bañé.", "CORRER BAILAR BAÑARSE", "verbo-verbo-verbo"},
            {"Hijo Correr Bailar", "El hijo corrió y bailó.", "HIJO CORRER BAILAR", "persona-verbo-verbo"},
            // orden de LSA (objeto antes del verbo) y varios objetos
            {"Leche Comprar", "Compré la leche.", "LECHE COMPRAR", "objeto-verbo"},
            {"Mujer Leche Comprar", "La mujer compró la leche.", "MUJER LECHE COMPRAR", "persona-objeto-verbo"},
            {"Comprar Leche Arroz", "Compré la leche y el arroz.", "COMPRAR LECHE ARROZ", "verbo-objeto-objeto"},
            {"Leche Arroz", "La leche y el arroz.", "LECHE ARROZ", "objeto-objeto"},
            // preguntas con dónde, que en LSA va al final
            {"Agua Donde", "¿Dónde está el agua?", "AGUA DONDE", "objeto-donde"},
            {"Hijo Donde", "¿Dónde está el hijo?", "HIJO DONDE", "persona-donde"},
            {"Colores Donde", "¿Dónde están los colores?", "COLORES DONDE", "colores-donde"},
            {"Donde Leche", "¿Dónde está la leche?", "DONDE LECHE", "donde-objeto"},
            // nombre deletreado
            {"Nombre A N A", "Mi nombre es Ana.", "NOMBRE A N A", "nombre-deletreo"},
            {"Nacido Argentina", "Nacido en Argentina.", "NACIDO ARGENTINA", "nacido-lugar"},
            {"Argentina", "Argentina.", "ARGENTINA", "lugar"},
            {"Pais", "El país.", "PAIS", "lugar"},
            // sin match: glosas tal cual
            {"Lejos Lejos", "Lejos. Lejos.", "LEJOS LEJOS", "lugar lugar"},
            {"Paciencia Rojo", "Paciencia. Es rojo.", "PACIENCIA ROJO", "expresion cualidad"},
            {"Donde Donde", "¿Dónde? ¿Dónde?", "DONDE DONDE", "donde donde"},
            // tramos parciales
            {"Mujer Rojo Correr", "La mujer roja. Corrí.", "MUJER ROJO CORRER", "persona-cualidad verbo"},
            {"Gracias Mujer Comprar Leche", "Gracias. La mujer compró la leche.",
                "GRACIAS MUJER COMPRAR LECHE", "expresion persona-verbo-objeto"},
            {"Opaco", "Es opaco.", "OPACO", "cualidad"},
            {"Fideo", "El fideo.", "FIDEO", "objeto"},
            {"Leche", "La leche.", "LECHE", "objeto"},
            {"Agua", "El agua.", "AGUA", "objeto"},
            {"Mujer", "La mujer.", "MUJER", "persona"},
            {"Colores", "Los colores.", "COLORES", "objeto"},
            {"Colores Opaco", "Los colores son opacos.", "COLORES OPACO", "colores-cualidad"},
            // ayuda pide algo: el verbo queda en infinitivo
            {"Ayuda Encontrar Fideo", "Ayuda para encontrar el fideo.",
                "AYUDA ENCONTRAR FIDEO", "ayuda-verbo-objeto"},
            {"Ayuda Encontrar", "Ayuda para encontrar.", "AYUDA ENCONTRAR", "ayuda-verbo"},
            {"Gracias Encontrar Fideo", "Gracias. Encontré el fideo.",
                "GRACIAS ENCONTRAR FIDEO", "expresion verbo-objeto"},
            {"Ayuda Encontrar *Fideo", "AYUDA ENCONTRAR ¿?", "AYUDA ENCONTRAR ¿?", ""},
            {"*Opaco", "¿?", "¿?", ""},
            // hueco por baja confianza: ninguna plantilla, la adivinanza no se muestra
            {"Mujer *Comprar Leche", "MUJER ¿? LECHE", "MUJER ¿? LECHE", ""},
            {"*Gracias", "¿?", "¿?", ""},
            // tiempo: encabeza la oración y fija el tiempo verbal
            {"Hoy", "Hoy.", "HOY", "tiempo"},
            {"Ayer Mujer Comprar Leche", "Ayer la mujer compró la leche.",
                "AYER MUJER COMPRAR LECHE", "tiempo-persona-verbo-objeto"},
            {"Mañana Hijo Correr", "Mañana el hijo correrá.", "MAÑANA HIJO CORRER", "tiempo-persona-verbo"},
            {"Hoy Comprar Leche", "Hoy compro la leche.", "HOY COMPRAR LECHE", "tiempo-verbo-objeto"},
            {"Ayer Correr", "Ayer corrí.", "AYER CORRER", "tiempo-verbo"},
            {"Mañana Comprar Encontrar Leche", "Mañana compraré y encontraré la leche.",
                "MAÑANA COMPRAR ENCONTRAR LECHE", "tiempo-verbo-verbo-objeto"},
            {"Mañana Bañarse", "Mañana me bañaré.", "MAÑANA BAÑARSE", "tiempo-verbo"},
            {"Hoy Dar", "Hoy doy.", "HOY DAR", "tiempo-verbo"},
            {"Mañana Encontrar", "Mañana encontraré.", "MAÑANA ENCONTRAR", "tiempo-verbo"},
            // el tiempo señado al final se lleva al principio; las glosas quedan como se señaron
            {"Comprar Leche Ayer", "Ayer compré la leche.", "COMPRAR LECHE AYER", "tiempo-verbo-objeto"},
            {"Mujer Comprar Leche Mañana", "Mañana la mujer comprará la leche.",
                "MUJER COMPRAR LECHE MAÑANA", "tiempo-persona-verbo-objeto"},
            {"Gracias Mañana Correr", "Mañana. Gracias. Correré.",
                "GRACIAS MAÑANA CORRER", "tiempo expresion verbo"},
            // deletreo: letras seguidas forman una palabra; las glosas siguen sueltas
            {"A N A", "Ana.", "A N A", "deletreo"},
            {"CH I L E", "Chile.", "CH I L E", "deletreo"},
            {"B", "B.", "B", "deletreo"},
            {"Mujer A N A", "La mujer. Ana.", "MUJER A N A", "persona deletreo"},
            {"A *N A", "A ¿? A", "A ¿? A", ""},
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
    public void letrasYTiempoSeAceptanConElUmbralReducido() {
        List<RecognizedSign> signs = Arrays.asList(
                new RecognizedSign(indexOf("Hoy"), "Hoy", MEDIUM),
                new RecognizedSign(indexOf("Comprar"), "Comprar", HIGH),
                new RecognizedSign(indexOf("Leche"), "Leche", HIGH));
        assertEquals("Hoy compro la leche.", BUILDER.build(signs).text());

        List<RecognizedSign> spelled = Arrays.asList(
                new RecognizedSign(indexOf("A"), "A", MEDIUM),
                new RecognizedSign(indexOf("N"), "N", MEDIUM),
                new RecognizedSign(indexOf("A"), "A", MEDIUM));
        assertEquals("Ana.", BUILDER.build(spelled).text());
    }

    @Test
    public void lasDemasSenasSiguenConElUmbralGeneral() {
        List<RecognizedSign> signs = Arrays.asList(
                new RecognizedSign(indexOf("Hoy"), "Hoy", HIGH),
                new RecognizedSign(indexOf("Comprar"), "Comprar", MEDIUM));
        assertEquals("HOY ¿?", BUILDER.build(signs).text());
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
