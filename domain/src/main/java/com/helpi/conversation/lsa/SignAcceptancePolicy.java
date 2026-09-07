package com.helpi.conversation.lsa;

/**
 * Compuerta secundaria (D03): decide si un resultado del clasificador se
 * publica. Por debajo del umbral se comunica que no se entendió; nunca se
 * muestra la adivinanza. Preferimos silencio a traducción incorrecta.
 */
public final class SignAcceptancePolicy {

    public enum Reason {
        ACCEPTED,
        BELOW_THRESHOLD,
        INVALID_OUTPUT
    }

    public static final class Decision {
        public final boolean accepted;
        public final int classIndex;
        public final float confidence;
        public final Reason reason;

        Decision(boolean accepted, int classIndex, float confidence, Reason reason) {
            this.accepted = accepted;
            this.classIndex = classIndex;
            this.confidence = confidence;
            this.reason = reason;
        }
    }

    private final float threshold;
    private final boolean outputsProbabilities;
    private final int expectedClasses;

    /**
     * @param threshold            umbral de confianza (sobre probabilidades).
     * @param outputsProbabilities true si el modelo ya emite probabilidades;
     *                             false si emite logits (se aplica softmax una
     *                             sola vez, nunca dos).
     * @param expectedClasses      cantidad de clases del catálogo.
     */
    public SignAcceptancePolicy(float threshold, boolean outputsProbabilities, int expectedClasses) {
        if (threshold <= 0f || threshold >= 1f) {
            throw new IllegalArgumentException("umbral fuera de (0,1): " + threshold);
        }
        this.threshold = threshold;
        this.outputsProbabilities = outputsProbabilities;
        this.expectedClasses = expectedClasses;
    }

    public Decision evaluate(float[] scores) {
        if (scores == null || scores.length != expectedClasses) {
            return new Decision(false, -1, 0f, Reason.INVALID_OUTPUT);
        }
        for (float s : scores) {
            if (Float.isNaN(s) || Float.isInfinite(s)) {
                return new Decision(false, -1, 0f, Reason.INVALID_OUTPUT);
            }
        }

        float[] probs = outputsProbabilities ? scores : softmax(scores);

        int best = 0;
        for (int i = 1; i < probs.length; i++) {
            if (probs[i] > probs[best]) {
                best = i;
            }
        }
        float confidence = probs[best];
        if (confidence < 0f || confidence > 1.0001f) {
            // el manifiesto declaró probabilidades pero no lo son
            return new Decision(false, -1, 0f, Reason.INVALID_OUTPUT);
        }
        if (confidence < threshold) {
            // vocabulario cerrado: fuera de las 64 se resuelve como
            // no-reconocida, nunca se fuerza a la clase más cercana
            return new Decision(false, best, confidence, Reason.BELOW_THRESHOLD);
        }
        return new Decision(true, best, confidence, Reason.ACCEPTED);
    }

    private static float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float l : logits) {
            max = Math.max(max, l);
        }
        double sum = 0;
        double[] exps = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exps[i] = Math.exp(logits[i] - max);
            sum += exps[i];
        }
        float[] out = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            out[i] = (float) (exps[i] / sum);
        }
        return out;
    }
}
