package com.helpi.conversation.vision;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FramingEvaluatorTest {

    private static final HandObservation OK_HAND =
            new HandObservation(true, 0f, 0f, false);
    private static final HandObservation EDGE_HAND =
            new HandObservation(true, 0f, 0f, true);

    @Test
    public void sinPersona() {
        FramingEvaluator e = new FramingEvaluator();
        assertEquals(FramingEvaluator.Issue.SIN_PERSONA,
                e.evaluate(new FrameObservation(0, false,
                        HandObservation.ABSENT, HandObservation.ABSENT)));
    }

    @Test
    public void sinHombrosAunConManos() {
        FramingEvaluator e = new FramingEvaluator();
        assertEquals(FramingEvaluator.Issue.SIN_HOMBROS,
                e.evaluate(new FrameObservation(0, false, OK_HAND, OK_HAND)));
    }

    @Test
    public void manoPerdidaSoloSiVeniaDetectada() {
        FramingEvaluator e = new FramingEvaluator();
        // primer cuadro sin mano izquierda: no es "pérdida"
        assertEquals(FramingEvaluator.Issue.OK,
                e.evaluate(new FrameObservation(0, true, HandObservation.ABSENT, OK_HAND)));
        // aparece y luego desaparece: ahora sí
        e.evaluate(new FrameObservation(33, true, OK_HAND, OK_HAND));
        assertEquals(FramingEvaluator.Issue.MANO_PERDIDA,
                e.evaluate(new FrameObservation(66, true, HandObservation.ABSENT, OK_HAND)));
    }

    @Test
    public void manosAlBorde() {
        FramingEvaluator e = new FramingEvaluator();
        assertEquals(FramingEvaluator.Issue.MANOS_AL_BORDE,
                e.evaluate(new FrameObservation(0, true, EDGE_HAND, OK_HAND)));
    }
}
