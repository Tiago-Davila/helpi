package com.helpi.evaluacion.dominio.sesion;

/** Estados persistibles de una sesión de evaluación y sus transiciones permitidas. */
public enum EstadoSesion {
    CREADA,
    EN_CURSO,
    PAUSADA,
    INTERRUMPIDA,
    COMPLETA,
    TERMINADA_ANTES;

    public boolean puedeTransicionarA(EstadoSesion destino) {
        if (destino == null) {
            return false;
        }
        return switch (this) {
            case CREADA -> destino == EN_CURSO || destino == TERMINADA_ANTES;
            case EN_CURSO -> destino == PAUSADA
                    || destino == INTERRUMPIDA
                    || destino == COMPLETA
                    || destino == TERMINADA_ANTES;
            case PAUSADA, INTERRUMPIDA -> destino == EN_CURSO || destino == TERMINADA_ANTES;
            case COMPLETA, TERMINADA_ANTES -> false;
        };
    }
}
