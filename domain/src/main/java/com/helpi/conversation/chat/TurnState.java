package com.helpi.conversation.chat;

/** Estado textual de un turno. */
public enum TurnState {
    /** Reserva: se detectó inicio (voz o seña) pero aún no hay texto final. */
    PENDING,
    /** Texto provisional de STT; se reemplaza, no genera turnos nuevos. */
    PARTIAL,
    /** Texto definitivo. */
    FINAL,
    /** El canal se interrumpió: el parcial no se convierte en definitivo. */
    INCOMPLETE,
    /** Segmento de seña rechazado o intento no procesado. */
    REJECTED
}
