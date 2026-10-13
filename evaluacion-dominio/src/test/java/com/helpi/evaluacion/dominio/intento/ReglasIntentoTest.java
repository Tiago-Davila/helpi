package com.helpi.evaluacion.dominio.intento;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.helpi.evaluacion.dominio.sesion.CalculoPosicionRetoma;
import java.util.List;
import org.junit.Test;

public class ReglasIntentoTest {
    @Test
    public void resultadoClasificadoValidaTop3YFormulaDeCorrecto() {
        Intento intento = clasificado(
                ResultadoIntento.SOBRE_UMBRAL,
                7,
                7,
                0.9f,
                0.9f,
                true,
                true,
                false,
                top3(7, 0.9f, 8, 0.06f, 9, 0.04f)
        );

        ReglasIntento.validar(intento);
        assertTrue(ReglasIntento.superoUmbral(0.7f, 0.7f));
        assertTrue(ReglasIntento.correcto(ResultadoIntento.SOBRE_UMBRAL, 7, 7));
        assertFalse(ReglasIntento.correcto(ResultadoIntento.BAJO_UMBRAL, 7, 7));
    }

    @Test
    public void sinResultadoExigeCausaYNoAdmitePredicciones() {
        Intento intento = new Intento(
                1, 1, 1, false, 7, ResultadoIntento.SIN_RESULTADO,
                CausaSinResultado.SIN_HOMBROS, null, null, null,
                0.7f, false, false, false, List.of()
        );

        ReglasIntento.validar(intento);
        Intento conTop3 = new Intento(
                1, 1, 1, false, 7, ResultadoIntento.SIN_RESULTADO,
                CausaSinResultado.SIN_HOMBROS, null, null, null,
                0.7f, false, false, false, top3(7, 0.9f, 8, 0.06f, 9, 0.04f)
        );
        assertThrows(IllegalArgumentException.class, () -> ReglasIntento.validar(conTop3));
    }

    @Test
    public void bajoUmbralNoPuedeSerCorrectoNiDescartado() {
        Intento intento = clasificado(
                ResultadoIntento.BAJO_UMBRAL,
                7,
                7,
                0.5f,
                0.7f,
                false,
                true,
                true,
                top3(7, 0.5f, 8, 0.3f, 9, 0.2f)
        );

        assertThrows(IllegalArgumentException.class, () -> ReglasIntento.validar(intento));
    }

    @Test
    public void encuentraPrimeraPosicionSinRespuestaDelBloque() {
        assertEquals(
                51,
                CalculoPosicionRetoma.primerIntentoSinRespuesta(2, java.util.Set.of(49, 50, 52))
                        .orElseThrow()
        );
        assertFalse(CalculoPosicionRetoma.primerIntentoSinRespuesta(1, posiciones(1, 48)).isPresent());
    }

    private static Intento clasificado(
            ResultadoIntento resultado,
            int predicho,
            int esperado,
            float confianza,
            float umbral,
            boolean superoUmbral,
            boolean correcto,
            boolean descartado,
            List<PrediccionTop> top3
    ) {
        return new Intento(
                1, 1, 1, false, esperado, resultado, null,
                predicho, "CASA", confianza, umbral, superoUmbral,
                correcto, descartado, top3
        );
    }

    private static List<PrediccionTop> top3(
            int indice1, float confianza1,
            int indice2, float confianza2,
            int indice3, float confianza3
    ) {
        return List.of(
                new PrediccionTop(1, indice1, "CASA", confianza1),
                new PrediccionTop(2, indice2, "AGUA", confianza2),
                new PrediccionTop(3, indice3, "COMER", confianza3)
        );
    }

    private static java.util.Set<Integer> posiciones(int inicio, int fin) {
        java.util.Set<Integer> resultado = new java.util.HashSet<>();
        for (int posicion = inicio; posicion <= fin; posicion++) {
            resultado.add(posicion);
        }
        return resultado;
    }
}
