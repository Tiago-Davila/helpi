package com.helpi.conversation.phrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TemplateSetTest {

    private static final LexicalEntry MUJER =
            new LexicalEntry(8, "Mujer", SignCategory.PERSONA, "mujer", Gender.FEMENINO, false, null, null);
    private static final LexicalEntry DONDE =
            new LexicalEntry(27, "Donde", SignCategory.CONECTOR, "dónde", null, false, null, null);

    @Test
    public void lasPlantillasDesplegadasSonValidasYConservanElOrden() {
        TemplateSet set = TemplateSet.parse(LsaAssets.read("plantillas_frases.json"));
        assertEquals("3", set.lexiconVersion());
        assertEquals(VerbTense.PASADO, set.defaultTense());
        assertEquals(63, set.templates().size());
        assertEquals("expresion", set.templates().get(0).id());
        assertEquals("verbo", set.templates().get(2).id());
        assertEquals("deletreo", set.templates().get(62).id());
    }

    @Test
    public void unaRanuraAceptaPorCategoriaOPorGlosaLiteral() {
        PhraseTemplate byCategory = template(Arrays.asList("persona"), "{0}.");
        PhraseTemplate byGloss = template(Arrays.asList("glosa:Donde"), "¿{0}?");
        assertTrue(byCategory.slotAccepts(0, MUJER));
        assertFalse(byCategory.slotAccepts(0, DONDE));
        assertTrue(byGloss.slotAccepts(0, DONDE));
        assertFalse(byGloss.slotAccepts(0, MUJER));
        assertEquals(1, byGloss.literalCount());
        assertEquals(0, byCategory.literalCount());
        assertEquals("Donde", byGloss.literalGloss(0));
        assertNull(byCategory.literalGloss(0));
    }

    @Test
    public void admitePalabrasDeFuncionYPuntuacion() {
        template(Arrays.asList("persona", "lugar"), "¡{0.art} {0} en {1} y de la, con los o las a el para es son está!");
    }

    @Test
    public void rechazaPlantillasQueRompenLasReglas() {
        Object[][] cases = {
            {Arrays.asList("expresion"), "Quiero {0}."},
            {Arrays.asList("objeto"), "{0} quiere."},
            {Arrays.asList("objeto"), "{1}."},
            {Arrays.asList("objeto", "cualidad"), "{1.concuerda:2}."},
            {Arrays.asList("objeto"), "{0.plural}."},
            {Arrays.asList("objeto"), "{0"},
            {Arrays.asList("animal"), "{0}."},
            {Arrays.asList("glosa:"), "{0}."},
            {Arrays.<String>asList(), "."},
            {Arrays.asList("objeto"), "{0.conj}."},
            {Arrays.asList("glosa:Comprar"), "{0.conj}."},
            {Arrays.asList("persona", "verbo"), "{1.conj:2}."},
            {Arrays.asList("verbo"), "Quiero {0}."},
        };
        for (Object[] c : cases) {
            try {
                @SuppressWarnings("unchecked")
                List<String> pattern = (List<String>) c[0];
                template(pattern, (String) c[1]);
                fail("debió rechazar: " + c[0] + " -> " + c[1]);
            } catch (IllegalArgumentException expected) {
                // una plantilla inválida nunca llega al matcher
            }
        }
    }

    @Test
    public void conjugaSoloSiLaSalidaLoPide() {
        assertTrue(template(Arrays.asList("verbo"), "{0.conj}.").conjugates());
        assertTrue(template(Arrays.asList("persona", "verbo"), "{0} {1.conj:0}.").conjugates());
        assertFalse(template(Arrays.asList("verbo"), "{0}.").conjugates());
    }

    @Test
    public void elTiempoPorDefectoEsPasadoSiNoSeDeclara() {
        String one = "{\"id\":\"a\",\"patron\":[\"expresion\"],\"salida\":\"{0}.\"}";
        String head = "{\"version\":\"1\",\"lexicoVersion\":\"1\",";
        assertEquals(VerbTense.PASADO, TemplateSet.parse(head + "\"plantillas\":[" + one + "]}").defaultTense());
        assertEquals(VerbTense.FUTURO, TemplateSet.parse(
                head + "\"tiempoPorDefecto\":\"futuro\",\"plantillas\":[" + one + "]}").defaultTense());
    }

    @Test
    public void rechazaConjuntosMalformados() {
        String head = "{\"version\":\"1\",\"lexicoVersion\":\"1\",\"plantillas\":";
        String one = "{\"id\":\"a\",\"patron\":[\"expresion\"],\"salida\":\"{0}.\"}";
        String[] invalid = {
            "no es json",
            head + "[]}",
            head + "[" + one + "," + one + "]}",
            "{\"version\":\"1\",\"plantillas\":[" + one + "]}",
            "{\"version\":\"1\",\"lexicoVersion\":\"1\",\"tiempoPorDefecto\":\"siempre\",\"plantillas\":["
                + one + "]}",
        };
        for (String json : invalid) {
            try {
                TemplateSet.parse(json);
                fail("debió rechazar: " + json);
            } catch (IllegalArgumentException expected) {
                // plantillas inválidas: el armado de frases no debe arrancar
            }
        }
    }

    private static PhraseTemplate template(List<String> pattern, String output) {
        return new PhraseTemplate("t", pattern, output);
    }
}
