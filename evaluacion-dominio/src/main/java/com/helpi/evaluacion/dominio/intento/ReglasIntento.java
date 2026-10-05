package com.helpi.evaluacion.dominio.intento;

import java.util.List;
import java.util.Objects;

/** Reglas semánticas compartidas por el registro local y las pruebas del protocolo. */
public final class ReglasIntento {
    private ReglasIntento() { }

    public static boolean superoUmbral(float confianza, float umbral) {
        validarProbabilidad(confianza, "confianza");
        validarProbabilidad(umbral, "umbral");
        return confianza >= umbral;
    }

    public static boolean correcto(ResultadoIntento resultado, Integer predicho, int esperado) {
        return resultado == ResultadoIntento.SOBRE_UMBRAL
                && predicho != null
                && predicho == esperado;
    }

    public static void validar(Intento intento) {
        Objects.requireNonNull(intento, "intento");
        if (intento.posicion() < 1 || intento.posicion() > 192) {
            throw new IllegalArgumentException("posicion debe estar entre 1 y 192");
        }
        if (intento.bloque() < 1 || intento.bloque() > 4) {
            throw new IllegalArgumentException("bloque debe estar entre 1 y 4");
        }
        if (intento.numeroIntento() < 1) {
            throw new IllegalArgumentException("numeroIntento debe ser al menos 1");
        }
        if (intento.senaEsperadaIndice() < 0 || intento.senaEsperadaIndice() > 63) {
            throw new IllegalArgumentException("senaEsperadaIndice debe estar entre 0 y 63");
        }
        validarProbabilidad(intento.umbral(), "umbral");

        if (intento.resultado() == ResultadoIntento.SIN_RESULTADO) {
            validarSinResultado(intento);
            return;
        }
        validarClasificado(intento);
    }

    static void validarProbabilidad(float valor, String nombre) {
        if (!Float.isFinite(valor) || valor < 0f || valor > 1f) {
            throw new IllegalArgumentException(nombre + " debe estar entre 0 y 1");
        }
    }

    private static void validarSinResultado(Intento intento) {
        if (intento.causaSinResultado() == null || intento.predichoIndice() != null
                || intento.predichoGlosa() != null || intento.confianza() != null
                || !intento.top3().isEmpty() || intento.superoUmbral()
                || intento.correcto() || intento.descartado()) {
            throw new IllegalArgumentException("SIN_RESULTADO no puede incluir predicción ni top-3");
        }
    }

    private static void validarClasificado(Intento intento) {
        if (intento.causaSinResultado() != null || intento.predichoIndice() == null
                || intento.predichoIndice() < 0 || intento.predichoIndice() > 63
                || intento.predichoGlosa() == null || intento.predichoGlosa().isBlank()
                || intento.confianza() == null) {
            throw new IllegalArgumentException("Un resultado clasificado requiere predicción completa");
        }
        validarProbabilidad(intento.confianza(), "confianza");

        List<PrediccionTop> top3 = intento.top3();
        if (top3.size() != 3) {
            throw new IllegalArgumentException("Un resultado clasificado requiere tres predicciones");
        }
        for (int i = 0; i < top3.size(); i++) {
            if (top3.get(i).rango() != i + 1) {
                throw new IllegalArgumentException("Los rangos top-3 deben estar ordenados 1, 2, 3");
            }
            if (i > 0 && top3.get(i - 1).confianza() < top3.get(i).confianza()) {
                throw new IllegalArgumentException("Las confianzas top-3 no pueden crecer con el rango");
            }
        }
        PrediccionTop primera = top3.get(0);
        if (primera.indice() != intento.predichoIndice()
                || !primera.glosa().equals(intento.predichoGlosa())
                || Float.compare(primera.confianza(), intento.confianza()) != 0) {
            throw new IllegalArgumentException("La predicción debe coincidir con el rango 1");
        }

        boolean superoUmbral = superoUmbral(intento.confianza(), intento.umbral());
        if (intento.superoUmbral() != superoUmbral
                || (intento.resultado() == ResultadoIntento.SOBRE_UMBRAL) != superoUmbral) {
            throw new IllegalArgumentException("resultado y superoUmbral no coinciden con confianza");
        }
        if (intento.correcto() != correcto(
                intento.resultado(), intento.predichoIndice(), intento.senaEsperadaIndice())) {
            throw new IllegalArgumentException("correcto no coincide con la verdad de referencia");
        }
        if (intento.descartado() && intento.resultado() != ResultadoIntento.SOBRE_UMBRAL) {
            throw new IllegalArgumentException("solo se puede descartar un resultado sobre el umbral");
        }
    }
}
