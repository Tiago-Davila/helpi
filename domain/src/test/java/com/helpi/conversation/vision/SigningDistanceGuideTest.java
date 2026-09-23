package com.helpi.conversation.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SigningDistanceGuideTest {

    @Test
    public void sinHombrosNoInventaUnaDistancia() {
        SigningDistanceGuide guide = new SigningDistanceGuide();
        assertEquals(SigningDistanceGuide.State.UNKNOWN, guide.evaluate(null));
        assertEquals(SigningDistanceGuide.State.UNKNOWN, guide.evaluate(new float[33 * 3]));
    }

    @Test
    public void distingueEscalaLejanaOptimaYCercana() {
        assertEquals(SigningDistanceGuide.State.MOVE_CLOSER, evaluateFresh(0.12f));
        assertEquals(SigningDistanceGuide.State.OPTIMAL, evaluateFresh(0.20f));
        assertEquals(SigningDistanceGuide.State.MOVE_FARTHER, evaluateFresh(0.30f));
    }

    @Test
    public void suavizaUnCuadroAislado() {
        SigningDistanceGuide guide = new SigningDistanceGuide();
        assertEquals(SigningDistanceGuide.State.OPTIMAL, guide.evaluate(poseWithSpan(0.20f)));
        assertEquals(SigningDistanceGuide.State.OPTIMAL, guide.evaluate(poseWithSpan(0.24f)));
        assertTrue(guide.smoothedShoulderSpan() < 0.225f);
    }

    @Test
    public void resetDescartaLaEscalaAnterior() {
        SigningDistanceGuide guide = new SigningDistanceGuide();
        guide.evaluate(poseWithSpan(0.20f));
        guide.reset();
        assertTrue(Float.isNaN(guide.smoothedShoulderSpan()));
        assertEquals(SigningDistanceGuide.State.MOVE_FARTHER, guide.evaluate(poseWithSpan(0.30f)));
    }

    private static SigningDistanceGuide.State evaluateFresh(float span) {
        return new SigningDistanceGuide().evaluate(poseWithSpan(span));
    }

    private static float[] poseWithSpan(float span) {
        float[] pose = new float[33 * 3];
        pose[11 * 3] = 0.5f - span / 2f;
        pose[11 * 3 + 1] = 0.4f;
        pose[11 * 3 + 2] = 0.01f;
        pose[12 * 3] = 0.5f + span / 2f;
        pose[12 * 3 + 1] = 0.4f;
        pose[12 * 3 + 2] = 0.01f;
        return pose;
    }
}
