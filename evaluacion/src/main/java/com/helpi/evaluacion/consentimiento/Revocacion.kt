package com.helpi.evaluacion.consentimiento

import androidx.room.withTransaction
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.entidades.CausaInterrupcion
import com.helpi.evaluacion.datos.entidades.InterrupcionEntity
import com.helpi.evaluacion.datos.entidades.ResultadoIntento
import com.helpi.evaluacion.datos.entidades.SesionEntity
import com.helpi.evaluacion.dominio.sesion.CalculoPosicionRetoma

/** Revoca el consentimiento y corta el trabajo asociado sin eliminar datos ya registrados. */
class Revocacion(
    private val database: HelpiEvaluacionDatabase,
    private val reloj: () -> Long = System::currentTimeMillis
) {
    suspend fun revocar(): Boolean {
        val ahora = reloj()
        return database.withTransaction {
            val consentimiento = database.consentimientoDao().ultimo()
                ?: return@withTransaction false
            if (consentimiento.revocadoEn != null) return@withTransaction false

            check(database.consentimientoDao().revocar(consentimiento.id, ahora) == 1) {
                "No se pudo revocar el consentimiento vigente"
            }

            val sesiones = database.sesionDao().listarEnCurso()
            sesiones.forEach { sesion ->
                check(
                    database.sesionDao().interrumpirSiEnCurso(
                        InterrupcionEntity(
                            sesionId = sesion.id,
                            bloque = sesion.bloqueActual,
                            posicionPendiente = primeraPosicionSinRespuesta(sesion),
                            causa = CausaInterrupcion.REVOCACION,
                            ocurrioEn = ahora
                        )
                    )
                ) { "No se pudo interrumpir la sesión ${sesion.id} al revocar" }
            }

            database.envioDao().eliminarPendientes()
            true
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
