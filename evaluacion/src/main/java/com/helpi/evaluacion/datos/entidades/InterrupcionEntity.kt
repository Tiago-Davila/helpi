package com.helpi.evaluacion.datos.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "interrupcion",
    foreignKeys = [
        ForeignKey(
            entity = SesionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sesionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sesionId")]
)
data class InterrupcionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sesionId: String,
    val bloque: Int,
    val posicionPendiente: Int,
    val causa: CausaInterrupcion,
    val ocurrioEn: Long
) {
    init {
        require(bloque in 1..4) { "bloque debe estar entre 1 y 4" }
        require(posicionPendiente in 1..192) { "posicionPendiente debe estar entre 1 y 192" }
    }
}
