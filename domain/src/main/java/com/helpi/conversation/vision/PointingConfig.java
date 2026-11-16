package com.helpi.conversation.vision;

/**
 * Umbrales del detector de señalamiento. Valores iniciales de plan, a
 * calibrar con segmentos grabados (SC-004). Las distancias van en anchos de
 * hombro o en múltiplos del largo de la palma, nunca en píxeles.
 */
public final class PointingConfig {

    /** Tiempo mínimo que el índice se sostiene quieto, ms. */
    public final long minHoldMs;
    /** Desplazamiento máximo de la punta mientras se sostiene, anchos de hombro. */
    public final float maxDrift;
    /** Distancia punta del índice–muñeca, en palmas, a partir de la cual el índice está extendido. */
    public final float indexExtended;
    /** Distancia punta–muñeca, en palmas, por debajo de la cual un dedo está plegado. */
    public final float fingerFolded;
    /** Coseno mínimo entre las falanges del índice: el dedo debe estar recto. */
    public final float minStraightness;
    /** Cuántas veces debe superar un eje a los otros dos para fijar la dirección. */
    public final float dominanceRatio;
    /** Medio ancho de la zona del torso, anchos de hombro desde el centro. */
    public final float torsoHalfWidth;
    /** Borde superior del torso respecto de la línea de hombros (positivo hacia abajo). */
    public final float torsoTop;
    /** Borde inferior del torso respecto de la línea de hombros. */
    public final float torsoBottom;
    /** Altura donde termina el pecho y empieza el abdomen, anchos de hombro bajo los hombros. */
    public final float abdomenTop;
    /** Radio de contacto alrededor de la nariz para la zona cabeza, anchos de hombro. */
    public final float headRadius;
    /** Radio de contacto alrededor de la garganta, anchos de hombro. */
    public final float throatRadius;
    /** Posición de la garganta entre la boca (0) y el centro de los hombros (1). */
    public final float throatPosition;

    /** Crea la configuración de umbrales del detector. */
    public PointingConfig(long minHoldMs, float maxDrift, float indexExtended, float fingerFolded,
            float minStraightness, float dominanceRatio, float torsoHalfWidth, float torsoTop,
            float torsoBottom, float abdomenTop, float headRadius, float throatRadius,
            float throatPosition) {
        this.abdomenTop = abdomenTop;
        this.headRadius = headRadius;
        this.throatRadius = throatRadius;
        this.throatPosition = throatPosition;
        this.minHoldMs = minHoldMs;
        this.maxDrift = maxDrift;
        this.indexExtended = indexExtended;
        this.fingerFolded = fingerFolded;
        this.minStraightness = minStraightness;
        this.dominanceRatio = dominanceRatio;
        this.torsoHalfWidth = torsoHalfWidth;
        this.torsoTop = torsoTop;
        this.torsoBottom = torsoBottom;
    }

    /** Valores iniciales del plan, antes de calibrar con segmentos reales. */
    public static PointingConfig defaults() {
        return new PointingConfig(250, 0.15f, 1.5f, 1.3f, 0.8f, 1.5f, 0.75f, -0.2f, 1.6f,
                0.9f, 0.4f, 0.15f, 0.5f);
    }
}
