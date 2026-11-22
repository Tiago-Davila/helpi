package com.helpi.conversation.phrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class SentenceAssemblerTest {

    private static final long PAUSE = 4_000L;
    private static final long PAUSE_FAIL = 6_000L;

    @Test
    public void sinSenasNoHayNadaQueCerrar() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE, PAUSE_FAIL);
        assertTrue(assembler.isEmpty());
        assertFalse(assembler.pauseElapsed(1_000_000L));
    }

    @Test
    public void laOracionSeCierraRecienAlCumplirseLaPausaDesdeLaUltimaSena() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE, PAUSE_FAIL);
        assembler.add(new RecognizedSign(8, "Mujer", 0.95f), 1_000L);
        assembler.add(new RecognizedSign(41, "Sordo", 0.95f), 3_000L);

        assertFalse("la pausa se cuenta desde la última seña", assembler.pauseElapsed(3_000L + PAUSE - 1));
        assertTrue(assembler.pauseElapsed(3_000L + PAUSE));
    }

    @Test
    public void cerrarEntregaLasSenasEnOrdenYDejaElArmadorVacio() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE, PAUSE_FAIL);
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
    public void trasUnaFallaLaOracionCierraALos6sDesdeLaFalla() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE, PAUSE_FAIL);
        assembler.add(new RecognizedSign(8, "Mujer", 0.95f), 1_000L);
        assembler.noteFailure(2_000L);

        assertFalse(assembler.pauseElapsed(2_000L + PAUSE));
        assertFalse(assembler.pauseElapsed(2_000L + PAUSE_FAIL - 1));
        assertTrue(assembler.pauseElapsed(2_000L + PAUSE_FAIL));
    }

    @Test
    public void unaFallaNoAgregaSenas() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE, PAUSE_FAIL);
        assembler.add(new RecognizedSign(8, "Mujer", 0.95f), 1_000L);
        assembler.noteFailure(2_000L);

        assertEquals(1, assembler.signs().size());
    }

    @Test
    public void unaFallaConLaOracionVaciaNoDejaNadaQueCerrar() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE, PAUSE_FAIL);
        assembler.noteFailure(1_000L);

        assertTrue(assembler.isEmpty());
        assertFalse(assembler.pauseElapsed(1_000_000L));
    }

    @Test
    public void unaSenaDespuesDeUnaFallaVuelveALaPausaNormal() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE, PAUSE_FAIL);
        assembler.add(new RecognizedSign(8, "Mujer", 0.95f), 1_000L);
        assembler.noteFailure(2_000L);
        assembler.add(new RecognizedSign(41, "Sordo", 0.95f), 3_000L);

        assertFalse(assembler.pauseElapsed(3_000L + PAUSE - 1));
        assertTrue(assembler.pauseElapsed(3_000L + PAUSE));
    }

    @Test
    public void lasSenasEntreganUnaCopiaQueNoCambiaConLaOracion() {
        SentenceAssembler assembler = new SentenceAssembler(PAUSE, PAUSE_FAIL);
        assembler.add(new RecognizedSign(8, "Mujer", 0.95f), 1_000L);
        List<RecognizedSign> snapshot = assembler.signs();
        assembler.add(new RecognizedSign(41, "Sordo", 0.95f), 2_000L);

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));
    }

    @Test
    public void rechazaPausasNoPositivas() {
        assertThrows(IllegalArgumentException.class, () -> new SentenceAssembler(0L, PAUSE_FAIL));
        assertThrows(IllegalArgumentException.class, () -> new SentenceAssembler(PAUSE, 0L));
        assertThrows(IllegalArgumentException.class, () -> new SentenceAssembler(PAUSE, -1L));
    }
}
