package com.helpi.evaluacion.datos.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "intento",
    foreignKeys = [
        ForeignKey(
            entity = SesionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sesionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sesionId"),
        Index(value = ["sesionId", "posicion", "numeroIntento"], unique = true)
    ]
)
data class IntentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sesionId: String,
    val posicion: Int,
    val bloque: Int,
    val numeroIntento: Int,
    val reemplazado: Boolean,
    val senaEsperadaIndice: Int,
    val senaEsperadaGlosa: String,
    val vioVideo: Boolean,
    val resultado: ResultadoIntento,
    val causaSinResultado: CausaSinResultado?,
    val predichoIndice: Int?,
    val predichoGlosa: String?,
    val confianza: Float?,
    val umbral: Float,
    val superoUmbral: Boolean,
    val correcto: Boolean,
    val descartado: Boolean,
    val loHiceMal: Boolean,
    val inicioRelMs: Long,
    val duracionSegmentoMs: Long?
) {
    init {
        require(posicion in 1..192) { "posicion debe estar entre 1 y 192" }
        require(bloque in 1..4) { "bloque debe estar entre 1 y 4" }
        require(numeroIntento >= 1) { "numeroIntento debe ser al menos 1" }
        require(senaEsperadaIndice in 0..63) { "senaEsperadaIndice debe estar entre 0 y 63" }
        require(predichoIndice == null || predichoIndice in 0..63) {
            "predichoIndice debe estar entre 0 y 63"
        }
        require(confianza == null || confianza.isFinite() && confianza in 0f..1f) {
            "confianza debe estar entre 0 y 1"
        }
        require(umbral.isFinite() && umbral in 0f..1f) { "umbral debe estar entre 0 y 1" }
        require(causaSinResultado != null == (resultado == ResultadoIntento.SIN_RESULTADO)) {
            "causaSinResultado solo corresponde a SIN_RESULTADO"
        }
        if (resultado == ResultadoIntento.SIN_RESULTADO) {
            require(predichoIndice == null && predichoGlosa == null && confianza == null) {
                "SIN_RESULTADO no admite predicción ni confianza"
            }
            require(!superoUmbral && !correcto && !descartado) {
                "SIN_RESULTADO no puede superar umbral, ser correcto ni descartado"
            }
        }
    }
}
