package com.helpi.conversation.chat;

/** Quién produjo un turno del chat. */
public enum Speaker {
    /** Persona oyente: entra por micrófono (STT). */
    HEARING,
    /** Persona sorda: entra por cámara (señas). */
    DEAF,
    /** Mensajes del sistema: nunca se atribuyen a una persona. */
    SYSTEM
}
