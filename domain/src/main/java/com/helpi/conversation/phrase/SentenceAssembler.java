package com.helpi.conversation.phrase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Junta las señas de una oración en curso y decide cuándo termina: la oración
 * se cierra tras una pausa sin señas nuevas. Los instantes son los del reloj de
 * la cámara, el mismo que marca el inicio y el fin de cada segmento.
 *
 * No es thread-safe: debe usarse solo desde el executor serial del
 * coordinador de sesión.
 */
public final class SentenceAssembler {

    private final long pauseMs;
    private final List<RecognizedSign> signs = new ArrayList<>();
    private long lastSignEndMs = -1L;

    /** Crea el armador con la pausa que cierra una oración. */
    public SentenceAssembler(long pauseMs) {
        if (pauseMs <= 0L) {
            throw new IllegalArgumentException("la pausa debe ser positiva: " + pauseMs);
        }
        this.pauseMs = pauseMs;
    }

    /** Suma una seña aceptada a la oración, con el instante en que terminó su segmento. */
    public void add(RecognizedSign sign, long segmentEndMs) {
        signs.add(sign);
        lastSignEndMs = Math.max(lastSignEndMs, segmentEndMs);
    }

    /**
     * Suma un intento que no se entendió. Va con confianza cero para que el
     * armado lo muestre como hueco: la adivinanza nunca llega a la frase, aunque
     * el rechazo haya sido por margen y no por umbral.
     */
    public void addGap(int classIndex, long segmentEndMs) {
        add(new RecognizedSign(classIndex, "", 0f), segmentEndMs);
    }

    /** Indica si ya pasó la pausa desde la última seña y hay algo que cerrar. */
    public boolean pauseElapsed(long nowMs) {
        return !signs.isEmpty() && nowMs - lastSignEndMs >= pauseMs;
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
        lastSignEndMs = -1L;
    }
}
