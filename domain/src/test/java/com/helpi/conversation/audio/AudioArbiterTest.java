package com.helpi.conversation.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class AudioArbiterTest {

    private static final class Recorder implements AudioArbiter.Listener {
        final List<String> events = new ArrayList<>();

        @Override
        public void requestCloseSttGate() {
            events.add("close");
        }

        @Override
        public void openSttGate() {
            events.add("open");
        }

        @Override
        public void speak(long id, String text) {
            events.add("speak:" + id);
        }

        @Override
        public void expired(long id) {
            events.add("expired:" + id);
        }
    }

    private Recorder rec;
    private AudioArbiter arbiter;

    @Before
    public void setUp() {
        rec = new Recorder();
        arbiter = new AudioArbiter(rec, 600, 400, 5000, 15000, 3);
    }

    @Test
    public void noHablaSinCerrarPrimeroLaCompuerta() {
        arbiter.enqueue(1, "casa", 0);
        // demora de cancelación: aún nada
        assertTrue(rec.events.isEmpty());
        arbiter.tick(600);
        assertEquals(List.of("close"), rec.events);
        assertEquals(AudioArbiter.State.WAITING_GATE, arbiter.state());
        // habla recién tras la confirmación
        arbiter.sttGateClosed(650);
        assertEquals(List.of("close", "speak:1"), rec.events);
    }

    @Test
    public void reabreSoloDespuesDeLaGuardaAcustica() {
        arbiter.enqueue(1, "casa", 0);
        arbiter.tick(600);
        arbiter.sttGateClosed(600);
        arbiter.ttsFinished(1, 1000);
        assertEquals(AudioArbiter.State.GUARD, arbiter.state());
        arbiter.tick(1200); // guarda incompleta
        assertFalse(rec.events.contains("open"));
        arbiter.tick(1400);
        assertTrue(rec.events.contains("open"));
        assertEquals(AudioArbiter.State.IDLE, arbiter.state());
    }

    @Test
    public void esperaElFinDelTurnoDelOyente() {
        arbiter.hearingSpeechActive(true, 0);
        arbiter.enqueue(1, "casa", 0);
        arbiter.tick(700);
        assertTrue(rec.events.isEmpty());
        arbiter.hearingSpeechActive(false, 800);
        assertEquals(List.of("close"), rec.events);
    }

    @Test
    public void cancelacionDentroDeLaVentana() {
        arbiter.enqueue(1, "fideos", 0);
        assertTrue(arbiter.cancel(1));
        arbiter.tick(700);
        assertTrue(rec.events.isEmpty()); // nunca se habló
    }

    @Test
    public void colaLlenaExpiraElNuevoMensaje() {
        arbiter.hearingSpeechActive(true, 0); // bloquea el arranque
        arbiter.enqueue(1, "a", 0);
        arbiter.enqueue(2, "b", 0);
        arbiter.enqueue(3, "c", 0);
        assertFalse(arbiter.enqueue(4, "d", 0));
        assertTrue(rec.events.contains("expired:4"));
    }

    @Test
    public void mensajeVencidoEnColaNoSePronuncia() {
        arbiter.hearingSpeechActive(true, 0);
        arbiter.enqueue(1, "a", 0);
        arbiter.tick(5001);
        assertTrue(rec.events.contains("expired:1"));
        arbiter.hearingSpeechActive(false, 5100);
        assertFalse(rec.events.contains("close")); // no queda nada por decir
    }

    @Test
    public void watchdogRecuperaCallbackPerdido() {
        arbiter.enqueue(1, "a", 0);
        arbiter.tick(600);
        arbiter.sttGateClosed(600);
        // el TTS nunca llama onDone
        arbiter.tick(15600);
        assertEquals(AudioArbiter.State.GUARD, arbiter.state());
        arbiter.tick(16000);
        assertTrue(rec.events.contains("open")); // el STT no queda mudo
    }

    @Test
    public void ordenDeColaSeConserva() {
        arbiter.enqueue(1, "a", 0);
        arbiter.enqueue(2, "b", 0);
        arbiter.tick(600);
        arbiter.sttGateClosed(600);
        arbiter.ttsFinished(1, 1000);
        arbiter.tick(1400); // fin de guarda -> arranca el siguiente
        arbiter.sttGateClosed(1500);
        List<String> speaks = rec.events.stream().filter(e -> e.startsWith("speak")).toList();
        assertEquals(List.of("speak:1", "speak:2"), speaks);
    }

    @Test
    public void callbacksTardiosSeIgnoran() {
        arbiter.enqueue(1, "a", 0);
        arbiter.tick(600);
        arbiter.sttGateClosed(600);
        arbiter.ttsFinished(99, 700); // id ajeno
        assertEquals(AudioArbiter.State.SPEAKING, arbiter.state());
        arbiter.sttGateClosed(800); // confirmación duplicada
        assertEquals(AudioArbiter.State.SPEAKING, arbiter.state());
    }

    @Test
    public void resetDescartaAudioDeLaSesionAnterior() {
        arbiter.enqueue(1, "mensaje viejo", 0);
        arbiter.tick(600);
        assertEquals(AudioArbiter.State.WAITING_GATE, arbiter.state());

        arbiter.reset();
        arbiter.sttGateClosed(700); // confirmación tardía de la sesión cerrada
        arbiter.tick(2000);

        assertEquals(AudioArbiter.State.IDLE, arbiter.state());
        assertFalse(rec.events.contains("speak:1"));
    }
}
