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
            {"persona,verbo,objeto", "{0.art} {0} {1.conj:0} {2.art} {2}.",
                new int[] {MUJER, COMPRAR, LECHE}, "La mujer compró la leche."},
            {"persona,verbo", "{0.art} {0} {1.conj:0}.", new int[] {HOMBRE, BANARSE}, "El hombre se bañó."},
            {"verbo", "{0.conj}.", new int[] {BANARSE}, "Me bañé."},
            {"verbo,objeto", "{0}.", new int[] {COMPRAR, LECHE}, "Comprar."},
            // contracción de «a el» y «de el»
            {"verbo,persona", "{0.conj} a {1.art} {1}.", new int[] {COMPRAR, HOMBRE}, "Compré al hombre."},
            {"objeto,persona", "{0.art} {0} de {1.art} {1}.", new int[] {LECHE, HOMBRE}, "La leche del hombre."},
            {"verbo,persona", "{0.conj} a {1.art} {1}.", new int[] {COMPRAR, MUJER}, "Compré a la mujer."},
            {"glosa:Donde", "¿{0}?", new int[] {DONDE}, "¿Dónde?"},
            {"expresion", "{0}.", new int[] {GRACIAS}, "Gracias."},
        };
        for (Object[] row : table) {
            PhraseTemplate template = new PhraseTemplate(
                    "t", Arrays.asList(((String) row[0]).split(",")), (String) row[1]);
            assertEquals(row[3], TemplateRenderer.render(template, entries((int[]) row[2]), VerbTense.PASADO));
        }
    }

    @Test
    public void conjugaEnElTiempoYLaPersonaPedidos() {
        PhraseTemplate yo = new PhraseTemplate("t", Arrays.asList("verbo", "objeto"), "{0.conj} {1.art} {1}.");
        PhraseTemplate ella = new PhraseTemplate(
                "t", Arrays.asList("persona", "verbo", "objeto"), "{0.art} {0} {1.conj:0} {2.art} {2}.");
        Object[][] table = {
            {yo, new int[] {COMPRAR, LECHE}, VerbTense.PASADO, "Compré la leche."},
            {yo, new int[] {COMPRAR, LECHE}, VerbTense.PRESENTE, "Compro la leche."},
            {yo, new int[] {COMPRAR, LECHE}, VerbTense.FUTURO, "Compraré la leche."},
            {ella, new int[] {MUJER, COMPRAR, LECHE}, VerbTense.PRESENTE, "La mujer compra la leche."},
            {ella, new int[] {MUJER, COMPRAR, LECHE}, VerbTense.FUTURO, "La mujer comprará la leche."},
        };
        for (Object[] row : table) {
            assertEquals(row[3], TemplateRenderer.render(
                    (PhraseTemplate) row[0], entries((int[]) row[1]), (VerbTense) row[2]));
        }
    }

    @Test
    public void elYoSenaladoConjugaEnPrimeraPersonaYLosDemasEnTercera() {
        PhraseTemplate template = new PhraseTemplate(
                "t", Arrays.asList("persona", "verbo", "objeto"), "{0.art} {0} {1.conj:0} {2.art} {2}.");
        List<LexicalEntry> yo = Arrays.asList(SyntheticSigns.entry(SyntheticSigns.YO),
                LEXICON.entry(COMPRAR), LEXICON.entry(LECHE));
        List<LexicalEntry> usted = Arrays.asList(SyntheticSigns.entry(SyntheticSigns.USTED),
                LEXICON.entry(COMPRAR), LEXICON.entry(LECHE));
        assertEquals("Yo compré la leche.", TemplateRenderer.render(template, yo, VerbTense.PASADO));
        assertEquals("Yo compraré la leche.",
                TemplateRenderer.render(template, yo, VerbTense.FUTURO));
        assertEquals("Usted compró la leche.", TemplateRenderer.render(template, usted, VerbTense.PASADO));
    }

    @Test
    public void rechazaUnaCantidadDeSenasDistintaAlPatron() {
        PhraseTemplate template = new PhraseTemplate("t", Arrays.asList("objeto", "cualidad"), CUALIDAD);
        try {
            TemplateRenderer.render(template, entries(new int[] {AGUA}), VerbTense.PASADO);
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

        assertEquals("Me duele.", TemplateRenderer.render(sola, Arrays.asList(dolor), VerbTense.PASADO));
        assertEquals("Mareo.", TemplateRenderer.render(sola, Arrays.asList(mareo), VerbTense.PASADO));
        assertEquals("Me duele el estómago.",
                TemplateRenderer.render(conCuerpo, Arrays.asList(estomago, dolor), VerbTense.PASADO));
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
