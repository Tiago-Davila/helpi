package com.helpi.conversation.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SigningDistanceGuideTest {

    private static final int W = 960;
    private static final int H = 540;

    @Test
    public void sinHombrosNoInventaUnaDistancia() {
        SigningDistanceGuide guide = new SigningDistanceGuide();
        assertEquals(SigningDistanceGuide.State.UNKNOWN, guide.evaluate(null, W, H));
        assertEquals(SigningDistanceGuide.State.UNKNOWN, guide.evaluate(new float[33 * 3], W, H));
    }

    @Test
    public void sinTamanoDeImagenNoInventaUnaDistancia() {
        SigningDistanceGuide guide = new SigningDistanceGuide();
        assertEquals(SigningDistanceGuide.State.UNKNOWN, guide.evaluate(poseWithSpan(0.20f), 0, 0));
    }

    @Test
    public void maximoSaleDelEspacioDeSenado() {
        // 16:9 horizontal: limita el alto -> 0,5625 / 2,31
        assertEquals(0.2435f, SigningDistanceGuide.maxShoulderSpan(960, 540), 1e-3f);
        // 3:4 vertical: limita el ancho -> 1 / 3,1
        assertEquals(0.3226f, SigningDistanceGuide.maxShoulderSpan(480, 640), 1e-3f);
    }

    @Test
    public void distingueEscalaLejanaOptimaYCercana() {
        assertEquals(SigningDistanceGuide.State.MOVE_CLOSER, evaluateFresh(0.10f));
        assertEquals(SigningDistanceGuide.State.OPTIMAL, evaluateFresh(0.20f));
        assertEquals(SigningDistanceGuide.State.MOVE_FARTHER, evaluateFresh(0.30f));
    }

    @Test
    public void suavizaUnCuadroAislado() {
        SigningDistanceGuide guide = new SigningDistanceGuide();
        assertEquals(SigningDistanceGuide.State.OPTIMAL, guide.evaluate(poseWithSpan(0.20f), W, H));
        assertEquals(SigningDistanceGuide.State.OPTIMAL, guide.evaluate(poseWithSpan(0.28f), W, H));
        assertTrue(guide.smoothedShoulderSpan() < SigningDistanceGuide.maxShoulderSpan(W, H));
    }

    @Test
    public void resetDescartaLaEscalaAnterior() {
        SigningDistanceGuide guide = new SigningDistanceGuide();
        guide.evaluate(poseWithSpan(0.20f), W, H);
        guide.reset();
        assertTrue(Float.isNaN(guide.smoothedShoulderSpan()));
        assertEquals(SigningDistanceGuide.State.MOVE_FARTHER, guide.evaluate(poseWithSpan(0.30f), W, H));
    }

    private static SigningDistanceGuide.State evaluateFresh(float span) {
        return new SigningDistanceGuide().evaluate(poseWithSpan(span), W, H);
    }

    /** Hombros horizontales separados [span] del ancho de la imagen. */
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
