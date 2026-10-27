package com.helpi.evaluacion.datos.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.helpi.evaluacion.dominio.intento.ReglasIntento
import com.helpi.evaluacion.dominio.protocolo.GeneradorProtocolo

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
        require(posicion in 1..GeneradorProtocolo.INTENTOS) {
            "posicion debe estar entre 1 y ${GeneradorProtocolo.INTENTOS}"
        }
        require(bloque in 1..GeneradorProtocolo.CANTIDAD_BLOQUES) {
            "bloque debe estar entre 1 y ${GeneradorProtocolo.CANTIDAD_BLOQUES}"
        }
        require(numeroIntento >= 1) { "numeroIntento debe ser al menos 1" }
        require(ReglasIntento.indiceValido(senaEsperadaIndice)) {
            "senaEsperadaIndice debe estar entre 0 y ${ReglasIntento.ULTIMO_INDICE}"
        }
        require(predichoIndice == null || ReglasIntento.indiceValido(predichoIndice)) {
            "predichoIndice debe estar entre 0 y ${ReglasIntento.ULTIMO_INDICE}"
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
