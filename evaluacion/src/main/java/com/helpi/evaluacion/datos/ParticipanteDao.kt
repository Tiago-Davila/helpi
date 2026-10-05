package com.helpi.evaluacion.datos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helpi.evaluacion.datos.entidades.ParticipanteEntity

@Dao
interface ParticipanteDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(participante: ParticipanteEntity)

    @Delete
    suspend fun eliminar(participante: ParticipanteEntity): Int

    @Query("SELECT * FROM participante WHERE codigo = :codigo")
    suspend fun buscar(codigo: String): ParticipanteEntity?

    @Query("SELECT COUNT(*) FROM participante")
    suspend fun cantidad(): Int
}
