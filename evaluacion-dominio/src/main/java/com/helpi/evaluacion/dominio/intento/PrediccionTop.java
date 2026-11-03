package com.helpi.evaluacion.dominio.intento;

import java.util.Objects;

public record PrediccionTop(int rango, int indice, String glosa, float confianza) {
    public PrediccionTop {
        if (rango < 1 || rango > 3) {
            throw new IllegalArgumentException("rango debe estar entre 1 y 3");
        }
        if (!ReglasIntento.indiceValido(indice)) {
            throw new IllegalArgumentException("indice debe estar entre 0 y " + ReglasIntento.ULTIMO_INDICE);
        }
        Objects.requireNonNull(glosa, "glosa");
        if (glosa.isBlank()) {
            throw new IllegalArgumentException("glosa no puede estar vacía");
        }
        ReglasIntento.validarProbabilidad(confianza, "confianza");
    }
}
