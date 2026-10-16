package com.helpi.evaluacion.ui.protocolo

import com.helpi.conversation.lsa.SignAcceptancePolicy
import com.helpi.evaluacion.datos.entidades.CausaSinResultado
import com.helpi.evaluacion.datos.entidades.ResultadoIntento
import com.helpi.evaluacion.dominio.protocolo.GeneradorProtocolo
import com.helpi.evaluacion.registro.EscritorIntentos
import com.helpi.evaluacion.registro.RegistroEvento
import com.helpi.evaluacion.registro.RegistroIntento
import com.helpi.evaluacion.registro.RegistroObserver
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocoloViewModelTest {
    @Test
    fun descartarGuardaLaMarcaSinCambiarElResultadoNiPublicarLaGlosa() {
        val fixture = fixture()
        try {
            fixture.eventoDecision(aceptada = true)

            assertTrue(fixture.vm.state.value.puedeDescartar)
            assertTrue(fixture.vm.descartar())

            val intento = fixture.escritor.registros.single().intento
            assertTrue(intento.descartado)
            assertTrue(intento.correcto)
            assertNull(fixture.vm.state.value.glosaPredicha)
        } finally {
            fixture.cerrar()
        }
    }

    @Test
    fun alVencerLaVentanaSeEncolaElIntentoSinDescartarlo() {
        val fixture = fixture()
        try {
            fixture.eventoDecision(aceptada = true)
            fixture.reloj.avanzar(VENTANA_DESHACER_MS)

            val intento = fixture.escritor.registros.single().intento
            assertFalse(intento.descartado)
            assertEquals(ResultadoIntento.SOBRE_UMBRAL, intento.resultado)
            assertFalse(fixture.vm.state.value.puedeDescartar)
        } finally {
            fixture.cerrar()
        }
    }

    @Test
    fun debajoDelUmbralNoExponeLaGlosaPredichaEnElEstado() {
        val fixture = fixture()
        try {
            fixture.eventoDecision(aceptada = false)

            assertEquals(
                ResultadoIntento.BAJO_UMBRAL,
                fixture.escritor.registros.single().intento.resultado
            )
            assertNull(fixture.vm.state.value.glosaPredicha)
        } finally {
            fixture.cerrar()
        }
    }

    @Test
    fun quinceSegundosSinSegmentoRegistranTiempoAgotado() {
        val fixture = fixture()
        try {
            fixture.reloj.avanzar(TIEMPO_AGOTADO_MS - 1)
            assertTrue(fixture.escritor.registros.isEmpty())

            fixture.reloj.avanzar(1)

            val intento = fixture.escritor.registros.single().intento
            assertEquals(ResultadoIntento.SIN_RESULTADO, intento.resultado)
            assertEquals(CausaSinResultado.TIEMPO_AGOTADO, intento.causaSinResultado)
            assertTrue(fixture.observerEvents.isEmpty())
        } finally {
            fixture.cerrar()
        }
    }

    @Test
    fun repetirSoloSePermiteAnteSinResultadoYConservaLaPosicion() {
        val fixture = fixture()
        try {
            fixture.reloj.avanzar(TIEMPO_AGOTADO_MS)
            assertTrue(fixture.vm.repetirIntento())

            assertEquals(1, fixture.vm.state.value.posicion)
            assertEquals(2, fixture.vm.state.value.numeroIntento)
            assertFalse(fixture.vm.repetirIntento())

            fixture.eventoDecision(aceptada = true)
            assertFalse(fixture.vm.repetirIntento())
        } finally {
            fixture.cerrar()
        }
    }

    @Test
    fun alCompletarUnBloqueOfreceSeguirORetomarOtroDia() {
        val fixture = fixture(posicionInicial = 48)
        try {
            fixture.eventoDecision(aceptada = false)
            assertTrue(fixture.vm.continuar())
            assertEquals(EtapaProtocolo.PAUSA_BLOQUE, fixture.vm.state.value.etapa)
            assertEquals(1, fixture.vm.state.value.bloque)

            assertTrue(fixture.vm.seguir())
            assertEquals(49, fixture.vm.state.value.posicion)
            assertEquals(2, fixture.vm.state.value.bloque)
        } finally {
            fixture.cerrar()
        }
    }

    @Test
    fun puedeDejarLaPausaParaRetomarOtroDia() {
        val fixture = fixture(posicionInicial = 48)
        try {
            fixture.eventoDecision(aceptada = false)
            fixture.vm.continuar()

            assertTrue(fixture.vm.retomarOtroDia())
            assertEquals(EtapaProtocolo.RETOMAR_OTRO_DIA, fixture.vm.state.value.etapa)
            assertFalse(fixture.vm.seguir())
        } finally {
            fixture.cerrar()
        }
    }

    @Test
    fun descartarNoModificaCorrectoFrenteAlMismoIntentoNoDescartado() {
        val descartado = fixture()
        val normal = fixture()
        try {
            descartado.eventoDecision(aceptada = true)
            assertTrue(descartado.vm.descartar())

            normal.eventoDecision(aceptada = true)
            normal.reloj.avanzar(VENTANA_DESHACER_MS)

            val intentoDescartado = descartado.escritor.registros.single().intento
            val intentoNormal = normal.escritor.registros.single().intento
            assertTrue(intentoDescartado.descartado)
            assertFalse(intentoNormal.descartado)
            assertEquals(intentoDescartado.correcto, intentoNormal.correcto)
            assertTrue(intentoNormal.correcto)
        } finally {
            descartado.cerrar()
            normal.cerrar()
        }
    }

    @Test
    fun loHiceMalSeGuardaConElIntentoYQuedaDisponibleHastaElSiguiente() {
        val fixture = fixture()
        try {
            fixture.eventoDecision(aceptada = true)

            assertTrue(fixture.vm.marcarLoHiceMal())
            assertTrue(fixture.vm.state.value.loHiceMal)
            fixture.reloj.avanzar(VENTANA_DESHACER_MS)

            assertTrue(fixture.escritor.registros.single().intento.loHiceMal)
            assertFalse(fixture.vm.marcarLoHiceMal())
            assertTrue(fixture.vm.continuar())
            assertFalse(fixture.vm.state.value.loHiceMal)
        } finally {
            fixture.cerrar()
        }
    }

    private fun fixture(posicionInicial: Int = 1): Fixture {
        val reloj = RelojFalso()
        val observer = RegistroObserver()
        val escritor = EscritorFalso()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val events = CopyOnWriteArrayList<RegistroEvento>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            observer.eventos.collect { evento -> events.add(evento) }
        }
        val secuencia = GeneradorProtocolo.generar(20261005L).secuencia.toMutableList().apply {
            this[0] = 0
        }
        val vm = ProtocoloViewModel(
            configuracion = ProtocoloConfiguracion(
                sesionId = "sesion-test",
                secuencia = secuencia,
                glosas = List(64) { "SENA_$it" },
                inicioSesionMs = reloj.elapsedMs,
                posicionInicial = posicionInicial
            ),
            observer = observer,
            escritor = escritor,
            reloj = reloj,
            scope = scope
        )
        return Fixture(vm, reloj, observer, escritor, scope, events)
    }

    private class Fixture(
        val vm: ProtocoloViewModel,
        val reloj: RelojFalso,
        private val observer: RegistroObserver,
        val escritor: EscritorFalso,
        private val scope: CoroutineScope,
        val observerEvents: List<RegistroEvento>
    ) {
        fun eventoDecision(aceptada: Boolean) {
            observer.onSegmentStarted(reloj.elapsedMs)
            observer.onDecision(
                SignAcceptancePolicy(UMBRAL, true, 64).evaluate(puntuaciones(aceptada)),
                UMBRAL,
                reloj.elapsedMs + DURACION_SEGMENTO_MS
            )
        }

        fun cerrar() {
            vm.close()
            scope.cancel()
        }
    }

    private class EscritorFalso : EscritorIntentos {
        val registros = mutableListOf<RegistroIntento>()

        override fun trySend(registro: RegistroIntento): Boolean {
            registros += registro
            return true
        }

        override fun marcarLoHiceMal(sesionId: String, posicion: Int, numeroIntento: Int): Boolean =
            true
    }

    private class RelojFalso : RelojProtocolo {
        override var elapsedMs: Long = 1_000L
        override var wallTimeMs: Long = 1_791_213_907_000L
        private val esperas = mutableListOf<Pair<Long, CompletableDeferred<Unit>>>()

        override suspend fun esperarHasta(elapsedDeadlineMs: Long) {
            if (elapsedMs >= elapsedDeadlineMs) return
            val espera = CompletableDeferred<Unit>()
            esperas += elapsedDeadlineMs to espera
            try {
                espera.await()
            } finally {
                esperas.removeAll { it.second === espera }
            }
        }

        fun avanzar(milisegundos: Long) {
            elapsedMs += milisegundos
            wallTimeMs += milisegundos
            esperas.filter { (limite, _) -> limite <= elapsedMs }
                .forEach { (_, espera) -> espera.complete(Unit) }
        }
    }

    private companion object {
        const val UMBRAL = 0.5f
        const val DURACION_SEGMENTO_MS = 750L
        const val VENTANA_DESHACER_MS = 3_000L
        const val TIEMPO_AGOTADO_MS = 15_000L

        fun puntuaciones(aceptada: Boolean): FloatArray = FloatArray(64) { 0.001f }.apply {
            this[0] = if (aceptada) 0.8f else 0.4f
            this[1] = if (aceptada) 0.1f else 0.3f
            this[2] = if (aceptada) 0.05f else 0.2f
        }
    }
}
