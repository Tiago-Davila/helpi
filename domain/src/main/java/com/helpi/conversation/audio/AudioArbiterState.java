package com.helpi.conversation.audio;

/** Estado actual del ciclo de arbitraje entre STT y TTS. */
public enum AudioArbiterState { IDLE, WAITING_GATE, SPEAKING, GUARD }
