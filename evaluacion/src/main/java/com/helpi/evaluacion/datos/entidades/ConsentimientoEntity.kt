package com.helpi.evaluacion.datos.entidades

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "consentimiento")
data class ConsentimientoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val avisoVersion: String,
    val avisoVideoSha256: String,
    val otorgadoEn: Long,
    val revocadoEn: Long?
) {
    init {
        require(avisoVideoSha256.matches(SHA256_REGEX)) {
            "avisoVideoSha256 debe tener 64 caracteres hexadecimales"
        }
    }
}
