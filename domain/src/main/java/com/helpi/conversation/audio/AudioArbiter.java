package com.helpi.conversation.audio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Exclusión mutua TTS–STT (D04). Con micrófono abierto y TTS hablando, el
 * reconocedor transcribiría la propia voz de la app: este árbitro cierra la
 * compuerta del STT ANTES de hablar, con confirmación explícita del executor
 * de audio, y la reabre después de una guarda acústica.
 *
 * El PCM durante TTS + guarda se descarta: la voz superpuesta del oyente se
 * pierde y debe repetirse (decisión de plan, comunicada en la interfaz).
 *
 * No es thread-safe: usar desde el executor serial del coordinador.
 */
public final class AudioArbiter {

    /** Demora inicial que permite cancelar una salida evidentemente incorrecta. */
    private final long cancelWindowMs;
    /** Guarda acústica tras el fin del TTS (cola de reverberación). */
    private final long acousticGuardMs;
    /** Vigencia de un mensaje en cola. */
    private final long queueTtlMs;
    /** Watchdog: un callback TTS perdido no puede silenciar el STT para siempre. */
    private final long ttsWatchdogMs;
    private final int maxQueue;
    private final AudioArbiterListener listener;

    private AudioArbiterState state = AudioArbiterState.IDLE;
    private final Deque<Pending> queue = new ArrayDeque<>();
    private long currentMessageId = -1;
    private long stateEnteredAtMs;
    private boolean hearingSpeaking;

    private static final class Pending {
        final long id;
        final String text;
        final long enqueuedAtMs;
        final long notBeforeMs;

        Pending(long id, String text, long enqueuedAtMs, long notBeforeMs) {
            this.id = id;
            this.text = text;
            this.enqueuedAtMs = enqueuedAtMs;
            this.notBeforeMs = notBeforeMs;
        }
    }

    /** Crea el árbitro con las demoras predeterminadas. */
    public AudioArbiter(AudioArbiterListener listener) {
        this(listener, 600, 400, 5000, 15000, 3);
    }

    /** Crea el árbitro con tiempos de cancelación, guarda, cola y watchdog configurables. */
    public AudioArbiter(AudioArbiterListener listener, long cancelWindowMs, long acousticGuardMs,
            long queueTtlMs, long ttsWatchdogMs, int maxQueue) {
        this.listener = Objects.requireNonNull(listener);
        this.cancelWindowMs = cancelWindowMs;
        this.acousticGuardMs = acousticGuardMs;
        this.queueTtlMs = queueTtlMs;
        this.ttsWatchdogMs = ttsWatchdogMs;
        this.maxQueue = maxQueue;
    }

    /** Devuelve el estado del ciclo actual de voz. */
    public AudioArbiterState state() {
        return state;
    }

    /** Encola un mensaje aceptado para pronunciarlo cuando el audio esté libre. */
    public boolean enqueue(long messageId, String text, long nowMs) {
        if (queue.size() >= maxQueue) {
            listener.expired(messageId);
            return false;
        }
        queue.addLast(new Pending(messageId, text, nowMs, nowMs + cancelWindowMs));
        tick(nowMs);
        return true;
    }

    /** Cancela un mensaje que todavía no empezó a reproducirse. */
    public boolean cancel(long messageId) {
        return queue.removeIf(p -> p.id == messageId);
    }

    /** Descarta el ciclo acústico y sus mensajes pendientes al cerrar la conversación. */
    public void reset() {
        queue.clear();
        currentMessageId = -1;
        stateEnteredAtMs = 0;
        hearingSpeaking = false;
        state = AudioArbiterState.IDLE;
    }

    /** Actualiza si Vosk detecta que el oyente está hablando. */
    public void hearingSpeechActive(boolean active, long nowMs) {
        this.hearingSpeaking = active;
        tick(nowMs);
    }

    /** Confirma que el executor cerró STT e invalidó su buffer. */
    public void sttGateClosed(long nowMs) {
        if (state != AudioArbiterState.WAITING_GATE) {
            return; // confirmación tardía de un ciclo anterior: se ignora
        }
        Pending next = queue.pollFirst();
        if (next == null) {
            reopen();
            return;
        }
        state = AudioArbiterState.SPEAKING;
        stateEnteredAtMs = nowMs;
        currentMessageId = next.id;
        listener.speak(next.id, next.text);
    }

    /** Registra que TTS terminó o canceló el mensaje indicado. */
    public void ttsFinished(long messageId, long nowMs) {
        if (state != AudioArbiterState.SPEAKING || messageId != currentMessageId) {
            return; // callback tardío
        }
        state = AudioArbiterState.GUARD;
        stateEnteredAtMs = nowMs;
        currentMessageId = -1;
    }

    /** Registra el fallo de TTS y aplica la guarda acústica. */
    public void ttsFailed(long messageId, long nowMs) {
        if (state == AudioArbiterState.SPEAKING && messageId == currentMessageId) {
            listener.expired(messageId);
            state = AudioArbiterState.GUARD;
            stateEnteredAtMs = nowMs;
            currentMessageId = -1;
        }
    }

    /** Avanza vencimientos, guardas y mensajes pendientes según la hora recibida. */
    public List<Long> tick(long nowMs) {
        List<Long> expired = new ArrayList<>();
        // vencimiento de mensajes en cola
        queue.removeIf(p -> {
            if (nowMs - p.enqueuedAtMs > queueTtlMs) {
                expired.add(p.id);
                listener.expired(p.id);
                return true;
            }
            return false;
        });

        switch (state) {
            case GUARD:
                if (nowMs - stateEnteredAtMs >= acousticGuardMs) {
                    reopen();
                    maybeStart(nowMs);
                }
                break;
            case SPEAKING:
                if (nowMs - stateEnteredAtMs >= ttsWatchdogMs) {
                    // callback perdido: no dejar el STT silenciado indefinidamente
                    listener.expired(currentMessageId);
                    currentMessageId = -1;
                    state = AudioArbiterState.GUARD;
                    stateEnteredAtMs = nowMs;
                }
                break;
            case IDLE:
                maybeStart(nowMs);
                break;
            case WAITING_GATE:
            default:
                break;
        }
        return expired;
    }

    private void maybeStart(long nowMs) {
        if (state != AudioArbiterState.IDLE || queue.isEmpty()) {
            return;
        }
        Pending head = queue.peekFirst();
        // demora de cancelación y espera del fin del turno del oyente
        if (nowMs < head.notBeforeMs || hearingSpeaking) {
            return;
        }
        state = AudioArbiterState.WAITING_GATE;
        stateEnteredAtMs = nowMs;
        listener.requestCloseSttGate();
    }

    private void reopen() {
        state = AudioArbiterState.IDLE;
        listener.openSttGate();
    }
}
