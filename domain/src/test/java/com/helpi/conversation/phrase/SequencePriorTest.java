package com.helpi.conversation.phrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SequencePriorTest {

    private static SequencePrior deployed() {
        return SequencePrior.parse(LsaAssets.read("contexto_oracion.json"));
    }

    private static Lexicon lexicon() {
        return Lexicon.parse(LsaAssets.read("lexico_senas.json"));
    }

    @Test
    public void alInicioElTiempoEsLoMasEsperado() {
        SequencePrior p = deployed();
        assertTrue(p.weight(null, SignCategory.PERSONA) > 1f);
        assertTrue(p.weight(null, SignCategory.TIEMPO) > p.weight(null, SignCategory.PERSONA));
        assertTrue(p.weight(null, SignCategory.TIEMPO) > p.weight(null, SignCategory.VERBO));
        assertEquals(1f, p.weight(null, SignCategory.OBJETO), 0f);
    }

    @Test
    public void ordenTiempoVerboObjeto() {
        SequencePrior p = deployed();
        assertTrue(p.weight(SignCategory.TIEMPO, SignCategory.VERBO)
                > p.weight(SignCategory.TIEMPO, SignCategory.PERSONA));
        assertTrue(p.weight(SignCategory.VERBO, SignCategory.OBJETO)
                > p.weight(SignCategory.VERBO, SignCategory.LUGAR));
    }

    @Test
    public void tiempoSeguidoDeTiempoBaja() {
        assertTrue(deployed().weight(SignCategory.TIEMPO, SignCategory.TIEMPO) < 1f);
    }

    @Test
    public void letraSeguidaDeLetraSube() {
        assertTrue(deployed().weight(SignCategory.LETRA, SignCategory.LETRA) > 1f);
    }

    @Test
    public void elAssetDeclaraLaVersionDelLexico() {
        assertEquals("3", deployed().lexiconVersion());
        assertEquals(lexicon().version(), deployed().lexiconVersion());
    }

    @Test
    public void pesosPorClaseSegunLaSenaAnterior() {
        Lexicon lex = lexicon();
        SequencePrior p = deployed();
        float[] vacio = p.weights(lex, Collections.<LexicalEntry>emptyList());
        assertEquals(lex.size(), vacio.length);
        List<LexicalEntry> previa = Collections.singletonList(lex.entry(findGloss(lex, "MANANA")));
        float[] trasManana = p.weights(lex, previa);
        // HOY (64) es TIEMPO: baja tras MAÑANA; ENCONTRAR (63) es verbo: sube
        assertTrue(vacio[64] > vacio[63]);
        assertTrue(trasManana[64] < trasManana[63]);
    }

    // Busca el índice de una glosa ignorando mayúsculas y tildes.
    private static int findGloss(Lexicon lex, String gloss) {
        for (int i = 0; i < lex.size(); i++) {
            String g = java.text.Normalizer.normalize(lex.entry(i).gloss(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").replace('ñ', 'n').replace('Ñ', 'N');
            if (g.equalsIgnoreCase(gloss)) {
                return i;
            }
        }
        throw new AssertionError("glosa no encontrada: " + gloss);
    }

    @Test
    public void neutralDaTodosUno() {
        Lexicon lex = lexicon();
        SequencePrior n = SequencePrior.neutral();
        assertNull(n.lexiconVersion());
        for (float w : n.weights(lex, Collections.singletonList(lex.entry(0)))) {
            assertEquals(1f, w, 0f);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaPesoFueraDeRango() {
        SequencePrior.parse("{\"lexicoVersion\":\"3\",\"transiciones\":{\"inicio\":{\"tiempo\":2.5}}}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaCategoriaDesconocida() {
        SequencePrior.parse("{\"lexicoVersion\":\"3\",\"transiciones\":{\"inicio\":{\"mago\":1.0}}}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaJsonRoto() {
        SequencePrior.parse("{no es json");
    }
}
