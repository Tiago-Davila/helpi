package com.helpi.conversation.lsa.sequence

import com.helpi.conversation.keypoints.SequenceKeypointContract
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable

/** Resultado candidato: siempre requiere confirmación de la interfaz. */
data class SequenceTranslationCandidate(
    val text: String,
    val tokenIds: List<Int>,
    val terminatedByEos: Boolean,
)

/**
 * Runner offline del paquete encoder-decoder LSA-T.
 *
 * El runner no comparte estado ni buffers con Eva. El encoder se ejecuta una
 * vez por captura y el decoder se ejecuta autoregresivamente, siempre en el
 * executor del llamador. La salida es un candidato experimental: esta clase
 * no lo publica ni lo envía a voz automáticamente.
 */
class SequenceTranslator(
    bundle: SequenceModelBundle,
    options: Interpreter.Options = Interpreter.Options().apply { numThreads = 2 },
) : Closeable {

    private val bundle = bundle
    private val encoder = Interpreter(bundle.encoder.duplicate(), options)
    private val decoder = Interpreter(bundle.decoder.duplicate(), options)

    private val encoderInput = encoder.getInputTensor(0)
    private val decoderInputIndices = IntArray(decoder.inputTensorCount) { it }
    private val decoderTokenInputIndex = decoderInputIndices.firstOrNull { index ->
        decoder.getInputTensor(index).dataType().isInteger
    } ?: error("decoder LSA-T sin entrada de tokens entera")
    private val decoderMemoryInputIndex = decoderInputIndices.firstOrNull { it != decoderTokenInputIndex }
        ?: error("decoder LSA-T sin entrada de memoria")

    init {
        require(encoderInput.dataType() == DataType.FLOAT32) {
            "encoder LSA-T debe recibir FLOAT32"
        }
        require(decoder.getInputTensor(decoderMemoryInputIndex).dataType() == DataType.FLOAT32) {
            "memoria del decoder LSA-T debe ser FLOAT32"
        }
        require(decoder.getOutputTensor(0).dataType() == DataType.FLOAT32) {
            "salida del decoder LSA-T debe ser FLOAT32"
        }
    }

    fun translate(inputTensor: FloatArray): SequenceTranslationCandidate {
        require(inputTensor.size == SequenceKeypointContract.FRAMES * SequenceKeypointContract.COORDS) {
            "tensor de ${inputTensor.size} floats, se esperaban " +
                "${SequenceKeypointContract.FRAMES * SequenceKeypointContract.COORDS}"
        }
        val memory = runEncoder(inputTensor)
        val tokenIds = ArrayList<Int>(bundle.manifest.maxTokens)
        var prefix = longArrayOf(bundle.manifest.bosId.toLong())
        var terminatedByEos = false

        for (step in 0 until bundle.manifest.maxTokens) {
            val logits = runDecoder(prefix, memory)
            val next = argMax(logits)
            tokenIds += next
            if (next == bundle.manifest.eosId) {
                terminatedByEos = true
                break
            }
            prefix += next.toLong()
        }

        return SequenceTranslationCandidate(
            text = bundle.vocabulary.decode(tokenIds),
            tokenIds = tokenIds.toList(),
            terminatedByEos = terminatedByEos,
        )
    }

    private fun runEncoder(inputTensor: FloatArray): FloatArray {
        val shape = encoderInput.shape()
        require(shape.size == 3 && shape[0] == 1) {
            "entrada del encoder LSA-T incompatible: ${shape.contentToString()}"
        }
        val input = createEncoderInput(inputTensor, shape)
        val outputs = HashMap<Int, Any>()
        for (index in 0 until encoder.outputTensorCount) {
            outputs[index] = floatArrayForShape(encoder.getOutputTensor(index).shape())
        }
        encoder.runForMultipleInputsOutputs(arrayOf(input), outputs)

        val embeddingIndex = (0 until encoder.outputTensorCount).firstOrNull { index ->
            encoder.getOutputTensor(index).shape().contains(256)
        } ?: error("encoder LSA-T no expone embeddings de 256 dimensiones")
        return flattenEmbeddings(
            outputs.getValue(embeddingIndex),
            encoder.getOutputTensor(embeddingIndex).shape(),
        )
    }

    private fun runDecoder(prefix: LongArray, memory: FloatArray): FloatArray {
        val tokenShape = intArrayOf(1, prefix.size)
        val memoryShape = memoryShape(decoder.getInputTensor(decoderMemoryInputIndex).shape())
        decoder.resizeInput(decoderTokenInputIndex, tokenShape, false)
        decoder.resizeInput(decoderMemoryInputIndex, memoryShape, false)
        decoder.allocateTensors()

        val inputs = Array<Any>(decoder.inputTensorCount) {
            FloatArray(0)
        }
        inputs[decoderTokenInputIndex] = when (decoder.getInputTensor(decoderTokenInputIndex).dataType()) {
            DataType.INT32 -> Array(1) { IntArray(prefix.size) { prefix[it].toInt() } }
            DataType.INT64 -> Array(1) { prefix.copyOf() }
            else -> error("tokens del decoder LSA-T deben ser INT32 o INT64")
        }
        inputs[decoderMemoryInputIndex] = createMemoryInput(
            memory,
            decoder.getInputTensor(decoderMemoryInputIndex).shape(),
        )

        val outputIndex = 0
        val output = floatArrayForShape(decoder.getOutputTensor(outputIndex).shape())
        decoder.runForMultipleInputsOutputs(inputs, mapOf(outputIndex to output))
        return flattenLastLogits(output, decoder.getOutputTensor(outputIndex).shape())
    }

    private fun createEncoderInput(values: FloatArray, shape: IntArray): Any {
        val first = shape[1]
        val second = shape[2]
        val result = Array(1) { Array(first) { FloatArray(second) } }
        when {
            first == SequenceKeypointContract.COORDS && second == SequenceKeypointContract.FRAMES -> {
                for (frame in 0 until SequenceKeypointContract.FRAMES) {
                    for (coord in 0 until SequenceKeypointContract.COORDS) {
                        result[0][coord][frame] = values[frame * SequenceKeypointContract.COORDS + coord]
                    }
                }
            }
            first == SequenceKeypointContract.FRAMES && second == SequenceKeypointContract.COORDS -> {
                for (frame in 0 until SequenceKeypointContract.FRAMES) {
                    System.arraycopy(
                        values,
                        frame * SequenceKeypointContract.COORDS,
                        result[0][frame],
                        0,
                        SequenceKeypointContract.COORDS,
                    )
                }
            }
            else -> error("forma de entrada del encoder LSA-T incompatible: ${shape.contentToString()}")
        }
        return result
    }

    private fun createMemoryInput(values: FloatArray, shape: IntArray): Any {
        require(shape.size == 3 && shape[0] == 1) {
            "memoria del decoder LSA-T incompatible: ${shape.contentToString()}"
        }
        val first = shape[1]
        val second = shape[2]
        val result = Array(1) { Array(first) { FloatArray(second) } }
        when {
            first == 256 && second == SequenceKeypointContract.FRAMES -> {
                for (channel in 0 until 256) {
                    for (frame in 0 until SequenceKeypointContract.FRAMES) {
                        result[0][channel][frame] = values[frame * 256 + channel]
                    }
                }
            }
            first == SequenceKeypointContract.FRAMES && second == 256 -> {
                for (frame in 0 until SequenceKeypointContract.FRAMES) {
                    System.arraycopy(values, frame * 256, result[0][frame], 0, 256)
                }
            }
            else -> error("layout de memoria del decoder LSA-T incompatible: ${shape.contentToString()}")
        }
        return result
    }

    private fun memoryShape(current: IntArray): IntArray = when {
        current.size == 3 && current[1] == 256 -> intArrayOf(1, 256, SequenceKeypointContract.FRAMES)
        current.size == 3 && current[2] == 256 -> intArrayOf(1, SequenceKeypointContract.FRAMES, 256)
        else -> error("forma de memoria del decoder LSA-T incompatible: ${current.contentToString()}")
    }

    private fun flattenEmbeddings(output: Any, shape: IntArray): FloatArray {
        require(shape.size == 3 && shape[0] == 1) {
            "salida de embeddings incompatible: ${shape.contentToString()}"
        }
        val nested = output as Array<Array<FloatArray>>
        return when {
            shape[1] == SequenceKeypointContract.FRAMES && shape[2] == 256 ->
                FloatArray(SequenceKeypointContract.FRAMES * 256) { index ->
                    nested[0][index / 256][index % 256]
                }
            shape[1] == 256 && shape[2] == SequenceKeypointContract.FRAMES ->
                FloatArray(SequenceKeypointContract.FRAMES * 256) { index ->
                    nested[0][index % 256][index / 256]
                }
            else -> error("salida de embeddings incompatible: ${shape.contentToString()}")
        }
    }

    private fun flattenLastLogits(output: Any, shape: IntArray): FloatArray {
        require(shape.size == 3 && shape[0] == 1 && shape[2] == bundle.vocabulary.size) {
            "salida del decoder LSA-T incompatible: ${shape.contentToString()}"
        }
        val nested = output as Array<Array<FloatArray>>
        return nested[0][shape[1] - 1].copyOf()
    }

    private fun floatArrayForShape(shape: IntArray): Any {
        require(shape.size == 3 && shape.all { it > 0 }) {
            "tensor FLOAT32 dinámico no resuelto: ${shape.contentToString()}"
        }
        return Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) } }
    }

    private fun argMax(values: FloatArray): Int {
        require(values.isNotEmpty()) { "decoder LSA-T devolvió logits vacíos" }
        var best = 0
        for (index in 1 until values.size) {
            if (values[index] > values[best]) best = index
        }
        return best
    }

    override fun close() {
        encoder.close()
        decoder.close()
    }
}

private val DataType.isInteger: Boolean
    get() = this == DataType.INT32 || this == DataType.INT64
