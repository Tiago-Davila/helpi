package com.helpi.conversation.keypoints

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * CONTRATO DE KEYPOINTS Eva v3 — única fuente de verdad en este repositorio.
 *
 * Este archivo espeja al productor de Python (eva_contract.py en
 * entrenamiento-modelo/lsa64). Una divergencia acá no produce ninguna
 * excepción: produce traducciones incorrectas.
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
 * - Muestreo temporal con aritmética ENTERA exacta (ver [sampleIndices]).
 *
 * Normalización v3 (sobre los 40 cuadros ya muestreados), en este orden y en
 * float32, igual que Python:
 *   1. Landmarks presentes a píxeles: X = x·W, Y = y·H, Z = z·W.
 *   2. Centro por cuadro: punto medio de los hombros (pose 11 y 12) restado a
 *      X e Y; Z no se centra. Un cuadro sin ambos hombros usa el centro del
 *      cuadro válido más cercano (el anterior si hay empate).
 *   3. Escala s: mediana del ancho de hombros (distancia X/Y) sobre los cuadros
 *      con ambos hombros; con cantidad par, promedio de los dos centrales.
 *      Todo landmark presente se divide por s (X, Y y Z).
 *   4. Sin hombros en ningún cuadro: secuencia inválida.
 * Así la entrada queda en "anchos de hombro" y no depende de la cámara, la
 * orientación ni la distancia.
 */
object KeypointContract {

    const val CONTRACT_VERSION: Int = 3

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

    private const val MIN_SCALE = 1e-6f

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

    /** Verdadero si ambos hombros (pose 11 y 12) tienen algún valor no nulo. */
    fun hasShoulders(frame: FloatArray): Boolean {
        require(frame.size == COORDS) { "cuadro de ${frame.size} coords, se esperaban $COORDS" }
        return !isZero(frame, LEFT_SHOULDER_OFFSET) && !isZero(frame, RIGHT_SHOULDER_OFFSET)
    }

    private fun isZero(frame: FloatArray, offset: Int): Boolean =
        frame[offset] == 0f && frame[offset + 1] == 0f && frame[offset + 2] == 0f

    /**
     * Índices de muestreo temporal para T cuadros disponibles, con división
     * ENTERA. Nunca punto flotante: linspace en float difiere del entero en
     * ~3% de los largos (ej. T=46, i=13: float da 14, entero da 15).
     *
     * - T >= N: idx[i] = (i * (T - 1)) / (N - 1), submuestrea incluyendo el último.
     * - T <  N: idx[i] = (i * T) / N, estira la seña repitiendo cada cuadro
     *   N/T o N/T+1 veces. Con el teléfono a 12-24 fps un segmento suele
     *   tener menos de 40 cuadros; repetir solo el último (v2) dejaba la seña
     *   comprimida al principio, algo que el modelo nunca vio en LSA64.
     */
    fun sampleIndices(totalFrames: Int): IntArray {
        require(totalFrames > 0) { "T debe ser > 0" }
        if (totalFrames < FRAMES) {
            return IntArray(FRAMES) { i -> (i * totalFrames) / FRAMES }
        }
        return IntArray(FRAMES) { i -> (i * (totalFrames - 1)) / (FRAMES - 1) }
    }

    /**
     * Selecciona exactamente [FRAMES] cuadros de la secuencia.
     * Si T < N los cuadros se repiten de forma pareja (ver [sampleIndices]).
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
     * Normalización v3 sobre cuadros ya muestreados, crudos de MediaPipe
     * (x, y en [0, 1] relativos a la imagen de [imageWidth]×[imageHeight]).
     * Devuelve cuadros nuevos; no modifica la entrada.
     *
     * @throws IllegalArgumentException si ningún cuadro tiene ambos hombros.
     */
    fun normalizeSequence(frames: List<FloatArray>, imageWidth: Int, imageHeight: Int): List<FloatArray> {
        require(imageWidth > 0 && imageHeight > 0) { "imagen inválida: ${imageWidth}x$imageHeight" }
        require(frames.isNotEmpty()) { "secuencia vacía" }
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()

        // 1. a píxeles
        val px = frames.map { f ->
            require(f.size == COORDS) { "cuadro de ${f.size} coords, se esperaban $COORDS" }
            val out = f.copyOf()
            var i = 0
            while (i < COORDS) {
                out[i] *= w
                out[i + 1] *= h
                out[i + 2] *= w
                i += 3
            }
            out
        }

        // 3. escala: mediana del ancho de hombros en los cuadros válidos
        val valid = BooleanArray(px.size) { t -> hasShoulders(frames[t]) }
        val widths = ArrayList<Float>(px.size)
        val cx = FloatArray(px.size)
        val cy = FloatArray(px.size)
        for (t in px.indices) {
            val f = px[t]
            val lx = f[LEFT_SHOULDER_OFFSET]
            val ly = f[LEFT_SHOULDER_OFFSET + 1]
            val rx = f[RIGHT_SHOULDER_OFFSET]
            val ry = f[RIGHT_SHOULDER_OFFSET + 1]
            cx[t] = (lx + rx) / 2f
            cy[t] = (ly + ry) / 2f
            if (valid[t]) {
                val dx = lx - rx
                val dy = ly - ry
                widths.add(sqrt(dx * dx + dy * dy))
            }
        }
        require(widths.isNotEmpty()) { "secuencia sin hombros en ningún cuadro: no se puede normalizar" }
        val scale = median(widths)
        require(scale.isFinite() && scale > MIN_SCALE) { "ancho de hombros inválido: $scale" }

        // 2. centro del cuadro válido más cercano (el anterior si empata)
        val validIdx = valid.indices.filter { valid[it] }
        return px.indices.map { t ->
            val src = nearest(validIdx, t)
            val ox = cx[src]
            val oy = cy[src]
            val original = frames[t]
            val out = px[t]
            var i = 0
            while (i < COORDS) {
                // landmark ausente = (0,0,0) exacto en el cuadro crudo: conserva sus ceros
                if (original[i] != 0f || original[i + 1] != 0f || original[i + 2] != 0f) {
                    out[i] = (out[i] - ox) / scale
                    out[i + 1] = (out[i + 1] - oy) / scale
                    out[i + 2] = out[i + 2] / scale
                }
                i += 3
            }
            out
        }
    }

    private fun median(values: List<Float>): Float {
        val v = values.sorted()
        val n = v.size
        return if (n % 2 == 1) v[n / 2] else (v[n / 2 - 1] + v[n / 2]) / 2f
    }

    private fun nearest(validIdx: List<Int>, t: Int): Int {
        var best = validIdx[0]
        for (i in validIdx) {
            if (abs(i - t) < abs(best - t)) best = i
        }
        return best
    }

    /**
     * Cadena completa: muestrea 40 cuadros, normaliza con el contrato v3 y
     * arma el tensor [FRAMES]×[COORDS] aplanado (row-major) listo para LiteRT.
     *
     * [imageWidth]×[imageHeight] es el tamaño del cuadro que analizó
     * MediaPipe (ya rotado), el mismo para todos los cuadros del segmento.
     */
    fun buildInputTensor(frames: List<FloatArray>, imageWidth: Int, imageHeight: Int): FloatArray {
        val sampled = sampleFrames(frames)
        sampled.forEachIndexed { row, frame ->
            frame.forEach { v -> require(v.isFinite()) { "valor no finito en el cuadro $row" } }
        }
        val normalized = normalizeSequence(sampled, imageWidth, imageHeight)
        val tensor = FloatArray(FRAMES * COORDS)
        normalized.forEachIndexed { row, frame ->
            System.arraycopy(frame, 0, tensor, row * COORDS, COORDS)
        }
        return tensor
    }
}
