package com.helpi.conversation.keypoints

/**
 * Contrato de entrada del modelo secuencial LSA-T.
 *
 * Es independiente de [KeypointContract], que pertenece a Eva y usa 40x168.
 * El productor Python equivalente está en entrenamiento-modelo/lsa/
 * sequence_contract.py.
 *
 * Por cuadro:
 * - mano izquierda: 21 landmarks x (x, y, z), offsets 0..62;
 * - mano derecha: 21 landmarks x (x, y, z), offsets 63..125;
 * - orden intercalado por landmark: [x0, y0, z0, x1, y1, z1, ...].
 *
 * La normalización reproduce el productor actual: centro x/y por cuadro sobre
 * los 42 landmarks (incluyendo ceros), z intacta y escala global x/y. Las
 * secuencias cortas se rellenan con cuadros cero; las largas usan división
 * entera exacta para muestrear 75 cuadros.
 */
object SequenceKeypointContract {

    const val FRAMES: Int = 75
    const val HAND_LANDMARKS: Int = 21
    const val HAND_COORDS: Int = HAND_LANDMARKS * 3
    const val LANDMARKS: Int = HAND_LANDMARKS * 2
    const val COORDS: Int = LANDMARKS * 3

    private const val EPSILON: Float = 1e-6f

    /** Aplana ambas manos; una mano ausente se representa con 63 ceros. */
    fun flattenHands(leftHand: FloatArray?, rightHand: FloatArray?): FloatArray {
        val frame = FloatArray(COORDS)
        copyHand(leftHand, frame, 0, "mano izquierda")
        copyHand(rightHand, frame, HAND_COORDS, "mano derecha")
        return frame
    }

    private fun copyHand(source: FloatArray?, destination: FloatArray, offset: Int, name: String) {
        if (source == null) return
        require(source.size == HAND_COORDS) {
            "$name debe tener $HAND_COORDS floats, llegaron ${source.size}"
        }
        source.forEach { value -> require(value.isFinite()) { "$name contiene un valor no finito" } }
        System.arraycopy(source, 0, destination, offset, HAND_COORDS)
    }

    /** Índices enteros: floor(i * (T - 1) / (N - 1)). */
    fun sampleIndices(totalFrames: Int): IntArray {
        require(totalFrames > 0) { "T debe ser > 0" }
        if (totalFrames <= FRAMES) return IntArray(totalFrames) { it }
        return IntArray(FRAMES) { i -> (i.toLong() * (totalFrames - 1) / (FRAMES - 1)).toInt() }
    }

    /** Normaliza y prepara exactamente 75 cuadros en formato row-major. */
    fun buildInputTensor(frames: List<FloatArray>): FloatArray {
        require(frames.isNotEmpty()) { "secuencia vacía: no se ejecuta inferencia" }
        frames.forEachIndexed { index, frame ->
            require(frame.size == COORDS) {
                "cuadro $index tiene ${frame.size} coords, se esperaban $COORDS"
            }
            frame.forEach { value -> require(value.isFinite()) { "valor no finito en cuadro $index" } }
        }

        val normalized = normalize(frames)
        val indices = sampleIndices(normalized.size)
        val tensor = FloatArray(FRAMES * COORDS)
        indices.forEachIndexed { row, sourceIndex ->
            System.arraycopy(normalized[sourceIndex], 0, tensor, row * COORDS, COORDS)
        }
        // El padding temporal del productor es cero, no repetición del último cuadro.
        return tensor
    }

    private fun normalize(frames: List<FloatArray>): Array<FloatArray> {
        val normalized = Array(frames.size) { index -> frames[index].copyOf() }
        var scale = 0f

        normalized.forEach { frame ->
            var sumX = 0f
            var sumY = 0f
            var offset = 0
            while (offset < COORDS) {
                sumX += frame[offset]
                sumY += frame[offset + 1]
                offset += 3
            }
            val centerX = sumX / LANDMARKS
            val centerY = sumY / LANDMARKS

            offset = 0
            while (offset < COORDS) {
                frame[offset] -= centerX
                frame[offset + 1] -= centerY
                scale = maxOf(scale, kotlin.math.abs(frame[offset]), kotlin.math.abs(frame[offset + 1]))
                offset += 3
            }
        }

        if (scale > EPSILON) {
            normalized.forEach { frame ->
                var offset = 0
                while (offset < COORDS) {
                    frame[offset] /= scale
                    frame[offset + 1] /= scale
                    offset += 3
                }
            }
        }
        return normalized
    }
}
