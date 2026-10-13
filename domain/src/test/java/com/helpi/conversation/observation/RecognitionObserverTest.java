package com.helpi.conversation.observation;

import com.helpi.conversation.lsa.SignAcceptancePolicy;
import com.helpi.conversation.lsa.SignDecision;
import org.junit.Test;

public class RecognitionObserverTest {

    @Test
    public void elObservadorNuloAceptaTodosLosEventosSinEfectos() {
        SignDecision decision = new SignAcceptancePolicy(0.5f, true, 2).evaluate(new float[] {0.9f, 0.1f});
        RecognitionObserver.NO_OP.onSegmentStarted(100L);
        for (NoResultCause cause : NoResultCause.values()) {
            RecognitionObserver.NO_OP.onNoResult(cause);
        }
        RecognitionObserver.NO_OP.onDecision(decision, 0.5f, 200L);
    }
}
