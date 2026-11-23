package com.helpi.conversation.lsa;

import java.util.List;

/** Resultado de decidir si una predicción puede publicarse como traducción. */
public final class SignDecision {
    public final boolean accepted;
    public final int classIndex;
    public final float confidence;
    /** Probabilidad cruda (sin contexto) de la clase elegida; 0 si la salida es inválida. */
    public final float rawConfidence;
    public final AcceptanceReason reason;
    public final List<Prediction> predictions;

    SignDecision(boolean accepted, int classIndex, float confidence, float rawConfidence,
            AcceptanceReason reason, List<Prediction> predictions) {
        this.accepted = accepted;
        this.classIndex = classIndex;
        this.confidence = confidence;
        this.rawConfidence = rawConfidence;
        this.reason = reason;
        this.predictions = predictions;
    }
}
