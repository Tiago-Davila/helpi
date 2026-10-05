package com.helpi.evaluacion.datos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helpi.evaluacion.datos.entidades.EstadoDepuracionEntity

@Dao
interface DepuracionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(estado: EstadoDepuracionEntity)

    @Query("SELECT * FROM estado_depuracion WHERE id = 1")
    suspend fun obtener(): EstadoDepuracionEntity?
}
