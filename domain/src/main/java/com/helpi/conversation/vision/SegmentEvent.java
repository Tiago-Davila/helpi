package com.helpi.conversation.vision;

/** Resultado de procesar un cuadro en el segmentador. */
public final class SegmentEvent {

    public enum Type {
        /** Sin novedad. */
        NONE,
        /** La compuerta quedó armada tras reposo estable. */
        ARMED,
        /** Inicio de seña detectado. */
        STARTED,
        /** Segmento cerrado y válido: clasificar. */
        ENDED,
        /** Segmento abortado: no se clasifica. */
        ABORTED
    }

    public enum AbortReason {
        NONE,
        TOO_SHORT,
        TOO_LONG,
        TRACKING_LOST
    }

    public final Type type;
    public final AbortReason abortReason;
    /** Inicio del segmento (incluye pre-roll), válido en STARTED/ENDED. */
    public final long segmentStartMs;
    /** Fin del segmento (incluye margen final), válido en ENDED. */
    public final long segmentEndMs;

    private SegmentEvent(Type type, AbortReason abortReason, long startMs, long endMs) {
        this.type = type;
        this.abortReason = abortReason;
        this.segmentStartMs = startMs;
        this.segmentEndMs = endMs;
    }

    static final SegmentEvent NONE = new SegmentEvent(Type.NONE, AbortReason.NONE, -1, -1);
    static final SegmentEvent ARMED = new SegmentEvent(Type.ARMED, AbortReason.NONE, -1, -1);

    static SegmentEvent started(long startMs) {
        return new SegmentEvent(Type.STARTED, AbortReason.NONE, startMs, -1);
    }

    static SegmentEvent ended(long startMs, long endMs) {
        return new SegmentEvent(Type.ENDED, AbortReason.NONE, startMs, endMs);
    }

    static SegmentEvent aborted(AbortReason reason) {
        return new SegmentEvent(Type.ABORTED, reason, -1, -1);
    }
}
