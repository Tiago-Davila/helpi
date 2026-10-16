package com.helpi.conversation.audio;

/** Efectos de audio que el árbitro delega en la capa Android. */
public interface AudioArbiterListener {
    /** Solicita cerrar STT hasta recibir confirmación. */
    void requestCloseSttGate();

    /** Reabre STT después de invalidar el buffer. */
    void openSttGate();

    /** Inicia la síntesis de un mensaje. */
    void speak(long messageId, String text);

    /** Informa que un mensaje venció y no será pronunciado. */
    void expired(long messageId);
}
