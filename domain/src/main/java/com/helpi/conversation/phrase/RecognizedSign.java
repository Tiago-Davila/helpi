package com.helpi.conversation.phrase;

import java.util.Objects;

/** Una seña de la oración cerrada, tal como la entregó el clasificador. */
public final class RecognizedSign {

    private final int classIndex;
    private final String gloss;
    private final float confidence;

    /** Crea el reconocimiento con el índice de clase, su glosa de catálogo y la confianza. */
    public RecognizedSign(int classIndex, String gloss, float confidence) {
        this.classIndex = classIndex;
        this.gloss = Objects.requireNonNull(gloss);
        this.confidence = confidence;
    }

    /** Índice de clase devuelto por el modelo. */
    public int classIndex() {
        return classIndex;
    }

    /** Glosa del catálogo para ese índice. */
    public String gloss() {
        return gloss;
    }

    /** Confianza de la predicción, entre 0 y 1. */
    public float confidence() {
        return confidence;
    }
}
