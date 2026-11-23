package com.helpi.conversation.lsa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compuerta secundaria (D03): decide si un resultado del clasificador se
 * publica. Por debajo del umbral se comunica que no se entendió y ninguna
 * opción se publica como traducción. La interfaz puede mostrar el top 3 solo
 * como predicciones explícitamente no confirmadas.
 */
public final class SignAcceptancePolicy {

    /** Cuánto puede quedar la probabilidad cruda por debajo del umbral antes de que el contexto deje de alcanzar. */
    public static final float RAW_FLOOR_MARGIN = 0.15f;

    private final float threshold;
    private final boolean outputsProbabilities;
    private final int expectedClasses;

    /** Configura el umbral, el formato de salida del modelo y el tamaño del catálogo. */
    public SignAcceptancePolicy(float threshold, boolean outputsProbabilities, int expectedClasses) {
        if (threshold <= 0f || threshold >= 1f) {
            throw new IllegalArgumentException("umbral fuera de (0,1): " + threshold);
        }
        if (expectedClasses <= 0) {
            throw new IllegalArgumentException("cantidad de clases inválida: " + expectedClasses);
        }
        this.threshold = threshold;
        this.outputsProbabilities = outputsProbabilities;
        this.expectedClasses = expectedClasses;
    }

    /** Evalúa las puntuaciones sin contexto previo. */
    public SignDecision evaluate(float[] scores) {
        return evaluate(scores, null);
    }

    /**
     * Evalúa ponderando por el contexto previo: exige p' (con contexto) sobre el umbral y
     * p cruda sobre umbral menos RAW_FLOOR_MARGIN; el contexto desempata, no inventa.
     */
    public SignDecision evaluate(float[] scores, float[] contextWeights) {
        if (scores == null || scores.length != expectedClasses) {
            return invalidDecision();
        }
        for (float s : scores) {
            if (Float.isNaN(s) || Float.isInfinite(s)) {
                return invalidDecision();
            }
        }
        if (contextWeights != null) {
            if (contextWeights.length != scores.length) {
                return invalidDecision();
            }
            for (float w : contextWeights) {
                if (Float.isNaN(w) || w <= 0f) {
                    return invalidDecision();
                }
            }
        }

        float[] raw = outputsProbabilities ? scores : softmax(scores);
        float[] probs = contextWeights == null ? raw : applyContext(raw, contextWeights);
        List<Prediction> predictions = topPredictions(probs, 3);
        Prediction best = predictions.get(0);
        float confidence = best.confidence;
        if (confidence < 0f || confidence > 1.0001f) {
            // el manifiesto declaró probabilidades pero no lo son
            return invalidDecision();
        }
        float rawConfidence = raw[best.classIndex];
        if (confidence < threshold) {
            // vocabulario cerrado: fuera de las 64 se resuelve como
            // no-reconocida, nunca se fuerza a la clase más cercana
            return new SignDecision(false, best.classIndex, confidence, rawConfidence,
                    AcceptanceReason.BELOW_THRESHOLD, predictions);
        }
        if (rawConfidence < threshold - RAW_FLOOR_MARGIN) {
            return new SignDecision(false, best.classIndex, confidence, rawConfidence,
                    AcceptanceReason.BELOW_RAW_FLOOR, predictions);
        }
        return new SignDecision(true, best.classIndex, confidence, rawConfidence,
                AcceptanceReason.ACCEPTED, predictions);
    }

    // Multiplica cada probabilidad por su peso y renormaliza para que sumen 1.
    private static float[] applyContext(float[] probs, float[] weights) {
        double sum = 0;
        double[] scaled = new double[probs.length];
        for (int i = 0; i < probs.length; i++) {
            scaled[i] = (double) probs[i] * weights[i];
            sum += scaled[i];
        }
        float[] out = new float[probs.length];
        for (int i = 0; i < probs.length; i++) {
            out[i] = sum > 0 ? (float) (scaled[i] / sum) : 0f;
        }
        return out;
    }

    private static SignDecision invalidDecision() {
        return new SignDecision(false, -1, 0f, 0f, AcceptanceReason.INVALID_OUTPUT, Collections.emptyList());
    }

    private static List<Prediction> topPredictions(float[] probabilities, int limit) {
        List<Prediction> top = new ArrayList<>(limit);
        for (int classIndex = 0; classIndex < probabilities.length; classIndex++) {
            Prediction candidate = new Prediction(classIndex, probabilities[classIndex]);
            int position = 0;
            while (position < top.size()
                    && top.get(position).confidence >= candidate.confidence) {
                position++;
            }
            if (position < limit) {
                top.add(position, candidate);
                if (top.size() > limit) {
                    top.remove(top.size() - 1);
                }
            }
        }
        return Collections.unmodifiableList(top);
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
