package com.helpi.evaluacion.dominio.sesion;

import java.util.OptionalInt;
import java.util.Set;

/** Encuentra la primera posición del bloque para la que aún no hay un intento resuelto. */
public final class CalculoPosicionRetoma {
    private static final int INTENTOS_POR_BLOQUE = 48;
    private static final int BLOQUES = 4;

    private CalculoPosicionRetoma() { }

    public static OptionalInt primerIntentoSinRespuesta(int bloque, Set<Integer> posicionesRespondidas) {
        if (bloque < 1 || bloque > BLOQUES) {
            throw new IllegalArgumentException("bloque debe estar entre 1 y 4");
        }
        if (posicionesRespondidas == null) {
            throw new IllegalArgumentException("posicionesRespondidas no puede ser null");
        }

        int inicio = (bloque - 1) * INTENTOS_POR_BLOQUE + 1;
        int fin = bloque * INTENTOS_POR_BLOQUE;
        for (Integer posicion : posicionesRespondidas) {
            if (posicion == null || posicion < 1 || posicion > BLOQUES * INTENTOS_POR_BLOQUE) {
                throw new IllegalArgumentException("posición respondida fuera de 1..192");
            }
        }
        for (int posicion = inicio; posicion <= fin; posicion++) {
            if (!posicionesRespondidas.contains(posicion)) {
                return OptionalInt.of(posicion);
            }
        }
        return OptionalInt.empty();
    }
}
