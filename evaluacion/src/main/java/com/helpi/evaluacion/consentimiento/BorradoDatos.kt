package com.helpi.evaluacion.consentimiento

import androidx.room.withTransaction
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.entidades.EstadoEnvio
import com.helpi.evaluacion.datos.entidades.ParticipanteEntity

data class ResumenBorrado(
    val sesiones: Int,
    val intentos: Int,
    val enviosPendientes: Int,
    val sesionesYaCompartidas: List<String>
)

/** Borrado explícito en cascada; el historial de consentimiento queda separado y se conserva. */
class BorradoDatos(private val database: HelpiEvaluacionDatabase) {
    suspend fun listarParticipantes(): List<ParticipanteEntity> =
        database.participanteDao().listarTodos()

    suspend fun previsualizar(participanteCodigo: String?): ResumenBorrado =
        database.withTransaction { resumen(participanteCodigo) }

    suspend fun borrar(participanteCodigo: String?): ResumenBorrado = database.withTransaction {
        val resultado = resumen(participanteCodigo)
        if (participanteCodigo == null) {
            database.participanteDao().eliminarTodos()
        } else {
            database.participanteDao().buscar(participanteCodigo)?.let {
                database.participanteDao().eliminar(it)
            }
        }
        resultado
    }

    private suspend fun resumen(participanteCodigo: String?): ResumenBorrado {
        val sesiones = if (participanteCodigo == null) {
            database.sesionDao().listarTodas()
        } else {
            database.sesionDao().listarDeParticipante(participanteCodigo)
        }
        var intentos = 0
        var pendientes = 0
        val compartidas = mutableListOf<String>()
        sesiones.forEach { sesion ->
            intentos += database.intentoDao().cantidadDeSesion(sesion.id)
            pendientes += database.envioDao().listarDeSesion(sesion.id)
                .count { it.estado == EstadoEnvio.PENDIENTE }
            if (sesion.ultimaExportacionEn != null) compartidas += sesion.id
        }
        return ResumenBorrado(
            sesiones = sesiones.size,
            intentos = intentos,
            enviosPendientes = pendientes,
            sesionesYaCompartidas = compartidas
        )
    }
}
