package com.helpi.evaluacion.datos.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "envio",
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
data class EnvioEntity(
    @PrimaryKey val envioId: String,
    val sesionId: String,
    val revision: Int,
    val payload: ByteArray,
    val sha256: String,
    val estado: EstadoEnvio,
    val motivo: String?,
    val intentos: Int,
    val creadoEn: Long,
    val ultimoIntentoEn: Long?
) {
    init {
        require(envioId.matches(UUID_V4_REGEX)) { "envioId debe ser un UUID v4" }
        require(sha256.matches(SHA256_REGEX)) { "sha256 debe tener 64 caracteres hexadecimales" }
        require(revision >= 0) { "revision no puede ser negativa" }
        require(intentos >= 0) { "intentos no puede ser negativo" }
    }
}
