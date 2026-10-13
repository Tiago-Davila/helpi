package com.helpi.conversation.phrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class LexiconTest {

    private static Lexicon deployed() {
        return Lexicon.parse(LsaAssets.read("lexico_senas.json"));
    }

    @Test
    public void elLexicoDesplegadoCubreLas64Clases() {
        Lexicon lexicon = deployed();
        assertEquals(64, lexicon.size());
        for (int i = 0; i < lexicon.size(); i++) {
            assertEquals(i, lexicon.entry(i).index());
        }
        assertNull(lexicon.entry(64));
        assertNull(lexicon.entry(-1));
    }

    @Test
    public void losSustantivosTienenGeneroYLosVerbosNo() {
        Lexicon lexicon = deployed();
        for (int i = 0; i < lexicon.size(); i++) {
            LexicalEntry e = lexicon.entry(i);
            boolean noun = e.category() == SignCategory.PERSONA || e.category() == SignCategory.OBJETO;
            if (noun) {
                assertNotNull("sin género: " + e.gloss(), e.gender());
            }
            if (e.category() == SignCategory.VERBO) {
                assertNull("verbo con género: " + e.gloss(), e.gender());
            }
            assertTrue("forma vacía: " + e.gloss(), !e.form().trim().isEmpty());
        }
    }

    @Test
    public void conservaLosDatosDeConcordancia() {
        Lexicon lexicon = deployed();
        LexicalEntry agua = lexicon.entry(21);
        assertEquals("Agua", agua.gloss());
        assertEquals(Gender.FEMENINO, agua.gender());
        assertEquals("el", agua.article());
        assertEquals("sorda", lexicon.entry(41).feminineForm());
        assertNull(lexicon.entry(2).feminineForm());
        assertTrue(lexicon.entry(6).plural());
    }

    @Test
    public void rechazaDatosIncompletosOMalformados() {
        String head = "{\"version\":\"1\",\"modelVersion\":\"m\",\"catalogVersion\":\"c\",\"entradas\":";
        String[] invalid = {
            "no es json",
            head + "[]}",
            head + "[{\"indice\":1,\"glosa\":\"A\",\"categoria\":\"verbo\",\"forma\":\"a\"}]}",
            head + "[{\"indice\":0,\"glosa\":\"A\",\"categoria\":\"objeto\",\"forma\":\"a\"}]}",
            head + "[{\"indice\":0,\"glosa\":\"A\",\"categoria\":\"animal\",\"forma\":\"a\"}]}",
            head + "[{\"indice\":0,\"glosa\":\"A\",\"categoria\":\"objeto\",\"forma\":\"a\",\"genero\":\"x\"}]}",
            head + "[{\"indice\":0,\"glosa\":\"A\",\"categoria\":\"verbo\",\"forma\":\" \"}]}",
        };
        for (String json : invalid) {
            try {
                Lexicon.parse(json);
                fail("debió rechazar: " + json);
            } catch (IllegalArgumentException expected) {
                // dato inválido: el armado de frases no debe arrancar
            }
        }
    }
}
