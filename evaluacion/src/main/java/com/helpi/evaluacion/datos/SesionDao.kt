package com.helpi.evaluacion.datos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.helpi.evaluacion.datos.entidades.CondicionesPruebaEntity
import com.helpi.evaluacion.datos.entidades.EstadoSesion
import com.helpi.evaluacion.datos.entidades.InterrupcionEntity
import com.helpi.evaluacion.datos.entidades.SesionEntity

@Dao
abstract class SesionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertar(sesion: SesionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertarCondiciones(condiciones: CondicionesPruebaEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertarInterrupcion(interrupcion: InterrupcionEntity): Long

    @Query("SELECT * FROM sesion WHERE id = :sesionId")
    abstract suspend fun buscar(sesionId: String): SesionEntity?

    @Query("SELECT * FROM sesion WHERE participanteCodigo = :codigo ORDER BY creadaEn")
    abstract suspend fun listarDeParticipante(codigo: String): List<SesionEntity>

    @Query("SELECT estado FROM sesion WHERE id = :sesionId")
    abstract suspend fun estado(sesionId: String): EstadoSesion?

    @Query("SELECT COUNT(*) FROM sesion WHERE estado = 'EN_CURSO' AND id != :sesionId")
    abstract suspend fun contarOtraEnCurso(sesionId: String): Int

    @Query(
        """
        UPDATE sesion SET estado = 'EN_CURSO', bloqueActual = :bloque
        WHERE id = :sesionId AND estado IN ('CREADA', 'PAUSADA', 'INTERRUMPIDA')
        """
    )
    abstract suspend fun actualizarABloqueEnCurso(sesionId: String, bloque: Int): Int

    @Query("SELECT * FROM interrupcion WHERE sesionId = :sesionId ORDER BY id")
    abstract suspend fun interrupciones(sesionId: String): List<InterrupcionEntity>

    @Query("SELECT COUNT(*) FROM sesion")
    abstract suspend fun cantidadSesiones(): Int

    @Query("SELECT COUNT(*) FROM condiciones_prueba WHERE sesionId = :sesionId")
    abstract suspend fun cantidadCondiciones(sesionId: String): Int

    @Query("SELECT COUNT(*) FROM interrupcion WHERE sesionId = :sesionId")
    abstract suspend fun cantidadInterrupciones(sesionId: String): Int

    @Transaction
    open suspend fun crearConCondiciones(
        sesion: SesionEntity,
        condiciones: CondicionesPruebaEntity
    ) {
        require(sesion.id == condiciones.sesionId) {
            "Las condiciones deben pertenecer a la sesión"
        }
        insertar(sesion)
        insertarCondiciones(condiciones)
    }

    @Transaction
    open suspend fun iniciarBloque(sesionId: String, bloque: Int): Boolean {
        require(bloque in 1..4) { "bloque debe estar entre 1 y 4" }
        if (contarOtraEnCurso(sesionId) > 0) return false
        return actualizarABloqueEnCurso(sesionId, bloque) == 1
    }
}
