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
@Suppress("TooManyFunctions")
abstract class SesionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertar(sesion: SesionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertarCondiciones(condiciones: CondicionesPruebaEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertarInterrupcion(interrupcion: InterrupcionEntity): Long

    @Query("SELECT * FROM sesion WHERE id = :sesionId")
    abstract suspend fun buscar(sesionId: String): SesionEntity?

    @Query(
        "UPDATE sesion SET revision = :revision WHERE id = :sesionId AND revision = :revision - 1"
    )
    abstract suspend fun actualizarRevisionDeEnvio(sesionId: String, revision: Int): Int

    @Query("UPDATE sesion SET ultimaExportacionEn = :exportadoEn WHERE id = :sesionId")
    abstract suspend fun actualizarUltimaExportacionEn(sesionId: String, exportadoEn: Long): Int

    @Query(
        "UPDATE sesion SET revision = :revision, ultimaExportacionEn = :exportadoEn " +
            "WHERE id = :sesionId AND revision = :revision - 1"
    )
    abstract suspend fun actualizarExportacion(
        sesionId: String,
        revision: Int,
        exportadoEn: Long
    ): Int

    @Query("SELECT * FROM sesion WHERE estado = 'EN_CURSO' ORDER BY creadaEn")
    abstract suspend fun listarEnCurso(): List<SesionEntity>

    @Query("SELECT * FROM sesion")
    abstract suspend fun listarTodas(): List<SesionEntity>

    @Query("SELECT * FROM condiciones_prueba WHERE sesionId = :sesionId")
    abstract suspend fun condiciones(sesionId: String): CondicionesPruebaEntity?

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

    @Query("UPDATE sesion SET bloqueActual = :bloque WHERE id = :sesionId AND estado = 'EN_CURSO'")
    abstract suspend fun actualizarBloqueEnCurso(sesionId: String, bloque: Int): Int

    @Query("UPDATE sesion SET estado = 'COMPLETA' WHERE id = :sesionId AND estado = 'EN_CURSO'")
    abstract suspend fun marcarCompletaSiEnCurso(sesionId: String): Int

    @Query(
        "UPDATE sesion SET estado = 'TERMINADA_ANTES' " +
            "WHERE id = :sesionId AND estado = 'EN_CURSO'"
    )
    abstract suspend fun marcarTerminadaAntesSiEnCurso(sesionId: String): Int

    @Query(
        "UPDATE sesion SET estado = 'PAUSADA', bloqueActual = :bloque " +
            "WHERE id = :sesionId AND estado = 'EN_CURSO'"
    )
    protected abstract suspend fun actualizarAPausadaSiEnCurso(sesionId: String, bloque: Int): Int

    @Query("SELECT * FROM interrupcion WHERE sesionId = :sesionId ORDER BY id")
    abstract suspend fun interrupciones(sesionId: String): List<InterrupcionEntity>

    @Query("SELECT COUNT(*) FROM sesion")
    abstract suspend fun cantidadSesiones(): Int

    @Query("UPDATE sesion SET envioVencido = 1 WHERE id = :sesionId")
    abstract suspend fun marcarEnvioVencido(sesionId: String): Int

    @Query("DELETE FROM sesion WHERE id IN (:ids)")
    abstract suspend fun eliminar(ids: List<String>): Int

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
    open suspend fun iniciarBloque(
        sesionId: String,
        bloque: Int,
        avisoVersion: String,
        avisoVideoSha256: String
    ): Boolean {
        require(bloque in PRIMER_BLOQUE..ULTIMO_BLOQUE) { "bloque debe estar entre 1 y 4" }
        if (!tieneConsentimientoVigente(avisoVersion, avisoVideoSha256) ||
            contarOtraEnCurso(sesionId) > 0
        ) {
            return false
        }
        return actualizarABloqueEnCurso(sesionId, bloque) == 1
    }

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM consentimiento
            WHERE id = (SELECT MAX(id) FROM consentimiento)
                AND revocadoEn IS NULL
                AND avisoVersion = :avisoVersion
                AND avisoVideoSha256 = :avisoVideoSha256
        )
        """
    )
    protected abstract suspend fun tieneConsentimientoVigente(
        avisoVersion: String,
        avisoVideoSha256: String
    ): Boolean

    @Query("UPDATE sesion SET estado = 'INTERRUMPIDA' WHERE id = :sesionId AND estado = 'EN_CURSO'")
    abstract suspend fun marcarInterrumpidaSiEnCurso(sesionId: String): Int

    @Transaction
    open suspend fun interrumpirSiEnCurso(interrupcion: InterrupcionEntity): Boolean {
        if (marcarInterrumpidaSiEnCurso(interrupcion.sesionId) != 1) return false
        insertarInterrupcion(interrupcion)
        return true
    }

    @Transaction
    open suspend fun pausarSiEnCurso(interrupcion: InterrupcionEntity): Boolean {
        require(interrupcion.bloque in PRIMER_BLOQUE..ULTIMO_BLOQUE) {
            "bloque debe estar entre 1 y 4"
        }
        if (actualizarAPausadaSiEnCurso(interrupcion.sesionId, interrupcion.bloque) != 1) {
            return false
        }
        insertarInterrupcion(interrupcion)
        return true
    }
}

private const val PRIMER_BLOQUE = 1
private const val ULTIMO_BLOQUE = 4
