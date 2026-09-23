package com.helpi.conversation.keypoints

/**
 * CONTRATO DE KEYPOINTS — única fuente de verdad en este repositorio.
 *
 * Este archivo espeja al productor de Python en helpi-ml. Una divergencia acá
 * no produce ninguna excepción: produce traducciones incorrectas.
 *
 * Vector de 168 coordenadas por cuadro:
 *   [0, 63)    mano izquierda, 21 landmarks × (x, y, z)
 *   [63, 126)  mano derecha,   21 landmarks × (x, y, z)
 *   [126, 168) pose 11..24,    14 landmarks × (x, y, z)
 *
 * - Orden de aplanado agrupado por landmark, ejes intercalados:
 *   [x0, y0, z0, x1, y1, z1, ...]
 * - Pose 0..10 (cara) y 25..32 (piernas) se descartan.
 * - No detectado -> ceros.
 * - Centrado: se resta el punto medio de los hombros (pose 11 y 12) a x e y.
 *   z NO se centra. Los landmarks ausentes conservan sus ceros.
 * - Muestreo temporal con aritmética ENTERA exacta.
 */
object KeypointContract {

    const val FRAMES: Int = 40
    const val COORDS: Int = 168

    const val HAND_LANDMARKS: Int = 21
    const val POSE_SOURCE_START: Int = 11
    const val POSE_SOURCE_END_EXCLUSIVE: Int = 25
    const val POSE_LANDMARKS: Int = POSE_SOURCE_END_EXCLUSIVE - POSE_SOURCE_START

    const val LEFT_HAND_OFFSET: Int = 0
    const val RIGHT_HAND_OFFSET: Int = 63
    const val POSE_OFFSET: Int = 126

    /** Pose 11 pasa a ser el primer landmark del bloque reducido. */
    const val LEFT_SHOULDER_OFFSET: Int = POSE_OFFSET
    /** Pose 12 pasa a ser el segundo landmark del bloque reducido. */
    const val RIGHT_SHOULDER_OFFSET: Int = POSE_OFFSET + 3

    /**
     * Aplana los landmarks de un cuadro al vector de 168 coordenadas.
     *
     * Cada lista viene como floats [x0, y0, z0, x1, ...] del propio landmark
     * o `null` si el componente no fue detectado (se rellena con ceros).
     * La pose puede traer 33 landmarks o los primeros 25. Se copian solamente
     * los índices de origen 11..24.
     */
    fun flattenFrame(
        leftHand: FloatArray?,
        rightHand: FloatArray?,
        pose: FloatArray?,
    ): FloatArray {
        val frame = FloatArray(COORDS)
        copyBlock(leftHand, frame, LEFT_HAND_OFFSET, HAND_LANDMARKS * 3, "mano izquierda")
        copyBlock(rightHand, frame, RIGHT_HAND_OFFSET, HAND_LANDMARKS * 3, "mano derecha")
        if (pose != null) {
            require(pose.size == 25 * 3 || pose.size == 33 * 3) {
                "pose debe tener 25 o 33 landmarks, llegaron ${pose.size / 3}"
            }
            System.arraycopy(
                pose,
                POSE_SOURCE_START * 3,
                frame,
                POSE_OFFSET,
                POSE_LANDMARKS * 3,
            )
        }
        return frame
    }

    private fun copyBlock(src: FloatArray?, dst: FloatArray, offset: Int, size: Int, name: String) {
        if (src == null) return // no detectado -> ceros
        require(src.size == size) { "$name debe tener $size floats, llegaron ${src.size}" }
        System.arraycopy(src, 0, dst, offset, size)
    }

    /**
     * Centra x e y restando el punto medio de los hombros. z no se toca.
     * Los landmarks ausentes (todo en cero) conservan sus ceros.
     *
     * Devuelve un cuadro nuevo; no modifica la entrada.
     *
     * Si algún hombro está ausente, devuelve una copia sin centrar: no inventa
     * un centro y mantiene el mismo comportamiento que el productor Python.
     */
    fun centerFrame(frame: FloatArray): FloatArray {
        require(frame.size == COORDS) { "cuadro de ${frame.size} coords, se esperaban $COORDS" }
        if (!hasShoulders(frame)) return frame.copyOf()

        val cx = (frame[LEFT_SHOULDER_OFFSET] + frame[RIGHT_SHOULDER_OFFSET]) / 2f
        val cy = (frame[LEFT_SHOULDER_OFFSET + 1] + frame[RIGHT_SHOULDER_OFFSET + 1]) / 2f

        val out = frame.copyOf()
        var i = 0
        while (i < COORDS) {
            val x = out[i]
            val y = out[i + 1]
            val z = out[i + 2]
            // landmark ausente = (0,0,0) exacto: conserva sus ceros
            if (x != 0f || y != 0f || z != 0f) {
                out[i] = x - cx
                out[i + 1] = y - cy
                // z NO se centra
            }
            i += 3
        }
        return out
    }

    /** Verdadero si ambos hombros (pose 11 y 12) tienen algún valor no nulo. */
    fun hasShoulders(frame: FloatArray): Boolean {
        require(frame.size == COORDS) { "cuadro de ${frame.size} coords, se esperaban $COORDS" }
        return !isZero(frame, LEFT_SHOULDER_OFFSET) && !isZero(frame, RIGHT_SHOULDER_OFFSET)
    }

    private fun isZero(frame: FloatArray, offset: Int): Boolean =
        frame[offset] == 0f && frame[offset + 1] == 0f && frame[offset + 2] == 0f

    /**
     * Índices de muestreo temporal para T cuadros disponibles.
     *
     * idx[i] = (i * (T - 1)) / (N - 1) con división ENTERA. Nunca punto
     * flotante: linspace en float difiere del entero en ~3% de los largos
     * (ej. T=46, i=13: float da 14, entero da 15).
     *
     * Si T < N se repite el último índice hasta completar [FRAMES].
     */
    fun sampleIndices(totalFrames: Int): IntArray {
        require(totalFrames > 0) { "T debe ser > 0" }
        if (totalFrames < FRAMES) {
            return IntArray(FRAMES) { i -> minOf(i, totalFrames - 1) }
        }
        return IntArray(FRAMES) { i -> (i * (totalFrames - 1)) / (FRAMES - 1) }
    }

    /**
     * Selecciona exactamente [FRAMES] cuadros de la secuencia.
     * Si T < N se repite el último cuadro (padding final).
     * No modifica los cuadros originales.
     */
    fun sampleFrames(frames: List<FloatArray>): List<FloatArray> {
        require(frames.isNotEmpty()) { "secuencia vacía: no se ejecuta inferencia" }
        frames.forEachIndexed { i, f ->
            require(f.size == COORDS) { "cuadro $i tiene ${f.size} coords, se esperaban $COORDS" }
        }
        val idx = sampleIndices(frames.size)
        val out = ArrayList<FloatArray>(FRAMES)
        for (j in idx) out.add(frames[j])
        return out
    }

    /**
     * Cadena completa: centra cada cuadro y arma el tensor [FRAMES]×[COORDS]
     * aplanado (row-major) listo para LiteRT.
     */
    fun buildInputTensor(frames: List<FloatArray>): FloatArray {
        val sampled = sampleFrames(frames)
        val tensor = FloatArray(FRAMES * COORDS)
        sampled.forEachIndexed { row, frame ->
            val centered = centerFrame(frame)
            centered.forEach { v ->
                require(v.isFinite()) { "valor no finito en el cuadro $row" }
            }
            System.arraycopy(centered, 0, tensor, row * COORDS, COORDS)
        }
        return tensor
    }
}
