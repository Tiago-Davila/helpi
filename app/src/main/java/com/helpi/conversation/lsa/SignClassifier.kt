package com.helpi.conversation.lsa

import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runner LiteRT del clasificador de señas (Eva). Devuelve la salida numérica
 * cruda; la traducción a glosa la hace el catálogo y la aceptación la decide
 * [com.helpi.conversation.lsa.SignAcceptancePolicy].
 *
 * El modelo usa solo ops elementales: si exigiera el delegado Flex es un
 * defecto del modelo, no algo a resolver agregando la librería.
 *
 * No es thread-safe: una sola inferencia activa, desde el executor LiteRT.
 */
class SignClassifier(bundle: ModelBundle) : Closeable {

    private val interpreter = Interpreter(
        bundle.model,
        Interpreter.Options().apply { numThreads = 2 },
    )
    private val frames = bundle.manifest.frames
    private val coords = bundle.manifest.coords
    private val numClasses = bundle.manifest.numClasses

    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(4 * frames * coords).order(ByteOrder.nativeOrder())
    private val outputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(4 * numClasses).order(ByteOrder.nativeOrder())

    init {
        val inShape = interpreter.getInputTensor(0).shape()
        val expected = intArrayOf(1, frames, coords)
        require(inShape.contentEquals(expected)) {
            "entrada del modelo ${inShape.contentToString()} != ${expected.contentToString()}"
        }
        val outShape = interpreter.getOutputTensor(0).shape()
        require(outShape.contentEquals(intArrayOf(1, numClasses))) {
            "salida del modelo ${outShape.contentToString()} != [1, $numClasses]"
        }
    }

    /**
     * @param inputTensor tensor 40×201 aplanado producido por KeypointContract.
     * @return scores crudos (logits o probabilidades según el manifiesto).
     */
    fun classify(inputTensor: FloatArray): FloatArray {
        require(inputTensor.size == frames * coords) {
            "tensor de ${inputTensor.size} floats, se esperaban ${frames * coords}"
        }
        inputBuffer.rewind()
        for (v in inputTensor) inputBuffer.putFloat(v)
        inputBuffer.rewind()
        outputBuffer.rewind()

        interpreter.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        val out = FloatArray(numClasses)
        for (i in out.indices) out[i] = outputBuffer.float
        return out
    }

    override fun close() {
        interpreter.close()
    }
}
