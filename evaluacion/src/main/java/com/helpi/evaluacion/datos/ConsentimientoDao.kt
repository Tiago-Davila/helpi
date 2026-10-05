package com.helpi.evaluacion.datos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity

@Dao
interface ConsentimientoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(consentimiento: ConsentimientoEntity): Long

    @Query("SELECT * FROM consentimiento WHERE id = :id")
    suspend fun buscar(id: Long): ConsentimientoEntity?

    @Query("SELECT * FROM consentimiento ORDER BY id")
    suspend fun listar(): List<ConsentimientoEntity>

    @Query(
        "UPDATE consentimiento SET revocadoEn = :revocadoEn WHERE id = :id AND revocadoEn IS NULL"
    )
    suspend fun revocar(id: Long, revocadoEn: Long): Int
}
