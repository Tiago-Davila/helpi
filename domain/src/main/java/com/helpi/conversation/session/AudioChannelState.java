package com.helpi.conversation.session;

/** Estado del canal de audio (STT/TTS). */
public enum AudioChannelState {
    NO_DISPONIBLE,
    STT_ESCUCHANDO,
    STT_TRANSCRIBIENDO,
    TTS_PENDIENTE,
    CERRANDO_ENTRADA_STT,
    TTS_HABLANDO,
    GUARDA_ACUSTICA
}
