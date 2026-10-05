package com.helpi.evaluacion.datos.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sesion",
    foreignKeys = [
        ForeignKey(
            entity = ParticipanteEntity::class,
            parentColumns = ["codigo"],
            childColumns = ["participanteCodigo"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ConsentimientoEntity::class,
            parentColumns = ["id"],
            childColumns = ["consentimientoId"]
        )
    ],
    indices = [Index("participanteCodigo"), Index("consentimientoId")]
)
data class SesionEntity(
    @PrimaryKey val id: String,
    val participanteCodigo: String,
    val consentimientoId: Long,
    val protocoloVersion: String,
    val semilla: Long,
    val estado: EstadoSesion,
    val bloqueActual: Int,
    val appVersionName: String,
    val appVersionCode: Int,
    val modeloVersion: String,
    val modeloSha256: String,
    val catalogoSha256: String,
    val contratoKeypoints: Int,
    val dispositivoFabricante: String,
    val dispositivoModelo: String,
    val sdkAndroid: Int,
    val creadaEn: Long,
    val ultimoIntentoEn: Long?,
    val ultimaExportacionEn: Long?,
    val intentosPerdidos: Int,
    val revision: Int
) {
    init {
        require(id.matches(UUID_V4_REGEX)) { "id debe ser un UUID v4" }
        require(bloqueActual in 1..4) { "bloqueActual debe estar entre 1 y 4" }
        require(modeloSha256.matches(SHA256_REGEX)) {
            "modeloSha256 debe tener 64 caracteres hexadecimales"
        }
        require(catalogoSha256.matches(SHA256_REGEX)) {
            "catalogoSha256 debe tener 64 caracteres hexadecimales"
        }
        require(intentosPerdidos >= 0) { "intentosPerdidos no puede ser negativo" }
        require(revision >= 0) { "revision no puede ser negativa" }
    }
}

internal val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
internal val UUID_V4_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
