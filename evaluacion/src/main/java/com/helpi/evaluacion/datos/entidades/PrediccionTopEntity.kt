package com.helpi.evaluacion.datos.entidades

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "prediccion_top",
    primaryKeys = ["intentoId", "rango"],
    foreignKeys = [
        ForeignKey(
            entity = IntentoEntity::class,
            parentColumns = ["id"],
            childColumns = ["intentoId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PrediccionTopEntity(
    val intentoId: Long,
    val rango: Int,
    val indice: Int,
    val glosa: String,
    val confianza: Float
) {
    init {
        require(rango in 1..3) { "rango debe estar entre 1 y 3" }
        require(indice in 0..63) { "indice debe estar entre 0 y 63" }
        require(confianza.isFinite() && confianza in 0f..1f) { "confianza debe estar entre 0 y 1" }
    }
}
