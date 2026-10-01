package com.helpi.conversation.lsa;

import java.util.List;

/** Resultado de decidir si una predicción puede publicarse como traducción. */
public final class SignDecision {
    public final boolean accepted;
    public final int classIndex;
    public final float confidence;
    public final AcceptanceReason reason;
    public final List<Prediction> predictions;

    SignDecision(boolean accepted, int classIndex, float confidence, AcceptanceReason reason,
            List<Prediction> predictions) {
        this.accepted = accepted;
        this.classIndex = classIndex;
        this.confidence = confidence;
        this.reason = reason;
        this.predictions = predictions;
    }
}
