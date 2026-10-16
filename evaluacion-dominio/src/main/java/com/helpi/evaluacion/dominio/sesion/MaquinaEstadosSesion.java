package com.helpi.evaluacion.dominio.sesion;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Máquina local de transiciones que impide mantener dos sesiones EN_CURSO. */
public final class MaquinaEstadosSesion {
    private final Map<String, EstadoSesion> estados = new HashMap<>();

    public synchronized void crear(String sesionId) {
        Objects.requireNonNull(sesionId, "sesionId");
        if (sesionId.isBlank()) {
            throw new IllegalArgumentException("sesionId no puede estar vacío");
        }
        if (estados.putIfAbsent(sesionId, EstadoSesion.CREADA) != null) {
            throw new IllegalStateException("La sesión ya existe: " + sesionId);
        }
    }

    public synchronized EstadoSesion obtener(String sesionId) {
        EstadoSesion estado = estados.get(sesionId);
        if (estado == null) {
            throw new IllegalArgumentException("Sesión desconocida: " + sesionId);
        }
        return estado;
    }

    public synchronized void transicionar(String sesionId, EstadoSesion destino) {
        EstadoSesion actual = obtener(sesionId);
        if (!actual.puedeTransicionarA(destino)) {
            throw new IllegalStateException("Transición no permitida: " + actual + " → " + destino);
        }
        if (destino == EstadoSesion.EN_CURSO && hayOtraSesionEnCurso(sesionId)) {
            throw new IllegalStateException("Ya hay otra sesión EN_CURSO");
        }
        estados.put(sesionId, destino);
    }

    private boolean hayOtraSesionEnCurso(String sesionId) {
        return estados.entrySet().stream().anyMatch(
                entrada -> !entrada.getKey().equals(sesionId)
                        && entrada.getValue() == EstadoSesion.EN_CURSO
        );
    }
}
