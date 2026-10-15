package com.helpi.conversation.phrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class SentenceAssemblerTest {

    private static final long PAUSE = 2_500L;

    @Test
    public void sinSenasNoHayNadaQueCerrar() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE);
        assertTrue(assembler.isEmpty());
        assertFalse(assembler.pauseElapsed(1_000_000L));
    }

    @Test
    public void laOracionSeCierraRecienAlCumplirseLaPausaDesdeLaUltimaSena() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE);
        assembler.add(new RecognizedSign(8, "Mujer", 0.95f), 1_000L);
        assembler.add(new RecognizedSign(41, "Sordo", 0.95f), 3_000L);

        assertFalse("la pausa se cuenta desde la última seña", assembler.pauseElapsed(3_000L + PAUSE - 1));
        assertTrue(assembler.pauseElapsed(3_000L + PAUSE));
    }

    @Test
    public void cerrarEntregaLasSenasEnOrdenYDejaElArmadorVacio() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE);
        assembler.add(new RecognizedSign(8, "Mujer", 0.95f), 1_000L);
        assembler.add(new RecognizedSign(41, "Sordo", 0.95f), 2_000L);

        List<RecognizedSign> closed = assembler.close();

        assertEquals(2, closed.size());
        assertEquals("Mujer", closed.get(0).gloss());
        assertEquals("Sordo", closed.get(1).gloss());
        assertTrue(assembler.isEmpty());
        assertFalse(assembler.pauseElapsed(1_000_000L));
    }

    @Test
    public void unHuecoNuncaMuestraLaAdivinanzaAunqueSuperaraElUmbral() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE);
        assembler.add(new RecognizedSign(8, "Mujer", 0.95f), 1_000L);
        assembler.addGap(41, 2_000L);

        PhraseResult result = PhraseBuilder.disabled("prueba", 0.5f).build(assembler.close());

        assertEquals("MUJER ¿?", result.text());
    }

    @Test
    public void lasSenasEntreganUnaCopiaQueNoCambiaConLaOracion() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE);
        assembler.add(new RecognizedSign(8, "Mujer", 0.95f), 1_000L);
        List<RecognizedSign> snapshot = assembler.signs();
        assembler.add(new RecognizedSign(41, "Sordo", 0.95f), 2_000L);

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));
    }

    @Test
    public void rechazaUnaPausaNoPositiva() {
        assertThrows(IllegalArgumentException.class, () -> new SentenceAssembler(0L));
    }
}
