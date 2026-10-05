package com.helpi.evaluacion.registro

import android.database.sqlite.SQLiteFullException
import com.helpi.evaluacion.datos.entidades.CausaSinResultado
import com.helpi.evaluacion.datos.entidades.IntentoEntity
import com.helpi.evaluacion.datos.entidades.ResultadoIntento
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EscritorRegistroTest {
    @Test
    fun encolaSinHacerLaPersistenciaEnElHiloLlamador() = runBlocking {
        val persistido = CountDownLatch(1)
        val hiloPersistencia = AtomicReference<String>()
        val hiloLlamador = Thread.currentThread().name
        val escritor = EscritorRegistro({ _, _ ->
            hiloPersistencia.set(Thread.currentThread().name)
            persistido.countDown()
        })

        try {
            assertTrue(escritor.trySend(registro()))
            assertTrue(persistido.await(5, TimeUnit.SECONDS))
            assertNotEquals(hiloLlamador, hiloPersistencia.get())
        } finally {
            escritor.cerrarYEsperar()
        }
    }

    @Test
    fun colaLlenaNoLanzaYElPerdidoSeSumaEnLaProximaTransaccion() = runBlocking {
        val primerIntentoEnCurso = CountDownLatch(1)
        val liberarPrimero = CountDownLatch(1)
        val procesados = CountDownLatch(EscritorRegistro.CAPACIDAD_COLA + 1)
        val perdidasRegistradas = CopyOnWriteArrayList<Int>()
        val numeroLlamadas = AtomicInteger()
        val escritor = EscritorRegistro({ _, perdidos ->
            if (numeroLlamadas.incrementAndGet() == 1) {
                primerIntentoEnCurso.countDown()
                liberarPrimero.await(5, TimeUnit.SECONDS)
            }
            perdidasRegistradas += perdidos
            procesados.countDown()
        })

        try {
            assertTrue(escritor.trySend(registro()))
            assertTrue(primerIntentoEnCurso.await(5, TimeUnit.SECONDS))
            repeat(EscritorRegistro.CAPACIDAD_COLA) {
                assertTrue(escritor.trySend(registro()))
            }
            assertFalse(escritor.trySend(registro()))

            liberarPrimero.countDown()
            assertTrue(procesados.await(5, TimeUnit.SECONDS))
            assertEquals(1, perdidasRegistradas.sum())
        } finally {
            liberarPrimero.countDown()
            escritor.cerrarYEsperar()
        }
    }

    @Test
    fun discoLlenoNoLanzaAlLlamadorYSeAcumulaParaElSiguienteIntento() = runBlocking {
        val llamadas = AtomicInteger()
        val segundaEscritura = CountDownLatch(1)
        val perdidasRegistradas = CopyOnWriteArrayList<Int>()
        val escritor = EscritorRegistro({ _, perdidos ->
            if (llamadas.getAndIncrement() == 0) {
                throw SQLiteFullException("disk full")
            }
            perdidasRegistradas += perdidos
            segundaEscritura.countDown()
        })

        try {
            assertTrue(escritor.trySend(registro()))
            assertTrue(escritor.trySend(registro()))
            assertTrue(segundaEscritura.await(5, TimeUnit.SECONDS))
            assertEquals(listOf(1), perdidasRegistradas)
        } finally {
            escritor.cerrarYEsperar()
        }
    }

    private fun registro() = RegistroIntento(
        intento = IntentoEntity(
            sesionId = "sesion-test",
            posicion = 1,
            bloque = 1,
            numeroIntento = 1,
            reemplazado = false,
            senaEsperadaIndice = 0,
            senaEsperadaGlosa = "CASA",
            vioVideo = false,
            resultado = ResultadoIntento.SIN_RESULTADO,
            causaSinResultado = CausaSinResultado.SIN_HOMBROS,
            predichoIndice = null,
            predichoGlosa = null,
            confianza = null,
            umbral = 0.7f,
            superoUmbral = false,
            correcto = false,
            descartado = false,
            loHiceMal = false,
            inicioRelMs = 0L,
            duracionSegmentoMs = null
        ),
        top3 = emptyList(),
        momento = 1L
    )
}
