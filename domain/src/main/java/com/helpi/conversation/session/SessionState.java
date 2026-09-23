package com.helpi.conversation.session;

/** Estado global de la sesión de conversación. */
public enum SessionState {
    /** Bienvenida, alcance y orientación física. */
    INICIO,
    /** Verificación de permisos y artefactos. */
    PREPARANDO,
    /** Preparación terminada; preview y encuadre visibles. */
    LISTA,
    /** Ambos canales operando. */
    ACTIVA,
    /** Falta un canal o una salida; banner persistente. */
    ACTIVA_LIMITADA,
    /** Segundo plano, bloqueo o pérdida de condición interactiva. */
    PAUSADA,
    /** Ninguna capacidad útil o fallo no recuperable. */
    BLOQUEADA,
    /** Cierre en curso: captura ya desactivada. */
    CERRANDO,
    /** Recursos liberados e historial descartado. */
    CERRADA
}
