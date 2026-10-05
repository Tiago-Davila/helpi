package com.helpi.evaluacion.registro

import com.helpi.conversation.lsa.AcceptanceReason
import com.helpi.conversation.lsa.SignDecision
import com.helpi.conversation.observation.NoResultCause
import com.helpi.conversation.observation.RecognitionObserver
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface RegistroEvento {
    data class SegmentoIniciado(val inicioMs: Long) : RegistroEvento

    data class SinResultado(val causa: NoResultCause) : RegistroEvento

    data class Decision(
        val aceptada: Boolean,
        val indice: Int,
        val confianza: Float,
        val razon: AcceptanceReason,
        val predicciones: List<PrediccionReconocida>,
        val umbral: Float,
        val finSegmentoMs: Long
    ) : RegistroEvento
}

data class PrediccionReconocida(val indice: Int, val confianza: Float)

/** Copia y reenvía los eventos del puerto; no resuelve intentos ni accede al almacenamiento. */
class RegistroObserver(bufferCapacity: Int = 64) : RecognitionObserver {
    private val _eventos = MutableSharedFlow<RegistroEvento>(
        extraBufferCapacity = bufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val eventos: SharedFlow<RegistroEvento> = _eventos.asSharedFlow()

    init {
        require(bufferCapacity > 0) { "bufferCapacity debe ser positivo" }
    }

    override fun onSegmentStarted(startMs: Long) {
        _eventos.tryEmit(RegistroEvento.SegmentoIniciado(startMs))
    }

    override fun onNoResult(cause: NoResultCause) {
        _eventos.tryEmit(RegistroEvento.SinResultado(cause))
    }

    override fun onDecision(decision: SignDecision, thresholdUsed: Float, segmentEndMs: Long) {
        _eventos.tryEmit(
            RegistroEvento.Decision(
                aceptada = decision.accepted,
                indice = decision.classIndex,
                confianza = decision.confidence,
                razon = decision.reason,
                predicciones = decision.predictions.map {
                    PrediccionReconocida(it.classIndex, it.confidence)
                },
                umbral = thresholdUsed,
                finSegmentoMs = segmentEndMs
            )
        )
    }
}
