package com.helpi.conversation.vision;

/**
 * Estima si la escala de la persona se parece a LSA64 usando la separación
 * normalizada entre Pose 11 y 12 como aproximación. No calcula metros: la
 * distancia física también depende de la cámara y del ancho de hombros.
 *
 * El rango 0.18..0.225 corresponde aproximadamente a los percentiles 5 y 95
 * observados en los 128 000 cuadros de entrenamiento. Un promedio móvil evita
 * que la guía visual cambie por el ruido de un único cuadro.
 */
public final class SigningDistanceGuide {

    public enum State {
        UNKNOWN,
        MOVE_CLOSER,
        OPTIMAL,
        MOVE_FARTHER
    }

    public static final float TRAINING_MIN_SHOULDER_SPAN = 0.18f;
    public static final float TRAINING_MAX_SHOULDER_SPAN = 0.225f;

    private static final int LEFT_SHOULDER = 11;
    private static final int RIGHT_SHOULDER = 12;
    private static final float SMOOTHING = 0.25f;

    private float smoothedSpan = Float.NaN;

    public State evaluate(float[] pose) {
        if (pose == null || pose.length < 33 * 3
                || isZero(pose, LEFT_SHOULDER) || isZero(pose, RIGHT_SHOULDER)) {
            return State.UNKNOWN;
        }

        int left = LEFT_SHOULDER * 3;
        int right = RIGHT_SHOULDER * 3;
        float dx = pose[left] - pose[right];
        float dy = pose[left + 1] - pose[right + 1];
        float span = (float) Math.sqrt(dx * dx + dy * dy);
        if (!Float.isFinite(span) || span <= 0f) {
            return State.UNKNOWN;
        }

        smoothedSpan = Float.isNaN(smoothedSpan)
                ? span
                : smoothedSpan + SMOOTHING * (span - smoothedSpan);

        if (smoothedSpan < TRAINING_MIN_SHOULDER_SPAN) {
            return State.MOVE_CLOSER;
        }
        if (smoothedSpan > TRAINING_MAX_SHOULDER_SPAN) {
            return State.MOVE_FARTHER;
        }
        return State.OPTIMAL;
    }

    public float smoothedShoulderSpan() {
        return smoothedSpan;
    }

    public void reset() {
        smoothedSpan = Float.NaN;
    }

    private static boolean isZero(float[] pose, int landmark) {
        int offset = landmark * 3;
        return pose[offset] == 0f && pose[offset + 1] == 0f && pose[offset + 2] == 0f;
    }
}
