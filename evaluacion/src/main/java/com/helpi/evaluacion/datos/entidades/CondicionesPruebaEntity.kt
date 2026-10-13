package com.helpi.evaluacion.datos.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "condiciones_prueba",
    foreignKeys = [
        ForeignKey(
            entity = SesionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sesionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CondicionesPruebaEntity(
    @PrimaryKey val sesionId: String,
    val entorno: Entorno,
    val tipoEntorno: TipoEntorno,
    val iluminacion: Iluminacion,
    val contraluz: Boolean,
    val distancia: Distancia,
    val manoDominante: ManoDominante,
    val guantes: Boolean,
    val soporteCamara: SoporteCamara,
    val perfilParticipante: PerfilParticipante
)
