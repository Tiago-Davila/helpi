package com.helpi.evaluacion.registro

import com.helpi.conversation.lsa.SignAcceptancePolicy
import com.helpi.conversation.observation.NoResultCause
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistroObserverTest {
    @Test
    fun soloCopiaYReenviaEventosAlFlujoEnMemoria() = runBlocking {
        val observer = RegistroObserver()
        val eventos = async(start = CoroutineStart.UNDISPATCHED) {
            observer.eventos.take(3).toList()
        }
        observer.onSegmentStarted(120L)
        observer.onNoResult(NoResultCause.SIN_HOMBROS)
        observer.onDecision(
            SignAcceptancePolicy(0.7f, true, 64).evaluate(probabilidades()),
            0.7f,
            300L
        )

        val recibidos = eventos.await()
        assertEquals(RegistroEvento.SegmentoIniciado(120L), recibidos[0])
        assertEquals(RegistroEvento.SinResultado(NoResultCause.SIN_HOMBROS), recibidos[1])
        val decision = recibidos[2] as RegistroEvento.Decision
        assertTrue(decision.aceptada)
        assertEquals(0, decision.indice)
        assertEquals(3, decision.predicciones.size)
        assertEquals(0.7f, decision.umbral)
        assertEquals(300L, decision.finSegmentoMs)
    }

    private fun probabilidades(): FloatArray = FloatArray(64).apply {
        this[0] = 0.9f
        this[1] = 0.06f
        this[2] = 0.04f
    }
}
