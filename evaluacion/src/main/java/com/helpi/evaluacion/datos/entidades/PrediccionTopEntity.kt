package com.helpi.evaluacion.datos.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import com.helpi.evaluacion.dominio.intento.ReglasIntento

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
        require(ReglasIntento.indiceValido(indice)) {
            "indice debe estar entre 0 y ${ReglasIntento.ULTIMO_INDICE}"
        }
        require(confianza.isFinite() && confianza in 0f..1f) { "confianza debe estar entre 0 y 1" }
    }
}
