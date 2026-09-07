package com.helpi.conversation.session;

/** Estado del canal visual (señas). */
public enum VisualChannelState {
    NO_DISPONIBLE,
    BUSCANDO_ENCUADRE,
    ESPERANDO_REPOSO,
    ARMADO,
    CAPTURANDO_SENA,
    CONFIRMANDO_FIN,
    REARMANDO,
    CALIDAD_INSUFICIENTE,
    SUSPENDIDO_RENDIMIENTO
}
