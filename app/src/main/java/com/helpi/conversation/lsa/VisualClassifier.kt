package com.helpi.conversation.lsa

import java.io.Closeable

/**
 * Clasificador del canal visual: recibe la ventana del contrato de keypoints
 * aplanada y devuelve la salida numérica cruda. Cerrarlo libera el intérprete.
 */
interface VisualClassifier : Closeable {
    fun classify(inputTensor: FloatArray): FloatArray
}
