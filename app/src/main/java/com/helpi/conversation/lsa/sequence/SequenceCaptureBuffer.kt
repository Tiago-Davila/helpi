package com.helpi.conversation.lsa.sequence

import com.helpi.conversation.keypoints.SequenceKeypointContract

/** Resultado de agregar un cuadro a una captura explícita de frase. */
enum class SequenceAppendResult {
    ACCEPTED,
    NOT_CAPTURING,
    TOO_LONG,
}

data class SequenceCapture(
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val frames: List<FloatArray>,
    val validFrameCount: Int,
) {
    val durationMs: Long get() = endTimestampMs - startTimestampMs
    val validFrameRatio: Float get() = validFrameCount.toFloat() / frames.size

    fun buildInputTensor(): FloatArray =
        SequenceKeypointContract.buildInputTensor(frames)
}

/**
 * Buffer separado del segmentador de Eva.
 *
 * La frase comienza y termina por una acción explícita de la interfaz. El
 * límite temporal evita acumular una captura sin fin y hace visible el motivo
 * por el que una frase no se procesa. Los cuadros sin manos se conservan para
 * que la normalización y la cobertura sean auditables antes de inferir.
 */
class SequenceCaptureBuffer(
    private val maxDurationMs: Long = DEFAULT_MAX_DURATION_MS,
) {
    private val frames = ArrayList<FloatArray>()
    private var startTimestampMs: Long? = null
    private var lastTimestampMs: Long? = null
    private var validFrameCount = 0

    val isCapturing: Boolean get() = startTimestampMs != null
    val frameCount: Int get() = frames.size
    val elapsedMs: Long
        get() {
            val start = startTimestampMs ?: return 0L
            val end = lastTimestampMs ?: start
            return end - start
        }

    fun start(timestampMs: Long) {
        require(timestampMs >= 0) { "timestamp negativo" }
        frames.clear()
        startTimestampMs = timestampMs
        lastTimestampMs = timestampMs
        validFrameCount = 0
    }

    fun append(
        timestampMs: Long,
        leftHand: FloatArray?,
        rightHand: FloatArray?,
    ): SequenceAppendResult {
        val start = startTimestampMs ?: return SequenceAppendResult.NOT_CAPTURING
        require(timestampMs >= (lastTimestampMs ?: timestampMs)) {
            "timestamp no monotónico"
        }
        if (timestampMs - start > maxDurationMs) return SequenceAppendResult.TOO_LONG

        val frame = SequenceKeypointContract.flattenHands(leftHand, rightHand)
        frames += frame
        if (leftHand != null || rightHand != null) validFrameCount++
        lastTimestampMs = timestampMs
        return SequenceAppendResult.ACCEPTED
    }

    fun stop(timestampMs: Long = lastTimestampMs ?: startTimestampMs ?: 0L): SequenceCapture? {
        val start = startTimestampMs ?: return null
        require(timestampMs >= (lastTimestampMs ?: start)) {
            "timestamp final no monotónico"
        }
        val capture = if (frames.isEmpty()) {
            null
        } else {
            SequenceCapture(
                startTimestampMs = start,
                endTimestampMs = timestampMs,
                frames = frames.map { it.copyOf() },
                validFrameCount = validFrameCount,
            )
        }
        clear()
        return capture
    }

    fun cancel() {
        clear()
    }

    private fun clear() {
        frames.clear()
        startTimestampMs = null
        lastTimestampMs = null
        validFrameCount = 0
    }

    companion object {
        /** 12 s de video bruto; luego se muestrea a 75 cuadros. */
        const val DEFAULT_MAX_DURATION_MS: Long = 12_000L
    }
}
