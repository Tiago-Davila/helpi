package com.helpi.conversation.vision;

import java.util.Objects;

/** Resultado del detector de señalamiento para un segmento. */
public final class Pointing {

    /**
     * Qué se señaló; AMBIGUOUS es un señalamiento claro sin dirección definida.
     * CABEZA, GARGANTA y ABDOMEN son zonas del cuerpo tocadas con el índice.
     */
    public enum Type {
        NONE, AMBIGUOUS, YO, USTED, EL_ELLA, CABEZA, GARGANTA, ABDOMEN
    }

    /** Segmento sin señalamiento. */
    public static final Pointing NONE = new Pointing(Type.NONE, 0L);

    private final Type type;
    private final long heldMs;

    /** Crea el resultado con el tiempo que se sostuvo el índice. */
    public Pointing(Type type, long heldMs) {
        this.type = Objects.requireNonNull(type);
        this.heldMs = heldMs;
    }

    /** Qué se señaló. */
    public Type type() {
        return type;
    }

    /** Tiempo que el índice se sostuvo quieto, ms; 0 si no hubo señalamiento. */
    public long heldMs() {
        return heldMs;
    }

    /** Indica si el resultado es un pronombre que puede entrar a la oración. */
    public boolean isPronoun() {
        return type == Type.YO || type == Type.USTED || type == Type.EL_ELLA;
    }

    /** Indica si el resultado es una zona del cuerpo tocada con el índice. */
    public boolean isBodyZone() {
        return type == Type.CABEZA || type == Type.GARGANTA || type == Type.ABDOMEN;
    }
}
