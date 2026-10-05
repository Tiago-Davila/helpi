package com.helpi.evaluacion.dominio.intento;

import java.util.List;
import java.util.Objects;

/** Datos de dominio necesarios para comprobar las invariantes de un intento resuelto. */
public record Intento(
        int posicion,
        int bloque,
        int numeroIntento,
        boolean reemplazado,
        int senaEsperadaIndice,
        ResultadoIntento resultado,
        CausaSinResultado causaSinResultado,
        Integer predichoIndice,
        String predichoGlosa,
        Float confianza,
        float umbral,
        boolean superoUmbral,
        boolean correcto,
        boolean descartado,
        List<PrediccionTop> top3
) {
    public Intento {
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(top3, "top3");
        top3 = List.copyOf(top3);
    }
}
