package com.helpi.conversation.vision;

/**
 * Umbrales de la compuerta de dos niveles (D02). Valores iniciales de plan,
 * a calibrar con datos de validación; nunca sobre el conjunto reservado.
 */
public final class SegmenterConfig {

    /** Velocidad de activación, anchos de hombro/s. */
    public final float vOn;
    /** Velocidad de reposo, anchos de hombro/s (histéresis). */
    public final float vOff;
    /** Persistencia de movimiento para iniciar, ms. */
    public final long startPersistenceMs;
    /** Observaciones válidas mínimas para iniciar. */
    public final int startMinObservations;
    /** Muñecas por debajo de este umbral = "manos abajo" (anchos de hombro). */
    public final float handsDownEnter;
    /** Salida de "manos abajo" (histéresis). */
    public final float handsDownExit;
    /** Reposo para armar/rearmar la compuerta, ms. */
    public final long restToArmMs;
    /** Fin por quietud con manos elevadas, ms. */
    public final long endQuietElevatedMs;
    /** Fin con manos abajo, ms. */
    public final long endQuietLoweredMs;
    /** Pre-roll conservado antes del inicio detectado, ms. */
    public final long preRollMs;
    /** Margen final conservado tras el último movimiento, ms. */
    public final long endMarginMs;
    /** Duración mínima del segmento, ms. */
    public final long minSegmentMs;
    /** Cuadros válidos mínimos del segmento. */
    public final int minValidFrames;
    /** Duración máxima del segmento, ms. */
    public final long maxSegmentMs;
    /** Pérdida de seguimiento tolerada, ms. */
    public final long trackingLossToleranceMs;

    public SegmenterConfig(
            float vOn, float vOff, long startPersistenceMs, int startMinObservations,
            float handsDownEnter, float handsDownExit, long restToArmMs,
            long endQuietElevatedMs, long endQuietLoweredMs, long preRollMs,
            long endMarginMs, long minSegmentMs, int minValidFrames,
            long maxSegmentMs, long trackingLossToleranceMs) {
        this.vOn = vOn;
        this.vOff = vOff;
        this.startPersistenceMs = startPersistenceMs;
        this.startMinObservations = startMinObservations;
        this.handsDownEnter = handsDownEnter;
        this.handsDownExit = handsDownExit;
        this.restToArmMs = restToArmMs;
        this.endQuietElevatedMs = endQuietElevatedMs;
        this.endQuietLoweredMs = endQuietLoweredMs;
        this.preRollMs = preRollMs;
        this.endMarginMs = endMarginMs;
        this.minSegmentMs = minSegmentMs;
        this.minValidFrames = minValidFrames;
        this.maxSegmentMs = maxSegmentMs;
        this.trackingLossToleranceMs = trackingLossToleranceMs;
    }

    /** Valores iniciales del plan (documento de decisiones D02). */
    public static SegmenterConfig defaults() {
        return new SegmenterConfig(
                0.7f, 0.2f, 150, 3,
                1.0f, 0.75f, 500,
                600, 350, 250,
                150, 300, 6,
                4000, 200);
    }
}
