package com.helpi.evaluacion.dominio.sesion;

import com.helpi.evaluacion.dominio.protocolo.GeneradorProtocolo;
import java.util.OptionalInt;
import java.util.Set;

/** Encuentra la primera posición del bloque para la que aún no hay un intento resuelto. */
public final class CalculoPosicionRetoma {
    private static final int INTENTOS_POR_BLOQUE = GeneradorProtocolo.INTENTOS_POR_BLOQUE;
    private static final int BLOQUES = GeneradorProtocolo.CANTIDAD_BLOQUES;

    private CalculoPosicionRetoma() { }

    public static OptionalInt primerIntentoSinRespuesta(int bloque, Set<Integer> posicionesRespondidas) {
        if (bloque < 1 || bloque > BLOQUES) {
            throw new IllegalArgumentException("bloque debe estar entre 1 y " + BLOQUES);
        }
        if (posicionesRespondidas == null) {
            throw new IllegalArgumentException("posicionesRespondidas no puede ser null");
        }

        int inicio = (bloque - 1) * INTENTOS_POR_BLOQUE + 1;
        int fin = bloque * INTENTOS_POR_BLOQUE;
        for (Integer posicion : posicionesRespondidas) {
            if (posicion == null || posicion < 1 || posicion > BLOQUES * INTENTOS_POR_BLOQUE) {
                throw new IllegalArgumentException("posición respondida fuera de 1.." + BLOQUES * INTENTOS_POR_BLOQUE);
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
