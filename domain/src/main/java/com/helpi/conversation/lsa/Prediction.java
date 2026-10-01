package com.helpi.conversation.lsa;

/** Opción del top 3 con su confianza; no implica que la seña haya sido aceptada. */
public final class Prediction {
    public final int classIndex;
    public final float confidence;

    Prediction(int classIndex, float confidence) {
        this.classIndex = classIndex;
        this.confidence = confidence;
    }
}
