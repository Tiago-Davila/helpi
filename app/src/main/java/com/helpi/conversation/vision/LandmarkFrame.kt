package com.helpi.conversation.vision

/**
 * Landmarks de un cuadro, ya en el formato de arrays que consume
 * KeypointContract: floats [x0,y0,z0,x1,...] o null si no se detectó.
 */
data class LandmarkFrame(
    val timestampMs: Long,
    val leftHand: FloatArray?,
    val rightHand: FloatArray?,
    /** pose 0..32 de MediaPipe (33 landmarks) o null. */
    val pose: FloatArray?,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = timestampMs.hashCode()
}
