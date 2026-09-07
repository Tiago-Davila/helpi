package com.helpi.conversation.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SignSegmenterTest {

    private static final long STEP = 33; // ~30 fps, pero el segmentador usa dt real

    private long ts = 0;

    private FrameObservation rest() {
        // manos abajo y quietas
        return obs(true,
                new HandObservation(true, 0.05f, 1.5f, false),
                new HandObservation(true, 0.05f, 1.5f, false));
    }

    private FrameObservation signing() {
        return obs(true,
                new HandObservation(true, 1.5f, -0.2f, false),
                new HandObservation(true, 1.4f, -0.1f, false));
    }

    private FrameObservation quietElevated() {
        return obs(true,
                new HandObservation(true, 0.05f, -0.2f, false),
                new HandObservation(true, 0.05f, -0.1f, false));
    }

    private FrameObservation lost() {
        return obs(false, HandObservation.ABSENT, HandObservation.ABSENT);
    }

    private FrameObservation obs(boolean shoulders, HandObservation l, HandObservation r) {
        ts += STEP;
        return new FrameObservation(ts, shoulders, l, r);
    }

    private SegmentEvent feed(SignSegmenter s, java.util.function.Supplier<FrameObservation> f, int n) {
        SegmentEvent last = SegmentEvent.NONE;
        for (int i = 0; i < n; i++) {
            SegmentEvent e = s.process(f.get());
            if (e.type != SegmentEvent.Type.NONE) {
                last = e;
            }
        }
        return last;
    }

    @Test
    public void seArmaSoloTrasReposoEstable() {
        SignSegmenter s = new SignSegmenter(SegmenterConfig.defaults());
        // movimiento inicial de acomodarse: no arma ni clasifica
        assertEquals(SegmentEvent.Type.NONE, feed(s, this::signing, 30).type);
        // 500 ms de reposo
        assertEquals(SegmentEvent.Type.ARMED, feed(s, this::rest, 20).type);
        assertTrue(s.isArmed());
    }

    @Test
    public void cicloCompletoReposoSenaFin() {
        SignSegmenter s = new SignSegmenter(SegmenterConfig.defaults());
        feed(s, this::rest, 20);
        SegmentEvent started = feed(s, this::signing, 10);
        assertEquals(SegmentEvent.Type.STARTED, started.type);
        assertTrue(s.isCapturing());

        // seña de ~1 s y quietud elevada de 600 ms
        feed(s, this::signing, 30);
        SegmentEvent ended = feed(s, this::quietElevated, 25);
        assertEquals(SegmentEvent.Type.ENDED, ended.type);
        assertTrue(ended.segmentEndMs > ended.segmentStartMs);
        // duración plausible del segmento
        assertTrue(ended.segmentEndMs - ended.segmentStartMs >= 300);
    }

    @Test
    public void elInicioIncluyePreRoll() {
        SignSegmenter s = new SignSegmenter(SegmenterConfig.defaults());
        feed(s, this::rest, 20);
        long beforeMotion = ts;
        SegmentEvent started = feed(s, this::signing, 10);
        assertEquals(SegmentEvent.Type.STARTED, started.type);
        assertTrue(started.segmentStartMs <= beforeMotion);
    }

    @Test
    public void pausaInternaCortaNoCierraElSegmento() {
        SignSegmenter s = new SignSegmenter(SegmenterConfig.defaults());
        feed(s, this::rest, 20);
        feed(s, this::signing, 15);
        // pausa de ~300 ms (menos que endQuietElevated=600)
        feed(s, this::quietElevated, 9);
        assertTrue(s.isCapturing());
        feed(s, this::signing, 15);
        SegmentEvent ended = feed(s, this::quietElevated, 25);
        assertEquals(SegmentEvent.Type.ENDED, ended.type);
    }

    @Test
    public void perdidaDeSeguimientoAbortaTrasTolerancia() {
        SignSegmenter s = new SignSegmenter(SegmenterConfig.defaults());
        feed(s, this::rest, 20);
        feed(s, this::signing, 15);
        SegmentEvent aborted = feed(s, this::lost, 10); // > 200 ms sin persona
        assertEquals(SegmentEvent.Type.ABORTED, aborted.type);
        assertEquals(SegmentEvent.AbortReason.TRACKING_LOST, aborted.abortReason);
    }

    @Test
    public void segmentoDemasiadoLargoSeRechazaSinDividir() {
        SignSegmenter s = new SignSegmenter(SegmenterConfig.defaults());
        feed(s, this::rest, 20);
        feed(s, this::signing, 10);
        SegmentEvent aborted = feed(s, this::signing, 160); // > 4 s
        assertEquals(SegmentEvent.Type.ABORTED, aborted.type);
        assertEquals(SegmentEvent.AbortReason.TOO_LONG, aborted.abortReason);
    }

    @Test
    public void trasCerrarExigeRearmadoPorReposo() {
        SignSegmenter s = new SignSegmenter(SegmenterConfig.defaults());
        feed(s, this::rest, 20);
        feed(s, this::signing, 15);
        feed(s, this::quietElevated, 25); // ENDED
        // moverse inmediatamente no reabre la compuerta
        assertEquals(SegmentEvent.Type.NONE, feed(s, this::signing, 20).type);
        // reposo -> rearmado
        assertEquals(SegmentEvent.Type.ARMED, feed(s, this::rest, 20).type);
    }

    @Test
    public void reposoManosAbajoTambienArma() {
        SignSegmenter s = new SignSegmenter(SegmenterConfig.defaults());
        // manos abajo pero con algo de movimiento (> vOff)
        SegmentEvent e = feed(s, () -> obs(true,
                new HandObservation(true, 0.5f, 1.5f, false),
                new HandObservation(true, 0.5f, 1.5f, false)), 20);
        assertEquals(SegmentEvent.Type.ARMED, e.type);
    }
}
