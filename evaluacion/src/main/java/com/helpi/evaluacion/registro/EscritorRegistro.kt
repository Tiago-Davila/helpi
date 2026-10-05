package com.helpi.evaluacion.registro

import android.database.sqlite.SQLiteFullException
import com.helpi.evaluacion.datos.IntentoDao
import com.helpi.evaluacion.datos.PrediccionTopDato
import com.helpi.evaluacion.datos.entidades.IntentoEntity
import java.io.Closeable
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

data class RegistroIntento(
    val intento: IntentoEntity,
    val top3: List<PrediccionTopDato>,
    val momento: Long
) {
    val top3Inmutable: List<PrediccionTopDato> = Collections.unmodifiableList(top3.toList())
}

/** Persiste intentos en un hilo propio y nunca espera por él al ser invocado desde reconocimiento. */
class EscritorRegistro internal constructor(
    private val persistir: suspend (RegistroIntento, Int) -> Unit,
    capacidadCola: Int = CAPACIDAD_COLA
) : Closeable {
    constructor(dao: IntentoDao) : this(
        persistir = { registro, perdidos ->
            dao.registrarIntento(
                registro.intento,
                registro.top3Inmutable,
                registro.momento,
                perdidos
            )
        }
    )

    private val cola = Channel<RegistroIntento>(capacidadCola)
    private val perdidasPendientes = mutableMapOf<String, Int>()
    private val lockPerdidas = Any()
    private val dispatcher = Executors.newSingleThreadExecutor { tarea ->
        Thread(tarea, "helpi-evaluacion-registro").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + dispatcher)
    private val worker = scope.launch {
        for (registro in cola) {
            procesar(registro)
        }
    }

    init {
        require(capacidadCola > 0) { "capacidadCola debe ser positiva" }
    }

    /** Devuelve inmediatamente; una cola llena se contabiliza en memoria para esa sesión. */
    fun trySend(registro: RegistroIntento): Boolean {
        val resultado = cola.trySend(registro)
        if (resultado.isFailure) registrarPerdida(registro.intento.sesionId, 1)
        return resultado.isSuccess
    }

    private suspend fun procesar(registro: RegistroIntento) {
        val sesionId = registro.intento.sesionId
        val perdidos = extraerPerdidas(sesionId)
        try {
            persistir(registro, perdidos)
        } catch (cancelacion: CancellationException) {
            registrarPerdida(sesionId, sumarSaturado(perdidos, 1))
            throw cancelacion
        } catch (_: SQLiteFullException) {
            registrarPerdida(sesionId, sumarSaturado(perdidos, 1))
        } catch (_: Exception) {
            registrarPerdida(sesionId, sumarSaturado(perdidos, 1))
        }
    }

    private fun extraerPerdidas(sesionId: String): Int = synchronized(lockPerdidas) {
        perdidasPendientes.remove(sesionId) ?: 0
    }

    private fun registrarPerdida(sesionId: String, cantidad: Int) {
        synchronized(lockPerdidas) {
            val actual = perdidasPendientes[sesionId] ?: 0
            perdidasPendientes[sesionId] = sumarSaturado(actual, cantidad)
        }
    }

    private fun sumarSaturado(actual: Int, cantidad: Int): Int =
        (actual.toLong() + cantidad).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Cierra la entrada, permite vaciar la cola y espera al hilo escritor. */
    suspend fun cerrarYEsperar() {
        cola.close()
        worker.join()
        job.cancel()
        dispatcher.close()
    }

    override fun close() {
        cola.close()
        job.cancel()
        dispatcher.close()
    }

    companion object {
        const val CAPACIDAD_COLA = 64
    }
}
