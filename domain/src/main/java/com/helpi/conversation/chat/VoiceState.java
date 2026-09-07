package com.helpi.conversation.chat;

/** Estado de la salida de voz asociada a un turno de la persona sorda. */
public enum VoiceState {
    /** Sin salida de voz (turnos del oyente o del sistema). */
    NONE,
    /** Aceptado, esperando arbitraje de audio. */
    PENDING,
    /** Pronunciado por TTS. */
    SPOKEN,
    /** Venció en cola o falló el TTS: queda visible como no pronunciado. */
    NOT_SPOKEN,
    /** Cancelado por la persona antes de reproducirse. */
    CANCELLED
}
