package com.helpi.conversation.session

import com.helpi.conversation.lsa.SignDecision
import com.helpi.conversation.observation.NoResultCause
import com.helpi.conversation.observation.RecognitionObserver

/** Reparte lo reconocido: siempre al observador y, según el modo, también a la conversación. */
internal class RecognitionOutlet(
    private val observer: RecognitionObserver,
    var mode: DeliveryMode
) {
    val reachesConversation: Boolean get() = mode == DeliveryMode.CONVERSATION

    fun segmentStarted(startMs: Long) = observer.onSegmentStarted(startMs)

    fun noResult(cause: NoResultCause) = observer.onNoResult(cause)

    /** Informa la decisión y, si corresponde, la entrega a la conversación. */
    fun decision(
        decision: SignDecision,
        thresholdUsed: Float,
        segmentEndMs: Long,
        conversationDelivery: () -> Unit
    ) {
        observer.onDecision(decision, thresholdUsed, segmentEndMs)
        if (reachesConversation) conversationDelivery()
    }
}
