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

    /** Efectos que el árbitro ordena; los ejecuta la capa Android. */
    public interface Listener {
        /** Cerrar la compuerta STT y confirmar con {@link #sttGateClosed}. */
        void requestCloseSttGate();

        /** Reabrir la compuerta STT (buffer ya invalidado). */
        void openSttGate();

        /** Iniciar la síntesis del mensaje. */
        void speak(long messageId, String text);

        /** El mensaje venció en cola: queda como "no pronunciado". */
        void expired(long messageId);
    }

    public enum State { IDLE, WAITING_GATE, SPEAKING, GUARD }

    /** Demora inicial que permite cancelar una salida evidentemente incorrecta. */
    private final long cancelWindowMs;
    /** Guarda acústica tras el fin del TTS (cola de reverberación). */
    private final long acousticGuardMs;
    /** Vigencia de un mensaje en cola. */
    private final long queueTtlMs;
    /** Watchdog: un callback TTS perdido no puede silenciar el STT para siempre. */
    private final long ttsWatchdogMs;
    private final int maxQueue;
    private final Listener listener;

    private State state = State.IDLE;
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

    public AudioArbiter(Listener listener) {
        this(listener, 600, 400, 5000, 15000, 3);
    }

    public AudioArbiter(Listener listener, long cancelWindowMs, long acousticGuardMs,
            long queueTtlMs, long ttsWatchdogMs, int maxQueue) {
        this.listener = Objects.requireNonNull(listener);
        this.cancelWindowMs = cancelWindowMs;
        this.acousticGuardMs = acousticGuardMs;
        this.queueTtlMs = queueTtlMs;
        this.ttsWatchdogMs = ttsWatchdogMs;
        this.maxQueue = maxQueue;
    }

    public State state() {
        return state;
    }

    /**
     * Encola un mensaje aceptado. Devuelve false si la cola está llena
     * (el mensaje queda como no pronunciado).
     */
    public boolean enqueue(long messageId, String text, long nowMs) {
        if (queue.size() >= maxQueue) {
            listener.expired(messageId);
            return false;
        }
        queue.addLast(new Pending(messageId, text, nowMs, nowMs + cancelWindowMs));
        tick(nowMs);
        return true;
    }

    /** Cancela un mensaje aún no reproducido ("no quise decir eso"). */
    public boolean cancel(long messageId) {
        return queue.removeIf(p -> p.id == messageId);
    }

    /**
     * Descarta por completo el ciclo acústico actual. Se usa al cerrar una
     * conversación para que ningún texto pendiente pueda pasar a la próxima.
     * No emite callbacks porque el coordinador elimina también sus turnos.
     */
    public void reset() {
        queue.clear();
        currentMessageId = -1;
        stateEnteredAtMs = 0;
        hearingSpeaking = false;
        state = State.IDLE;
    }

    /** El oyente está (o dejó de estar) hablando según Vosk. */
    public void hearingSpeechActive(boolean active, long nowMs) {
        this.hearingSpeaking = active;
        tick(nowMs);
    }

    /** Confirmación del executor de audio: compuerta cerrada y buffer invalidado. */
    public void sttGateClosed(long nowMs) {
        if (state != State.WAITING_GATE) {
            return; // confirmación tardía de un ciclo anterior: se ignora
        }
        Pending next = queue.pollFirst();
        if (next == null) {
            reopen();
            return;
        }
        state = State.SPEAKING;
        stateEnteredAtMs = nowMs;
        currentMessageId = next.id;
        listener.speak(next.id, next.text);
    }

    /** onDone / cancelación del TTS. */
    public void ttsFinished(long messageId, long nowMs) {
        if (state != State.SPEAKING || messageId != currentMessageId) {
            return; // callback tardío
        }
        state = State.GUARD;
        stateEnteredAtMs = nowMs;
        currentMessageId = -1;
    }

    /** Error del TTS: mismo tratamiento acústico que un fin normal. */
    public void ttsFailed(long messageId, long nowMs) {
        if (state == State.SPEAKING && messageId == currentMessageId) {
            listener.expired(messageId);
            state = State.GUARD;
            stateEnteredAtMs = nowMs;
            currentMessageId = -1;
        }
    }

    /**
     * Avance de tiempo: vencimientos de cola, fin de guarda, watchdog y
     * arranque de reproducciones pendientes. Llamar periódicamente.
     */
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
                    state = State.GUARD;
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
        if (state != State.IDLE || queue.isEmpty()) {
            return;
        }
        Pending head = queue.peekFirst();
        // demora de cancelación y espera del fin del turno del oyente
        if (nowMs < head.notBeforeMs || hearingSpeaking) {
            return;
        }
        state = State.WAITING_GATE;
        stateEnteredAtMs = nowMs;
        listener.requestCloseSttGate();
    }

    private void reopen() {
        state = State.IDLE;
        listener.openSttGate();
    }
}
