package com.helpi.conversation.session

import com.helpi.conversation.keypoints.KeypointContract
import com.helpi.conversation.observation.NoResultCause
import com.helpi.conversation.vision.LandmarkFrame
import java.util.ArrayDeque

/**
 * Cuadro del segmento en curso: 168 coords crudas más el tamaño de la
 * imagen analizada, que el contrato v3 necesita para pasar a píxeles.
 * [head] guarda aparte nariz y boca (pose 0, 9 y 10) para las zonas del
 * cuerpo del detector de señalamiento; nunca entra al modelo.
 */
internal class CapturedFrame(
    val timestampMs: Long,
    val vector: FloatArray,
    val imageWidth: Int,
    val imageHeight: Int,
    val head: FloatArray?
) {
    companion object {
        private const val NOSE = 0
        private const val MOUTH_LEFT = 9
        private const val MOUTH_RIGHT = 10
        private const val AXES = 3
        private val HEAD_POSE_LANDMARKS = intArrayOf(NOSE, MOUTH_LEFT, MOUTH_RIGHT)
        private val HEAD_COORDS = HEAD_POSE_LANDMARKS.size * AXES
        private const val POSE_COORDS = 33 * AXES

        /** Aplana los landmarks según el contrato de keypoints. */
        fun of(frame: LandmarkFrame) = CapturedFrame(
            frame.timestampMs,
            KeypointContract.flattenFrame(frame.leftHand, frame.rightHand, frame.pose),
            frame.imageWidth,
            frame.imageHeight,
            headLandmarks(frame.pose)
        )

        /** Nariz y comisuras de la boca (pose 0, 9 y 10) como 9 floats; null sin pose. */
        fun headLandmarks(pose: FloatArray?): FloatArray? {
            if (pose == null || pose.size < POSE_COORDS) return null
            val head = FloatArray(HEAD_COORDS)
            HEAD_POSE_LANDMARKS.forEachIndexed { i, landmark ->
                System.arraycopy(pose, landmark * AXES, head, i * AXES, AXES)
            }
            return head
        }
    }
}

/** Cuadros del segmento en curso más un pre-roll con los previos al inicio detectado. */
internal class SegmentFrames {
    private val current = ArrayDeque<CapturedFrame>()
    private val preRoll = ArrayDeque<CapturedFrame>()

    val size: Int get() = current.size + preRoll.size

    /** Guarda un cuadro en el pre-roll y, si se está capturando, en el segmento. */
    fun add(frame: CapturedFrame, capturing: Boolean) {
        preRoll.addLast(frame)
        while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
        if (capturing) current.addLast(frame)
    }

    /** Empieza un segmento con los cuadros del pre-roll desde su inicio. */
    fun startFrom(startMs: Long) {
        current.clear()
        preRoll.filterTo(current) { it.timestampMs >= startMs }
    }

    /** Entrega los cuadros del segmento cerrado y vacía el buffer. */
    fun take(startMs: Long, endMs: Long): List<CapturedFrame> {
        val segment = current.filter { it.timestampMs in startMs..endMs }
        current.clear()
        return segment
    }

    fun discardCurrent() = current.clear()

    fun clear() {
        current.clear()
        preRoll.clear()
    }

    private companion object {
        const val PRE_ROLL_FRAMES = 8
    }
}

/** Motivos por los que un segmento no se puede clasificar. */
internal enum class SegmentProblem(val cause: NoResultCause, val note: String?) {
    EMPTY(NoResultCause.SEGMENTO_CORTO, null),

    /**
     * El contrato v3 usa un único tamaño de imagen por segmento. Con la
     * orientación fija no cambia; si cambiara (rotación a mitad de seña),
     * el intento se descarta en vez de mezclar geometrías.
     */
    ORIENTATION_CHANGED(
        NoResultCause.ORIENTACION_CAMBIO,
        "Ese intento no se procesó: cambió la orientación de la cámara."
    ),

    /** Sin hombros el contrato v3 no tiene escala: se avisa en lugar de culpar al modelo. */
    WITHOUT_SHOULDERS(
        NoResultCause.SIN_HOMBROS,
        "No vi tus hombros. Encuadrate con el torso visible y volvé a intentar."
    );

    companion object {
        /** Primer problema del segmento, o null si se puede clasificar. */
        fun of(frames: List<CapturedFrame>): SegmentProblem? = when {
            frames.isEmpty() -> EMPTY
            frames.any { !it.sameImageAs(frames.first()) } -> ORIENTATION_CHANGED
            frames.none { KeypointContract.hasShoulders(it.vector) } -> WITHOUT_SHOULDERS
            else -> null
        }

        private fun CapturedFrame.sameImageAs(other: CapturedFrame): Boolean =
            imageWidth == other.imageWidth && imageHeight == other.imageHeight
    }
}
