package com.helpi.conversation.vision;

/**
 * Compuerta primaria de la traducción visual (D02): detecta reposo, inicio,
 * actividad y fin de una seña. Con la compuerta cerrada no se clasifica nada.
 *
 * Reglas principales:
 * - Al abrir, se exige reposo estable antes de armar (no se clasifica el
 *   movimiento de acomodarse).
 * - Manos abajo O quietas mantienen cerrada la compuerta; histéresis en
 *   velocidad y en posición para no oscilar.
 * - La desaparición de landmarks nunca cuenta como velocidad cero: deja de
 *   acumularse evidencia y, si supera la tolerancia durante una seña, se
 *   aborta el segmento.
 * - Tras cerrar un segmento la compuerta debe rearmarse por reposo.
 *
 * No es thread-safe: usar desde el executor visual serial.
 */
public final class SignSegmenter {

    private enum State { WAITING_REST, ARMED, CAPTURING, CONFIRMING_END }

    private final SegmenterConfig cfg;

    private State state = State.WAITING_REST;

    // acumuladores (en ms, sobre timestamps reales)
    private long restAccumMs;
    private long motionAccumMs;
    private int motionObservations;
    private long quietAccumMs;
    private long trackingLossAccumMs;

    private long lastTimestampMs = Long.MIN_VALUE;
    private long firstMotionTs = -1;
    private long segmentStartTs = -1;
    private long quietStartTs = -1;
    private int validFrames;
    private boolean handsWereDown = true;

    public SignSegmenter(SegmenterConfig config) {
        this.cfg = config;
    }

    public boolean isArmed() {
        return state == State.ARMED;
    }

    public boolean isCapturing() {
        return state == State.CAPTURING || state == State.CONFIRMING_END;
    }

    /** Procesa una observación; los timestamps deben ser monotónicos. */
    public SegmentEvent process(FrameObservation obs) {
        long dt = lastTimestampMs == Long.MIN_VALUE ? 0 : obs.timestampMs - lastTimestampMs;
        if (dt < 0) {
            throw new IllegalArgumentException("timestamp no monotónico");
        }
        lastTimestampMs = obs.timestampMs;

        boolean tracked = obs.shouldersVisible && (obs.left.present || obs.right.present);

        if (!tracked) {
            // ausencia nunca es velocidad cero
            restAccumMs = 0;
            motionAccumMs = 0;
            motionObservations = 0;
            if (isCapturing()) {
                trackingLossAccumMs += dt;
                if (trackingLossAccumMs > cfg.trackingLossToleranceMs) {
                    reset();
                    return SegmentEvent.aborted(SegmentEvent.AbortReason.TRACKING_LOST);
                }
            }
            return SegmentEvent.NONE;
        }
        trackingLossAccumMs = 0;

        boolean handsDown = handsDown(obs);
        boolean quiet = allPresentHandsQuiet(obs);
        boolean atRest = handsDown || quiet;
        boolean moving = anyHandSigning(obs);

        switch (state) {
            case WAITING_REST:
                if (atRest) {
                    restAccumMs += dt;
                    if (restAccumMs >= cfg.restToArmMs) {
                        state = State.ARMED;
                        restAccumMs = 0;
                        return SegmentEvent.ARMED;
                    }
                } else {
                    restAccumMs = 0;
                }
                return SegmentEvent.NONE;

            case ARMED:
                if (moving) {
                    if (firstMotionTs < 0) {
                        firstMotionTs = obs.timestampMs;
                    }
                    motionAccumMs += dt;
                    motionObservations++;
                    if (motionAccumMs >= cfg.startPersistenceMs
                            && motionObservations >= cfg.startMinObservations) {
                        state = State.CAPTURING;
                        segmentStartTs = Math.max(0, firstMotionTs - cfg.preRollMs);
                        validFrames = 1;
                        quietAccumMs = 0;
                        quietStartTs = -1;
                        return SegmentEvent.started(segmentStartTs);
                    }
                } else {
                    motionAccumMs = 0;
                    motionObservations = 0;
                    firstMotionTs = -1;
                }
                return SegmentEvent.NONE;

            case CAPTURING:
            case CONFIRMING_END:
                validFrames++;

                if (obs.timestampMs - segmentStartTs > cfg.maxSegmentMs) {
                    reset();
                    return SegmentEvent.aborted(SegmentEvent.AbortReason.TOO_LONG);
                }

                if (quiet) {
                    if (quietStartTs < 0) {
                        quietStartTs = obs.timestampMs;
                    }
                    quietAccumMs += dt;
                    state = State.CONFIRMING_END;
                    long threshold = handsDown ? cfg.endQuietLoweredMs : cfg.endQuietElevatedMs;
                    if (quietAccumMs >= threshold) {
                        long endTs = quietStartTs + cfg.endMarginMs;
                        long startTs = segmentStartTs;
                        int frames = validFrames;
                        reset();
                        if (endTs - startTs < cfg.minSegmentMs || frames < cfg.minValidFrames) {
                            return SegmentEvent.aborted(SegmentEvent.AbortReason.TOO_SHORT);
                        }
                        return SegmentEvent.ended(startTs, endTs);
                    }
                } else {
                    // pausa interna corta: histéresis, se vuelve a capturar
                    quietAccumMs = 0;
                    quietStartTs = -1;
                    state = State.CAPTURING;
                }
                return SegmentEvent.NONE;

            default:
                throw new IllegalStateException("estado desconocido");
        }
    }

    private boolean handsDown(FrameObservation obs) {
        float threshold = handsWereDown ? cfg.handsDownExit : cfg.handsDownEnter;
        boolean down = handDownOrAbsent(obs.left, threshold)
                && handDownOrAbsent(obs.right, threshold)
                && (obs.left.present || obs.right.present);
        handsWereDown = down;
        return down;
    }

    private static boolean handDownOrAbsent(HandObservation hand, float threshold) {
        // una mano ausente no aporta evidencia en contra, pero tampoco a favor:
        // el llamador exige al menos una presente
        return !hand.present || hand.belowShoulders > threshold;
    }

    private boolean allPresentHandsQuiet(FrameObservation obs) {
        boolean any = false;
        if (obs.left.present) {
            any = true;
            if (obs.left.speed >= cfg.vOff) return false;
        }
        if (obs.right.present) {
            any = true;
            if (obs.right.speed >= cfg.vOff) return false;
        }
        return any;
    }

    private boolean anyHandSigning(FrameObservation obs) {
        return isSigning(obs.left) || isSigning(obs.right);
    }

    private boolean isSigning(HandObservation hand) {
        return hand.present
                && hand.belowShoulders <= cfg.handsDownEnter
                && hand.speed > cfg.vOn;
    }

    private void reset() {
        state = State.WAITING_REST;
        restAccumMs = 0;
        motionAccumMs = 0;
        motionObservations = 0;
        quietAccumMs = 0;
        trackingLossAccumMs = 0;
        firstMotionTs = -1;
        segmentStartTs = -1;
        quietStartTs = -1;
        validFrames = 0;
    }
}
