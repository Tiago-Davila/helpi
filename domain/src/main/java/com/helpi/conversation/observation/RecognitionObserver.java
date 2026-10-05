package com.helpi.conversation.observation;

import com.helpi.conversation.lsa.SignDecision;

/**
 * Side-effect-free observation port for recognition events.
 *
 * <p>Implementations must only copy the supplied values and return. They must not perform I/O or
 * block the recognition thread.
 */
public interface RecognitionObserver {

    RecognitionObserver NO_OP = new RecognitionObserver() {
        @Override
        public void onSegmentStarted(long startMs) { }

        @Override
        public void onNoResult(NoResultCause cause) { }

        @Override
        public void onDecision(SignDecision decision, float thresholdUsed, long segmentEndMs) { }
    };

    /** Called when the segmenter detects the beginning of a sign segment. */
    void onSegmentStarted(long startMs);

    /** Called when a segment or model path produces no usable decision. */
    void onNoResult(NoResultCause cause);

    /** Called with the original decision and the threshold captured for this segment. */
    void onDecision(SignDecision decision, float thresholdUsed, long segmentEndMs);
}
