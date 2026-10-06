package com.helpi.evaluacion.dominio.retencion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReglasRetencionTest {
    private static final long AHORA = 1_800_000_000_000L;

    @Test
    public void sesionSinIntentosUsaCreacionComoBase() {
        long creada = AHORA - dias(180);

        assertTrue(ReglasRetencion.sesionVencida(null, null, creada, AHORA));
        assertFalse(ReglasRetencion.sesionVencida(null, null, creada, AHORA - 1));
    }

    @Test
    public void sesionRetomadaUsaElUltimoIntentoRegistrado() {
        long creada = AHORA - dias(220);
        long ultimoIntento = AHORA - dias(179);

        assertFalse(ReglasRetencion.sesionVencida(null, ultimoIntento, creada, AHORA));
        assertTrue(ReglasRetencion.sesionVencida(null, AHORA - dias(180), creada, AHORA));
    }

    @Test
    public void sesionExportadaUsaNoventaDiasDesdeExportacion() {
        assertTrue(ReglasRetencion.sesionVencida(AHORA - dias(90), null, 0L, AHORA));
        assertFalse(ReglasRetencion.sesionVencida(AHORA - dias(89), null, 0L, AHORA));
    }

    @Test
    public void enviosPendientesYTerminalesVencenAlCumplirTreintaDias() {
        long creado = AHORA - dias(30);

        assertTrue(ReglasRetencion.envioVencido(ReglasRetencion.EstadoEnvio.PENDIENTE, creado, AHORA));
        assertTrue(ReglasRetencion.envioVencido(ReglasRetencion.EstadoEnvio.RECHAZADO, creado, AHORA));
        assertTrue(ReglasRetencion.envioVencido(ReglasRetencion.EstadoEnvio.BLOQUEADO, creado, AHORA));
        assertFalse(
                ReglasRetencion.envioVencido(
                        ReglasRetencion.EstadoEnvio.PENDIENTE,
                        AHORA - dias(29),
                        AHORA
                )
        );
    }

    @Test
    public void protegeContraRelojAtrasadoYSaltosMayoresA400Dias() {
        assertEquals(
                ReglasRetencion.DecisionReloj.HORA_RETROCEDIO,
                ReglasRetencion.evaluarReloj(AHORA, AHORA - 1)
        );
        assertEquals(
                ReglasRetencion.DecisionReloj.PERMITE_DEPURAR,
                ReglasRetencion.evaluarReloj(AHORA, AHORA + dias(400))
        );
        assertEquals(
                ReglasRetencion.DecisionReloj.SALTO_EXCESIVO,
                ReglasRetencion.evaluarReloj(AHORA, AHORA + dias(400) + 1)
        );
    }

    private static long dias(int cantidad) {
        return cantidad * ReglasRetencion.DIA_EN_MILLIS;
    }
}
