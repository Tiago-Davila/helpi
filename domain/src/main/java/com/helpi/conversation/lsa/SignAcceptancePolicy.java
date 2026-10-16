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

    /** Evalúa las puntuaciones y devuelve el resultado de aceptación. */
    public SignDecision evaluate(float[] scores) {
        if (scores == null || scores.length != expectedClasses) {
            return invalidDecision();
        }
        for (float s : scores) {
            if (Float.isNaN(s) || Float.isInfinite(s)) {
                return invalidDecision();
            }
        }

        float[] probs = outputsProbabilities ? scores : softmax(scores);
        List<Prediction> predictions = topPredictions(probs, 3);
        Prediction best = predictions.get(0);
        float confidence = best.confidence;
        if (confidence < 0f || confidence > 1.0001f) {
            // el manifiesto declaró probabilidades pero no lo son
            return invalidDecision();
        }
        if (confidence < threshold) {
            // vocabulario cerrado: fuera de las 64 se resuelve como
            // no-reconocida, nunca se fuerza a la clase más cercana
            return new SignDecision(
                    false, best.classIndex, confidence, AcceptanceReason.BELOW_THRESHOLD, predictions);
        }
        return new SignDecision(true, best.classIndex, confidence, AcceptanceReason.ACCEPTED, predictions);
    }

    private static SignDecision invalidDecision() {
        return new SignDecision(false, -1, 0f, AcceptanceReason.INVALID_OUTPUT, Collections.emptyList());
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
