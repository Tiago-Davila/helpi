package com.helpi.conversation.lsa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SignAcceptancePolicyTest {

    @Test
    public void aceptaSobreElUmbral() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, true, 3);
        SignAcceptancePolicy.Decision d = p.evaluate(new float[] {0.02f, 0.95f, 0.03f});
        assertTrue(d.accepted);
        assertEquals(1, d.classIndex);
        assertEquals(SignAcceptancePolicy.Reason.ACCEPTED, d.reason);
    }

    @Test
    public void bajoElUmbralNoSeFuerzaLaClaseMasCercana() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, true, 3);
        SignAcceptancePolicy.Decision d = p.evaluate(new float[] {0.3f, 0.5f, 0.2f});
        assertFalse(d.accepted);
        assertEquals(SignAcceptancePolicy.Reason.BELOW_THRESHOLD, d.reason);
    }

    @Test
    public void logitsRecibenSoftmaxUnaSolaVez() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, false, 3);
        // logits muy separados -> probabilidad ~1
        SignAcceptancePolicy.Decision d = p.evaluate(new float[] {-10f, 10f, -10f});
        assertTrue(d.accepted);
        assertEquals(1, d.classIndex);
        assertTrue(d.confidence > 0.99f);
    }

    @Test
    public void salidaInvalidaSeRechaza() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, true, 3);
        assertEquals(SignAcceptancePolicy.Reason.INVALID_OUTPUT,
                p.evaluate(new float[] {0.1f, Float.NaN, 0.8f}).reason);
        assertEquals(SignAcceptancePolicy.Reason.INVALID_OUTPUT,
                p.evaluate(new float[] {0.5f, 0.5f}).reason); // tamaño incorrecto
        assertEquals(SignAcceptancePolicy.Reason.INVALID_OUTPUT,
                p.evaluate(null).reason);
    }

    @Test
    public void probabilidadesDeclaradasQueNoLoSonSeRechazan() {
        SignAcceptancePolicy p = new SignAcceptancePolicy(0.9f, true, 3);
        // el manifiesto mintió: llegaron logits
        assertEquals(SignAcceptancePolicy.Reason.INVALID_OUTPUT,
                p.evaluate(new float[] {-3f, 8f, 1f}).reason);
    }
}
