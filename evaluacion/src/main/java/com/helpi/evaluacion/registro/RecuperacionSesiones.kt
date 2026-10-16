package com.helpi.evaluacion.registro

import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.entidades.CausaInterrupcion
import com.helpi.evaluacion.datos.entidades.InterrupcionEntity
import com.helpi.evaluacion.datos.entidades.ResultadoIntento
import com.helpi.evaluacion.datos.entidades.SesionEntity
import com.helpi.evaluacion.dominio.sesion.CalculoPosicionRetoma

/** Persiste el punto de reanudación sin alterar los datos propios de la sesión. */
class RecuperacionSesiones(
    private val database: HelpiEvaluacionDatabase,
    private val reloj: () -> Long = System::currentTimeMillis
) {
    suspend fun alArrancar() = interrumpirSesionesEnCurso(CausaInterrupcion.PROCESO_TERMINADO)

    suspend fun alPasarSegundoPlano() = interrumpirSesionesEnCurso(CausaInterrupcion.SEGUNDO_PLANO)

    private suspend fun interrumpirSesionesEnCurso(causa: CausaInterrupcion) {
        database.sesionDao().listarEnCurso().forEach { sesion ->
            val posicionPendiente = primeraPosicionSinRespuesta(sesion)
            database.sesionDao().interrumpirSiEnCurso(
                InterrupcionEntity(
                    sesionId = sesion.id,
                    bloque = sesion.bloqueActual,
                    posicionPendiente = posicionPendiente,
                    causa = causa,
                    ocurrioEn = reloj()
                )
            )
        }
    }

    private suspend fun primeraPosicionSinRespuesta(sesion: SesionEntity): Int {
        val respondidas = database.intentoDao().listarDeSesion(sesion.id)
            .asSequence()
            .filter {
                it.bloque == sesion.bloqueActual && it.resultado != ResultadoIntento.SIN_RESULTADO
            }
            .map { it.posicion }
            .toSet()

        val posicion = CalculoPosicionRetoma
            .primerIntentoSinRespuesta(sesion.bloqueActual, respondidas)
        check(posicion.isPresent) {
            "La sesión ${sesion.id} está EN_CURSO pero el bloque ${sesion.bloqueActual} no tiene posiciones pendientes"
        }
        return posicion.asInt
    }
}
