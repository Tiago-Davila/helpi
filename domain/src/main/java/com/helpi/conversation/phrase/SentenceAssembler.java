package com.helpi.conversation.phrase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Junta las señas de una oración en curso y decide cuándo termina: la oración
 * se cierra tras una pausa sin señas nuevas, más larga si el último intento falló. Los instantes son los del reloj de
 * la cámara, el mismo que marca el inicio y el fin de cada segmento.
 *
 * No es thread-safe: debe usarse solo desde el executor serial del
 * coordinador de sesión.
 */
public final class SentenceAssembler {

    private final long pauseMs;
    private final long pauseAfterFailureMs;
    private final List<RecognizedSign> signs = new ArrayList<>();
    private long lastEventMs = -1L;
    private long currentPauseMs;

    /** Crea el armador con la pausa tras una seña y la más larga tras una falla. */
    public SentenceAssembler(long pauseMs, long pauseAfterFailureMs) {
        if (pauseMs <= 0L) {
            throw new IllegalArgumentException("la pausa debe ser positiva: " + pauseMs);
        }
        if (pauseAfterFailureMs <= 0L) {
            throw new IllegalArgumentException(
                "la pausa tras una falla debe ser positiva: " + pauseAfterFailureMs);
        }
        this.pauseMs = pauseMs;
        this.pauseAfterFailureMs = pauseAfterFailureMs;
        this.currentPauseMs = pauseMs;
    }

    /** Suma una seña aceptada y vuelve a la pausa normal desde el fin de su segmento. */
    public void add(RecognizedSign sign, long segmentEndMs) {
        signs.add(sign);
        lastEventMs = Math.max(lastEventMs, segmentEndMs);
        currentPauseMs = pauseMs;
    }

    /** Registra un intento fallido: no suma señas, solo da más tiempo para repetir. */
    public void noteFailure(long segmentEndMs) {
        lastEventMs = Math.max(lastEventMs, segmentEndMs);
        currentPauseMs = pauseAfterFailureMs;
    }

    /** Indica si ya pasó la pausa vigente desde el último evento y hay algo que cerrar. */
    public boolean pauseElapsed(long nowMs) {
        return !signs.isEmpty() && nowMs - lastEventMs >= currentPauseMs;
    }

    /** Indica si todavía no hay ninguna seña en la oración. */
    public boolean isEmpty() {
        return signs.isEmpty();
    }

    /** Señas de la oración en curso, en orden. */
    public List<RecognizedSign> signs() {
        return Collections.unmodifiableList(new ArrayList<>(signs));
    }

    /** Entrega las señas de la oración y deja el armador listo para la siguiente. */
    public List<RecognizedSign> close() {
        List<RecognizedSign> closed = signs();
        reset();
        return closed;
    }

    /** Descarta la oración en curso sin entregarla. */
    public void reset() {
        signs.clear();
        lastEventMs = -1L;
        currentPauseMs = pauseMs;
    }
}
