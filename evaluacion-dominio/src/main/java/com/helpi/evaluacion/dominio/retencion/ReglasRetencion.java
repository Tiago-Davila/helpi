package com.helpi.evaluacion.dominio.retencion;

/** Reglas de vencimiento independientes de Android y de Room. */
public final class ReglasRetencion {
    public static final long DIA_EN_MILLIS = 24L * 60L * 60L * 1000L;
    public static final int DIAS_SESION_EXPORTADA = 90;
    public static final int DIAS_SESION_SIN_EXPORTAR = 180;
    public static final int DIAS_ENVIO = 30;
    public static final int MAXIMO_SALTO_RELOJ_DIAS = 400;

    private ReglasRetencion() {}

    public enum DecisionReloj {
        PERMITE_DEPURAR,
        HORA_RETROCEDIO,
        SALTO_EXCESIVO
    }

    public enum EstadoEnvio {
        PENDIENTE,
        RECHAZADO,
        BLOQUEADO
    }

    public static DecisionReloj evaluarReloj(Long ultimaHoraVistaEn, long ahora) {
        if (ultimaHoraVistaEn == null) {
            return DecisionReloj.PERMITE_DEPURAR;
        }
        if (ahora < ultimaHoraVistaEn) {
            return DecisionReloj.HORA_RETROCEDIO;
        }
        if (superoDias(ultimaHoraVistaEn, ahora, MAXIMO_SALTO_RELOJ_DIAS)) {
            return DecisionReloj.SALTO_EXCESIVO;
        }
        return DecisionReloj.PERMITE_DEPURAR;
    }

    public static boolean sesionVencida(
            Long ultimaExportacionEn,
            Long ultimoIntentoEn,
            long creadaEn,
            long ahora
    ) {
        if (ultimaExportacionEn != null) {
            return hanPasadoDias(ultimaExportacionEn, ahora, DIAS_SESION_EXPORTADA);
        }
        long base = ultimoIntentoEn != null ? ultimoIntentoEn : creadaEn;
        return hanPasadoDias(base, ahora, DIAS_SESION_SIN_EXPORTAR);
    }

    public static boolean envioVencido(EstadoEnvio estado, long creadoEn, long ahora) {
        if (estado == null) {
            throw new IllegalArgumentException("estado es obligatorio");
        }
        return hanPasadoDias(creadoEn, ahora, DIAS_ENVIO);
    }

    private static boolean hanPasadoDias(long desde, long ahora, int dias) {
        if (ahora < desde) {
            return false;
        }
        long vencimiento;
        try {
            vencimiento = Math.addExact(desde, Math.multiplyExact((long) dias, DIA_EN_MILLIS));
        } catch (ArithmeticException desbordamiento) {
            return false;
        }
        return ahora >= vencimiento;
    }

    private static boolean superoDias(long desde, long ahora, int dias) {
        if (ahora <= desde) {
            return false;
        }
        long limite;
        try {
            limite = Math.addExact(desde, Math.multiplyExact((long) dias, DIA_EN_MILLIS));
        } catch (ArithmeticException desbordamiento) {
            return false;
        }
        return ahora > limite;
    }
}
