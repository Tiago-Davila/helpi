package com.helpi.conversation.phrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class TemplateMatcherTest {

    private static final Lexicon LEXICON = Lexicon.parse(LsaAssets.read("lexico_senas.json"));

    private static final int ROJO = 1;
    private static final int MUJER = 8;
    private static final int LEJOS = 12;
    private static final int LECHE = 20;
    private static final int DONDE = 27;
    private static final int SORDO = 41;
    private static final int GRACIAS = 50;
    private static final int COMPRAR = 58;
    private static final int CORRER = 60;

    @Test
    public void tablaDeTramos() {
        TemplateMatcher matcher = new TemplateMatcher(set(
                "{\"id\":\"pv\",\"patron\":[\"persona\",\"verbo\"],\"salida\":\"{0}.\"}",
                "{\"id\":\"pvo\",\"patron\":[\"persona\",\"verbo\",\"objeto\"],\"salida\":\"{0}.\"}",
                "{\"id\":\"oc\",\"patron\":[\"objeto\",\"cualidad\"],\"salida\":\"{0}.\"}",
                "{\"id\":\"conector\",\"patron\":[\"conector\"],\"salida\":\"{0}.\"}",
                "{\"id\":\"donde\",\"patron\":[\"glosa:Donde\"],\"salida\":\"{0}.\"}",
                "{\"id\":\"expresion-a\",\"patron\":[\"expresion\"],\"salida\":\"{0}.\"}",
                "{\"id\":\"expresion-b\",\"patron\":[\"expresion\"],\"salida\":\"{0}.\"}"));
        Object[][] table = {
            // señas, ids de los tramos (- = seña suelta)
            {new int[] {MUJER, COMPRAR, LECHE}, "pvo"},
            {new int[] {MUJER, COMPRAR}, "pv"},
            {new int[] {MUJER, COMPRAR, LECHE, ROJO}, "pvo -"},
            {new int[] {LECHE, ROJO, MUJER, CORRER}, "oc pv"},
            {new int[] {DONDE}, "donde"},
            {new int[] {GRACIAS}, "expresion-a"},
            {new int[] {CORRER, LEJOS}, "- -"},
            {new int[] {MUJER, SORDO}, "- -"},
            {new int[] {LEJOS, MUJER, CORRER, LEJOS}, "- pv -"},
            {new int[] {}, ""},
        };
        for (Object[] row : table) {
            assertEquals(row[1], ids(matcher.segment(entries((int[]) row[0]))));
        }
    }

    @Test
    public void unaSenaSueltaConservaSuEntradaYNoTienePlantilla() {
        TemplateMatcher matcher = new TemplateMatcher(set(
                "{\"id\":\"pv\",\"patron\":[\"persona\",\"verbo\"],\"salida\":\"{0}.\"}"));
        List<TemplateMatcher.Segment> segments = matcher.segment(entries(new int[] {LEJOS, MUJER, CORRER}));
        assertEquals(2, segments.size());
        assertNull(segments.get(0).template());
        assertEquals("Lejos", segments.get(0).signs().get(0).gloss());
        assertEquals(2, segments.get(1).signs().size());
    }

    private static TemplateSet set(String... templates) {
        return TemplateSet.parse("{\"version\":\"1\",\"lexicoVersion\":\"1\",\"plantillas\":["
                + String.join(",", templates) + "]}");
    }

    private static String ids(List<TemplateMatcher.Segment> segments) {
        List<String> ids = new ArrayList<>();
        for (TemplateMatcher.Segment s : segments) {
            ids.add(s.template() == null ? "-" : s.template().id());
        }
        return String.join(" ", ids);
    }

    private static List<LexicalEntry> entries(int[] indices) {
        List<LexicalEntry> list = new ArrayList<>();
        for (int index : indices) {
            list.add(LEXICON.entry(index));
        }
        return list;
    }
}
