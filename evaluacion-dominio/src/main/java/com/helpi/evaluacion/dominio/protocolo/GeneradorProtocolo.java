package com.helpi.evaluacion.dominio.protocolo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Genera la secuencia reproducible de señas para una sesión de evaluación. */
public final class GeneradorProtocolo {
    public static final String VERSION = "1.0.0";
    public static final int CANTIDAD_SENAS = 64;
    public static final int REPETICIONES = 3;
    public static final int INTENTOS = CANTIDAD_SENAS * REPETICIONES;
    public static final int CANTIDAD_BLOQUES = 4;
    public static final int INTENTOS_POR_BLOQUE = INTENTOS / CANTIDAD_BLOQUES;

    private GeneradorProtocolo() { }

    public static Protocolo generar(long semilla) {
        List<Integer> secuencia = new ArrayList<>(INTENTOS);
        for (int repeticion = 0; repeticion < REPETICIONES; repeticion++) {
            for (int sena = 0; sena < CANTIDAD_SENAS; sena++) {
                secuencia.add(sena);
            }
        }

        Collections.shuffle(secuencia, new Random(semilla));
        repararAdyacencias(secuencia);

        List<List<Integer>> bloques = new ArrayList<>(CANTIDAD_BLOQUES);
        for (int indice = 0; indice < CANTIDAD_BLOQUES; indice++) {
            int inicio = indice * INTENTOS_POR_BLOQUE;
            int fin = inicio + INTENTOS_POR_BLOQUE;
            bloques.add(List.copyOf(secuencia.subList(inicio, fin)));
        }
        return new Protocolo(VERSION, semilla, secuencia, bloques);
    }

    private static void repararAdyacencias(List<Integer> secuencia) {
        for (int indice = 1; indice < secuencia.size(); indice++) {
            if (!secuencia.get(indice).equals(secuencia.get(indice - 1))) {
                continue;
            }

            int repetida = secuencia.get(indice);
            int indiceCandidato = buscarPrimerIntercambioValido(secuencia, indice, repetida);
            if (indiceCandidato < 0) {
                throw new IllegalStateException("no se pudo reparar la adyacencia en " + indice);
            }
            Collections.swap(secuencia, indice, indiceCandidato);
        }
    }

    private static int buscarPrimerIntercambioValido(List<Integer> secuencia, int indice, int repetida) {
        for (int candidato = indice + 1; candidato < secuencia.size(); candidato++) {
            int valorCandidato = secuencia.get(candidato);
            if (valorCandidato == repetida) {
                continue;
            }
            if (valorCandidato == secuencia.get(indice - 1)) {
                continue;
            }
            if (candidato > indice + 1 && valorCandidato == secuencia.get(indice + 1)) {
                continue;
            }
            if (candidato > indice + 1 && repetida == secuencia.get(candidato - 1)) {
                continue;
            }
            if (candidato + 1 < secuencia.size() && repetida == secuencia.get(candidato + 1)) {
                continue;
            }
            return candidato;
        }
        return -1;
    }

    /** Secuencia y bloques inmutables ya cortados según la versión del protocolo. */
    public record Protocolo(String version, long semilla, List<Integer> secuencia, List<List<Integer>> bloques) {
        public Protocolo {
            secuencia = List.copyOf(secuencia);
            List<List<Integer>> bloquesInmutables = new ArrayList<>(bloques.size());
            for (List<Integer> bloque : bloques) {
                bloquesInmutables.add(List.copyOf(bloque));
            }
            bloques = List.copyOf(bloquesInmutables);
        }
    }
}
