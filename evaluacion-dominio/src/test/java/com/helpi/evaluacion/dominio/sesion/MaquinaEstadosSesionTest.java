package com.helpi.evaluacion.dominio.sesion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class MaquinaEstadosSesionTest {
    @Test
    public void permiteTransicionesYEstadosFinales() {
        MaquinaEstadosSesion maquina = new MaquinaEstadosSesion();
        maquina.crear("sesion-1");
        maquina.transicionar("sesion-1", EstadoSesion.EN_CURSO);
        maquina.transicionar("sesion-1", EstadoSesion.INTERRUMPIDA);
        maquina.transicionar("sesion-1", EstadoSesion.EN_CURSO);
        maquina.transicionar("sesion-1", EstadoSesion.COMPLETA);

        assertEquals(EstadoSesion.COMPLETA, maquina.obtener("sesion-1"));
        assertThrows(IllegalStateException.class,
                () -> maquina.transicionar("sesion-1", EstadoSesion.EN_CURSO));
    }

    @Test
    public void impideUnaSegundaSesionEnCurso() {
        MaquinaEstadosSesion maquina = new MaquinaEstadosSesion();
        maquina.crear("sesion-1");
        maquina.crear("sesion-2");
        maquina.transicionar("sesion-1", EstadoSesion.EN_CURSO);

        assertThrows(IllegalStateException.class,
                () -> maquina.transicionar("sesion-2", EstadoSesion.EN_CURSO));
        assertEquals(EstadoSesion.CREADA, maquina.obtener("sesion-2"));
    }
}
