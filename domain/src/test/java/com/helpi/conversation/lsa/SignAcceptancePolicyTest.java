package com.helpi.conversation.lsa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SignAcceptancePolicyTest {

    @Test
    public void aceptaSobreElUmbral() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, true, 3);
        SignDecision d = p.evaluate(new float[] {0.02f, 0.95f, 0.03f});
        assertTrue(d.accepted);
        assertEquals(1, d.classIndex);
        assertEquals(AcceptanceReason.ACCEPTED, d.reason);
    }

    @Test
    public void bajoElUmbralNoSeFuerzaLaClaseMasCercana() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, true, 3);
        SignDecision d = p.evaluate(new float[] {0.3f, 0.5f, 0.2f});
        assertFalse(d.accepted);
        assertEquals(AcceptanceReason.BELOW_THRESHOLD, d.reason);
    }

    @Test
    public void logitsRecibenSoftmaxUnaSolaVez() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, false, 3);
        // logits muy separados -> probabilidad ~1
        SignDecision d = p.evaluate(new float[] {-10f, 10f, -10f});
        assertTrue(d.accepted);
        assertEquals(1, d.classIndex);
        assertTrue(d.confidence > 0.99f);
    }

    @Test
    public void salidaInvalidaSeRechaza() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, true, 3);
        assertEquals(AcceptanceReason.INVALID_OUTPUT,
                p.evaluate(new float[] {0.1f, Float.NaN, 0.8f}).reason);
        assertEquals(AcceptanceReason.INVALID_OUTPUT,
                p.evaluate(new float[] {0.5f, 0.5f}).reason); // tamaño incorrecto
        assertEquals(AcceptanceReason.INVALID_OUTPUT,
                p.evaluate(null).reason);
    }

    @Test
    public void probabilidadesDeclaradasQueNoLoSonSeRechazan() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, true, 3);
        // el manifiesto mintió: llegaron logits
        assertEquals(AcceptanceReason.INVALID_OUTPUT,
                p.evaluate(new float[] {-3f, 8f, 1f}).reason);
    }

    @Test
    public void devuelveLasTresPrediccionesOrdenadasConSuConfianza() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, true, 5);
        SignDecision d =
                p.evaluate(new float[] {0.10f, 0.40f, 0.30f, 0.15f, 0.05f});

        assertEquals(3, d.predictions.size());
        assertEquals(1, d.predictions.get(0).classIndex);
        assertEquals(0.40f, d.predictions.get(0).confidence, 0f);
        assertEquals(2, d.predictions.get(1).classIndex);
        assertEquals(0.30f, d.predictions.get(1).confidence, 0f);
        assertEquals(3, d.predictions.get(2).classIndex);
        assertEquals(0.15f, d.predictions.get(2).confidence, 0f);
        assertFalse(d.accepted);
    }

    @Test
    public void salidaInvalidaNoExponePredicciones() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, true, 3);
        assertTrue(p.evaluate(new float[] {Float.NaN, 0.5f, 0.5f}).predictions.isEmpty());
    }

    @Test
    public void elPesoDesempataUnEmpateCercano() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.5f, true, 3);
        float[] scores = {0.02f, 0.50f, 0.48f};
        assertEquals(1, p.evaluate(scores).classIndex);
        SignDecision d = p.evaluate(scores, new float[] {1f, 0.5f, 1.6f});
        assertTrue(d.accepted);
        assertEquals(2, d.classIndex);
        assertEquals(0.48f, d.rawConfidence, 1e-6f);
        assertTrue(d.confidence > d.rawConfidence);
    }

    @Test
    public void contextoNoRescataUnaCrudaBajoElPiso() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.7f, true, 3);
        float[] scores = {0.45f, 0.05f, 0.5f};
        SignDecision d = p.evaluate(scores, new float[] {2f, 1f, 0.05f});
        assertEquals(0, d.classIndex);
        assertTrue(d.confidence >= 0.7f);
        assertFalse(d.accepted);
        assertEquals(AcceptanceReason.BELOW_RAW_FLOOR, d.reason);
        assertEquals(0.45f, d.rawConfidence, 1e-6f);
    }

    @Test
    public void pesosNeutrosDanLaMismaDecision() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.5f, false, 4);
        float[] scores = {2f, 1.5f, -1f, 0.3f};
        SignDecision a = p.evaluate(scores);
        SignDecision b = p.evaluate(scores, new float[] {1f, 1f, 1f, 1f});
        assertEquals(a.accepted, b.accepted);
        assertEquals(a.classIndex, b.classIndex);
        assertEquals(a.confidence, b.confidence, 1e-6f);
        assertEquals(a.rawConfidence, b.rawConfidence, 1e-6f);
        assertEquals(a.reason, b.reason);
        assertEquals(a.predictions.size(), b.predictions.size());
        for (int i = 0; i < a.predictions.size(); i++) {
            assertEquals(a.predictions.get(i).classIndex, b.predictions.get(i).classIndex);
            assertEquals(a.predictions.get(i).confidence, b.predictions.get(i).confidence, 1e-6f);
        }
    }

    @Test
    public void pesosInvalidosDanSalidaInvalida() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.5f, true, 3);
        float[] scores = {0.1f, 0.8f, 0.1f};
        assertEquals(AcceptanceReason.INVALID_OUTPUT, p.evaluate(scores, new float[] {1f, 1f}).reason);
        assertEquals(AcceptanceReason.INVALID_OUTPUT, p.evaluate(scores, new float[] {1f, 0f, 1f}).reason);
        assertEquals(AcceptanceReason.INVALID_OUTPUT, p.evaluate(scores, new float[] {1f, Float.NaN, 1f}).reason);
        assertEquals(0f, p.evaluate(scores, new float[] {1f, 0f, 1f}).rawConfidence, 0f);
    }

    @Test
    public void pesosNulosSeComportanComoSinContexto() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.5f, true, 3);
        float[] scores = {0.1f, 0.8f, 0.1f};
        assertEquals(p.evaluate(scores).confidence, p.evaluate(scores, null).confidence, 0f);
    }

    @Test
    public void rawConfidenceEsLaCrudaDeLaClaseElegida() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, true, 3);
        SignDecision d = p.evaluate(new float[] {0.02f, 0.95f, 0.03f});
        assertEquals(0.95f, d.rawConfidence, 1e-6f);
        assertEquals(d.confidence, d.rawConfidence, 1e-6f);
    }
}
