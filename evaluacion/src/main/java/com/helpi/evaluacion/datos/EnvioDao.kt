package com.helpi.evaluacion.datos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helpi.evaluacion.datos.entidades.EnvioEntity
import com.helpi.evaluacion.datos.entidades.EstadoEnvio

@Dao
interface EnvioDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(envio: EnvioEntity)

    @Delete
    suspend fun eliminar(envio: EnvioEntity): Int

    @Query("SELECT * FROM envio WHERE envioId = :envioId")
    suspend fun buscar(envioId: String): EnvioEntity?

    @Query("SELECT * FROM envio WHERE sesionId = :sesionId ORDER BY creadoEn, envioId")
    suspend fun listarDeSesion(sesionId: String): List<EnvioEntity>

    @Query("SELECT * FROM envio ORDER BY creadoEn, envioId")
    suspend fun listarTodos(): List<EnvioEntity>

    @Query("DELETE FROM envio WHERE envioId = :envioId")
    suspend fun eliminarPorId(envioId: String): Int

    @Query("SELECT COUNT(*) FROM envio WHERE estado = :estado")
    suspend fun cantidad(estado: EstadoEnvio): Int
}
