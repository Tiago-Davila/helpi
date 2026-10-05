package com.helpi.evaluacion.datos.entidades

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "estado_depuracion")
data class EstadoDepuracionEntity(
    @PrimaryKey val id: Int = 1,
    val ultimaHoraVistaEn: Long,
    val ultimaDepuracionEn: Long?
) {
    init {
        require(id == 1) { "estado_depuracion contiene una única fila con id = 1" }
    }
}
