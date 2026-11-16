package com.helpi.conversation.phrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TemplateRendererTest {

    private static final Lexicon LEXICON = Lexicon.parse(LsaAssets.read("lexico_senas.json"));

    // índices del catálogo usados en la tabla
    private static final int ROJO = 1;
    private static final int VERDE = 2;
    private static final int COLORES = 6;
    private static final int ROSA = 7;
    private static final int MUJER = 8;
    private static final int HOMBRE = 11;
    private static final int LECHE = 20;
    private static final int AGUA = 21;
    private static final int ARGENTINA = 23;
    private static final int PAIS = 25;
    private static final int DONDE = 27;
    private static final int SORDO = 41;
    private static final int GRACIAS = 50;
    private static final int ATERRIZAR = 53;
    private static final int COMPRAR = 58;
    private static final int BANARSE = 57;

    private static final String CUALIDAD = "{0.art} {0} {1.concuerda:0}.";
    private static final String VERBO_LUGAR = "{0} en {1.art} {1}.";

    @Test
    public void tablaDeConcordanciaYArticulos() {
        Object[][] table = {
            // patrón, salida, señas, frase esperada
            {"persona,cualidad", CUALIDAD, new int[] {MUJER, SORDO}, "La mujer sorda."},
            {"persona,cualidad", CUALIDAD, new int[] {HOMBRE, SORDO}, "El hombre sordo."},
            {"objeto,cualidad", CUALIDAD, new int[] {AGUA, ROJO}, "El agua roja."},
            {"objeto,cualidad", CUALIDAD, new int[] {LECHE, VERDE}, "La leche verde."},
            {"objeto,cualidad", CUALIDAD, new int[] {COLORES, ROJO}, "Los colores rojos."},
            {"objeto,cualidad", CUALIDAD, new int[] {COLORES, ROSA}, "Los colores rosas."},
            {"verbo,lugar", VERBO_LUGAR, new int[] {ATERRIZAR, ARGENTINA}, "Aterrizar en Argentina."},
            {"verbo,lugar", VERBO_LUGAR, new int[] {ATERRIZAR, PAIS}, "Aterrizar en el país."},
            {"persona,verbo,objeto", "{0.art} {0}: {1} {2.art} {2}.",
                new int[] {MUJER, COMPRAR, LECHE}, "La mujer: comprar la leche."},
            {"persona,verbo", "{0.art} {0}: {1}.", new int[] {HOMBRE, BANARSE}, "El hombre: bañarse."},
            {"glosa:Donde", "¿{0}?", new int[] {DONDE}, "¿Dónde?"},
            {"expresion", "{0}.", new int[] {GRACIAS}, "Gracias."},
        };
        for (Object[] row : table) {
            PhraseTemplate template = new PhraseTemplate(
                    "t", Arrays.asList(((String) row[0]).split(",")), (String) row[1]);
            assertEquals(row[3], TemplateRenderer.render(template, entries((int[]) row[2])));
        }
    }

    @Test
    public void rechazaUnaCantidadDeSenasDistintaAlPatron() {
        PhraseTemplate template = new PhraseTemplate("t", Arrays.asList("objeto", "cualidad"), CUALIDAD);
        try {
            TemplateRenderer.render(template, entries(new int[] {AGUA}));
            fail("debió rechazar");
        } catch (IllegalArgumentException expected) {
            // el matcher siempre entrega tantas señas como ranuras
        }
    }

    @Test
    public void laPrimeraPersonaUsaFormaYoYSinoLaFormaBase() {
        LexicalEntry dolor = new LexicalEntry(0, "Dolor", SignCategory.SINTOMA, "dolor",
                Gender.MASCULINO, false, null, null, "me duele");
        LexicalEntry mareo = new LexicalEntry(1, "Mareo", SignCategory.SINTOMA, "mareo",
                Gender.MASCULINO, false, null, null, null);
        LexicalEntry estomago = new LexicalEntry(2, "Estomago", SignCategory.CUERPO, "estómago",
                Gender.MASCULINO, false, null, null, null);
        PhraseTemplate sola = new PhraseTemplate("s", Arrays.asList("sintoma"), "{0.yo}.");
        PhraseTemplate conCuerpo = new PhraseTemplate(
                "c", Arrays.asList("cuerpo", "sintoma"), "{1.yo} {0.art} {0}.");

        assertEquals("Me duele.", TemplateRenderer.render(sola, Arrays.asList(dolor)));
        assertEquals("Mareo.", TemplateRenderer.render(sola, Arrays.asList(mareo)));
        assertEquals("Me duele el estómago.",
                TemplateRenderer.render(conCuerpo, Arrays.asList(estomago, dolor)));
    }

    @Test
    public void elMarcadorYoNoHabilitaPalabrasDeContenido() {
        new PhraseTemplate("ok", Arrays.asList("sintoma"), "{0.yo}.");
        try {
            new PhraseTemplate("mal", Arrays.asList("sintoma"), "Tengo {0.yo}.");
            fail("debió rechazar «Tengo»");
        } catch (IllegalArgumentException expected) {
            // la conjugación vive en el léxico, nunca en la plantilla
        }
    }

    private static List<LexicalEntry> entries(int[] indices) {
        List<LexicalEntry> list = new ArrayList<>();
        for (int index : indices) {
            list.add(LEXICON.entry(index));
        }
        return list;
    }
}
