package com.helpi.conversation.vision;

/**
 * Observación de una mano en un cuadro, ya normalizada por el productor:
 * velocidades en anchos de hombro por segundo y posición vertical relativa
 * al punto medio de los hombros (positivo hacia abajo), también en anchos
 * de hombro.
 */
public final class HandObservation {

    /** Mano no detectada en este cuadro. */
    public static final HandObservation ABSENT = new HandObservation(false, 0f, 0f, false);

    public final boolean present;
    /** Velocidad robusta (incluye dedos), en anchos de hombro/s. */
    public final float speed;
    /** Distancia vertical bajo el centro de hombros, en anchos de hombro. */
    public final float belowShoulders;
    public final boolean nearEdge;

    public HandObservation(boolean present, float speed, float belowShoulders, boolean nearEdge) {
        this.present = present;
        this.speed = speed;
        this.belowShoulders = belowShoulders;
        this.nearEdge = nearEdge;
    }
}
