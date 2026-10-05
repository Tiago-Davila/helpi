package com.helpi.evaluacion.datos.entidades

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "participante")
data class ParticipanteEntity(@PrimaryKey val codigo: String, val creadoEn: Long) {
    init {
        require(codigo.matches(Regex("^P-[0-9]{3,4}$"))) {
            "codigo debe cumplir el patrón P-NNN o P-NNNN"
        }
    }
}
