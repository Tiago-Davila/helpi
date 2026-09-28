package com.helpi.conversation.vision;

/**
 * Guía de encuadre: estima si el espacio de señado entra en la imagen, usando
 * el ancho de hombros (Pose 11 y 12) medido en píxeles como escala.
 *
 * Con el contrato de keypoints v3 el modelo ya no depende de la distancia: la
 * entrada se normaliza por el ancho de hombros. Lo que sí importa es que las
 * manos no salgan del cuadro (se perderían como ceros) y que no queden tan
 * chicas que el extractor pierda los dedos.
 *
 * El espacio de señado se midió en LSA64 normalizado v3 (percentiles 1 y 99
 * de las manos, en anchos de hombro): x entre -1,55 y +0,97 respecto del
 * centro de los hombros e y entre -0,95 y +1,36. Tomando la mano más alejada
 * de cada lado (una persona zurda espeja x), el cuadro necesita
 * REQUIRED_WIDTH_SHOULDERS anchos de hombro de ancho y
 * REQUIRED_HEIGHT_SHOULDERS de alto. De ahí sale el ancho de hombros máximo
 * en píxeles; el mínimo es una fracción de ese máximo.
 *
 * No calcula metros. Un promedio móvil evita que la guía cambie por el ruido
 * de un único cuadro. No bloquea la captura: solo orienta.
 */
public final class SigningDistanceGuide {

    public enum State {
        UNKNOWN,
        MOVE_CLOSER,
        OPTIMAL,
        MOVE_FARTHER
    }

    /** Ancho del espacio de señado, en anchos de hombro (2 × 1,55). */
    public static final float REQUIRED_WIDTH_SHOULDERS = 3.1f;
    /** Alto del espacio de señado, en anchos de hombro (0,95 + 1,36). */
    public static final float REQUIRED_HEIGHT_SHOULDERS = 2.31f;
    /** Por debajo de esta fracción del máximo la persona está demasiado lejos. */
    public static final float MIN_FRACTION_OF_MAX = 0.55f;

    private static final int LEFT_SHOULDER = 11;
    private static final int RIGHT_SHOULDER = 12;
    private static final float SMOOTHING = 0.25f;

    /** Ancho de hombros suavizado, en fracción del ancho de la imagen. */
    private float smoothedSpan = Float.NaN;

    /**
     * @param pose        33 landmarks de Pose normalizados (x, y en [0, 1]).
     * @param imageWidth  ancho en píxeles del cuadro analizado (ya rotado).
     * @param imageHeight alto en píxeles del cuadro analizado.
     */
    public State evaluate(float[] pose, int imageWidth, int imageHeight) {
        if (pose == null || pose.length < 33 * 3 || imageWidth <= 0 || imageHeight <= 0
                || isZero(pose, LEFT_SHOULDER) || isZero(pose, RIGHT_SHOULDER)) {
            return State.UNKNOWN;
        }

        int left = LEFT_SHOULDER * 3;
        int right = RIGHT_SHOULDER * 3;
        float dx = (pose[left] - pose[right]) * imageWidth;
        float dy = (pose[left + 1] - pose[right + 1]) * imageHeight;
        float span = (float) Math.sqrt(dx * dx + dy * dy) / imageWidth;
        if (!Float.isFinite(span) || span <= 0f) {
            return State.UNKNOWN;
        }

        smoothedSpan = Float.isNaN(smoothedSpan)
                ? span
                : smoothedSpan + SMOOTHING * (span - smoothedSpan);

        float max = maxShoulderSpan(imageWidth, imageHeight);
        if (smoothedSpan < max * MIN_FRACTION_OF_MAX) {
            return State.MOVE_CLOSER;
        }
        if (smoothedSpan > max) {
            return State.MOVE_FARTHER;
        }
        return State.OPTIMAL;
    }

    /**
     * Ancho de hombros máximo, en fracción del ancho de la imagen, para que el
     * espacio de señado entre completo. En 16:9 horizontal limita el alto y da
     * ~0,24; en 3:4 vertical limita el ancho y da ~0,32.
     */
    public static float maxShoulderSpan(int imageWidth, int imageHeight) {
        float byWidth = 1f / REQUIRED_WIDTH_SHOULDERS;
        float byHeight = ((float) imageHeight / imageWidth) / REQUIRED_HEIGHT_SHOULDERS;
        return Math.min(byWidth, byHeight);
    }

    /** Ancho de hombros suavizado, en fracción del ancho de la imagen. */
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
