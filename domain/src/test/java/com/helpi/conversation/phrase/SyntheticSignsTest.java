package com.helpi.conversation.phrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class SyntheticSignsTest {

    private static final Lexicon LEXICON = Lexicon.parse(LsaAssets.read("lexico_senas.json"));

    @Test
    public void elLexicoResuelveLosPronombresConIndicesNegativos() {
        assertEquals("yo", LEXICON.entry(SyntheticSigns.YO).form());
        assertEquals("usted", LEXICON.entry(SyntheticSigns.USTED).form());
        assertEquals("él o ella", LEXICON.entry(SyntheticSigns.EL_ELLA).form());
        for (int index : new int[] {SyntheticSigns.YO, SyntheticSigns.USTED, SyntheticSigns.EL_ELLA}) {
            assertEquals(SignCategory.PERSONA, LEXICON.entry(index).category());
            assertTrue(SyntheticSigns.isSynthetic(index));
        }
        assertNull(LEXICON.entry(-99));
        assertFalse(SyntheticSigns.isSynthetic(0));
    }

    @Test
    public void unaPlantillaPuedeNombrarUnPronombreComoGlosaLiteral() {
        TemplateSet conYo = TemplateSet.parse("{\"version\":\"1\",\"lexicoVersion\":\"" + LEXICON.version()
                + "\",\"plantillas\":[{\"id\":\"yo\",\"patron\":[\"glosa:YO\"],\"salida\":\"{0}.\"}]}");
        assertTrue(PhraseCompatibility.check(LEXICON.modelVersion(), LEXICON.catalogVersion(),
                catalog(), LEXICON, conYo).compatible());

        TemplateSet inexistente = TemplateSet.parse("{\"version\":\"1\",\"lexicoVersion\":\"" + LEXICON.version()
                + "\",\"plantillas\":[{\"id\":\"x\",\"patron\":[\"glosa:NOSOTROS\"],\"salida\":\"{0}.\"}]}");
        assertFalse(PhraseCompatibility.check(LEXICON.modelVersion(), LEXICON.catalogVersion(),
                catalog(), LEXICON, inexistente).compatible());
    }

    @Test
    public void unPronombreDetectadoEntraALaFraseConSuGlosa() {
        PhraseBuilder builder = PhraseBuilder.enabled(LEXICON,
                TemplateSet.parse(LsaAssets.read("plantillas_frases.json")), 0.7f);
        PhraseResult result = builder.build(Arrays.asList(
                new RecognizedSign(SyntheticSigns.YO, "YO", 1f),
                new RecognizedSign(60, "Correr", 0.95f)));
        assertEquals("Yo: correr.", result.text());
        assertEquals("YO CORRER", String.join(" ", result.glosses()));
        assertEquals(Collections.singletonList("persona-verbo"), result.appliedTemplates());
    }

    private static java.util.List<String> catalog() {
        java.util.List<String> glosses = new java.util.ArrayList<>();
        for (int i = 0; i < LEXICON.size(); i++) {
            glosses.add(LEXICON.entry(i).gloss());
        }
        return glosses;
    }
}
