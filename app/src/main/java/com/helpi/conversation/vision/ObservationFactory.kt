package com.helpi.conversation.vision

import com.helpi.conversation.keypoints.KeypointContract
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Convierte cuadros de landmarks en observaciones normalizadas para el
 * segmentador: velocidades en anchos de hombro por segundo (con timestamps
 * reales) y posición vertical relativa al centro de hombros.
 *
 * Esta normalización es SOLO para la compuerta de movimiento: no se aplica
 * al tensor del clasificador.
 */
class ObservationFactory(
    /** Distancia normalizada al borde bajo la cual una mano está "al borde". */
    private val edgeMargin: Float = 0.05f,
    /** Ancho/alto del cuadro analizado, para corregir relación de aspecto. */
    private val aspectRatio: Float = 4f / 3f,
) {
    private var previous: LandmarkFrame? = null

    /** Landmarks usados para la velocidad robusta: muñeca y puntas de dedos. */
    private val speedLandmarks = intArrayOf(0, 4, 8, 12, 16, 20)

    fun reset() {
        previous = null
    }

    fun observe(frame: LandmarkFrame): FrameObservation {
        val pose = frame.pose
        val shoulders = pose != null && !isZero(pose, 11) && !isZero(pose, 12)

        var left = HandObservation.ABSENT
        var right = HandObservation.ABSENT

        if (shoulders) {
            val p = pose!!
            val midX = (p[11 * 3] + p[12 * 3]) / 2f
            val midY = (p[11 * 3 + 1] + p[12 * 3 + 1]) / 2f
            val shoulderWidth = run {
                val dx = (p[11 * 3] - p[12 * 3]) * aspectRatio
                val dy = p[11 * 3 + 1] - p[12 * 3 + 1]
                sqrt(dx * dx + dy * dy)
            }
            if (shoulderWidth > 1e-4f) {
                val prev = previous
                val dtSec = prev?.let { (frame.timestampMs - it.timestampMs) / 1000f } ?: 0f
                left = handObservation(frame.leftHand, prev?.leftHand, dtSec, midY, shoulderWidth)
                right = handObservation(frame.rightHand, prev?.rightHand, dtSec, midY, shoulderWidth)
            }
        }

        previous = frame
        return FrameObservation(frame.timestampMs, shoulders, left, right)
    }

    private fun handObservation(
        hand: FloatArray?,
        prevHand: FloatArray?,
        dtSec: Float,
        shoulderMidY: Float,
        shoulderWidth: Float,
    ): HandObservation {
        if (hand == null) return HandObservation.ABSENT

        val wristY = hand[1]
        val belowShoulders = (wristY - shoulderMidY) / shoulderWidth

        // la ausencia previa nunca cuenta como velocidad cero: sin cuadro
        // anterior la velocidad se informa como 0 pero el segmentador exige
        // persistencia de varios cuadros antes de decidir
        var speed = 0f
        if (prevHand != null && dtSec > 1e-4f) {
            var sum = 0f
            for (lm in speedLandmarks) {
                val dx = (hand[lm * 3] - prevHand[lm * 3]) * aspectRatio
                val dy = hand[lm * 3 + 1] - prevHand[lm * 3 + 1]
                sum += sqrt(dx * dx + dy * dy)
            }
            val meanDisp = sum / speedLandmarks.size
            speed = (meanDisp / shoulderWidth) / dtSec
        }

        var nearEdge = false
        for (lm in 0 until KeypointContract.HAND_LANDMARKS) {
            val x = hand[lm * 3]
            val y = hand[lm * 3 + 1]
            if (x < edgeMargin || x > 1f - edgeMargin || y < edgeMargin || y > 1f - edgeMargin) {
                nearEdge = true
                break
            }
        }

        return HandObservation(true, speed, belowShoulders, nearEdge)
    }

    private fun isZero(pose: FloatArray, landmark: Int): Boolean {
        val i = landmark * 3
        return abs(pose[i]) == 0f && abs(pose[i + 1]) == 0f && abs(pose[i + 2]) == 0f
    }
}
