package com.helpi.conversation.vision;

import java.util.Objects;

/** Observación de un cuadro con timestamp monotónico real (no se asume 1/30 s). */
public final class FrameObservation {

    public final long timestampMs;
    public final boolean shouldersVisible;
    public final HandObservation left;
    public final HandObservation right;

    public FrameObservation(
            long timestampMs,
            boolean shouldersVisible,
            HandObservation left,
            HandObservation right) {
        this.timestampMs = timestampMs;
        this.shouldersVisible = shouldersVisible;
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
    }
}
