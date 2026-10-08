package com.helpi.evaluacion.datos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.helpi.evaluacion.datos.entidades.IntentoEntity
import com.helpi.evaluacion.datos.entidades.PrediccionTopEntity
import com.helpi.evaluacion.datos.entidades.ResultadoIntento

data class PrediccionTopDato(
    val rango: Int,
    val indice: Int,
    val glosa: String,
    val confianza: Float
)

@Dao
abstract class IntentoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertar(intento: IntentoEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertarPredicciones(predicciones: List<PrediccionTopEntity>)

    @Query(
        """
        SELECT * FROM intento
        WHERE sesionId = :sesionId AND posicion = :posicion
        ORDER BY numeroIntento DESC LIMIT 1
        """
    )
    abstract suspend fun ultimoDePosicion(sesionId: String, posicion: Int): IntentoEntity?

    @Query(
        "UPDATE intento SET reemplazado = 1 WHERE id = :intentoId AND reemplazado = 0"
    )
    abstract suspend fun marcarReemplazado(intentoId: Long): Int

    @Query(
        """
        UPDATE sesion
        SET ultimoIntentoEn = :momento,
            intentosPerdidos = intentosPerdidos + :intentosPerdidos
        WHERE id = :sesionId
        """
    )
    abstract suspend fun actualizarUltimoIntento(
        sesionId: String,
        momento: Long,
        intentosPerdidos: Int
    ): Int

    @Query("SELECT * FROM intento WHERE sesionId = :sesionId ORDER BY posicion, numeroIntento")
    abstract suspend fun listarDeSesion(sesionId: String): List<IntentoEntity>

    @Query("SELECT * FROM prediccion_top WHERE intentoId = :intentoId ORDER BY rango")
    abstract suspend fun predicciones(intentoId: Long): List<PrediccionTopEntity>

    @Query("SELECT * FROM intento WHERE id = :intentoId")
    abstract suspend fun buscar(intentoId: Long): IntentoEntity?

    @Query("SELECT COUNT(*) FROM intento WHERE sesionId = :sesionId")
    abstract suspend fun cantidadDeSesion(sesionId: String): Int

    @Query("SELECT COUNT(*) FROM prediccion_top WHERE intentoId = :intentoId")
    abstract suspend fun cantidadPredicciones(intentoId: Long): Int

    @Query("SELECT COUNT(*) FROM intento")
    abstract suspend fun cantidadTotal(): Int

    @Query(
        """
        UPDATE intento SET loHiceMal = 1
        WHERE sesionId = :sesionId AND posicion = :posicion
            AND numeroIntento = :numeroIntento AND reemplazado = 0
        """
    )
    abstract suspend fun marcarLoHiceMal(sesionId: String, posicion: Int, numeroIntento: Int): Int

    @Transaction
    open suspend fun registrarIntento(
        intento: IntentoEntity,
        top3: List<PrediccionTopDato>,
        momento: Long,
        intentosPerdidos: Int = 0
    ): Long {
        require(intentosPerdidos >= 0) { "intentosPerdidos no puede ser negativo" }
        validarTop3(intento, top3)

        val anterior = ultimoDePosicion(intento.sesionId, intento.posicion)
        if (anterior == null) {
            require(intento.numeroIntento == 1) { "El primer intento de una posición debe ser 1" }
        } else {
            require(!anterior.reemplazado) { "El intento más reciente ya está reemplazado" }
            require(anterior.resultado == ResultadoIntento.SIN_RESULTADO) {
                "Solo se puede repetir un intento sin resultado"
            }
            require(intento.numeroIntento == anterior.numeroIntento + 1) {
                "numeroIntento debe aumentar exactamente en uno"
            }
            check(marcarReemplazado(anterior.id) == 1) {
                "No se pudo reemplazar el intento anterior"
            }
        }

        val intentoId = insertar(intento)
        if (top3.isNotEmpty()) {
            insertarPredicciones(
                top3.sortedBy(PrediccionTopDato::rango).map { prediccion ->
                    PrediccionTopEntity(
                        intentoId = intentoId,
                        rango = prediccion.rango,
                        indice = prediccion.indice,
                        glosa = prediccion.glosa,
                        confianza = prediccion.confianza
                    )
                }
            )
        }
        check(actualizarUltimoIntento(intento.sesionId, momento, intentosPerdidos) == 1) {
            "La sesión no existe para registrar su último intento"
        }
        return intentoId
    }

    private fun validarTop3(intento: IntentoEntity, top3: List<PrediccionTopDato>) {
        if (intento.resultado == ResultadoIntento.SIN_RESULTADO) {
            require(top3.isEmpty()) { "SIN_RESULTADO no admite predicciones top-3" }
            return
        }

        require(top3.size == 3) { "Un intento clasificado requiere exactamente tres predicciones" }
        val ordenadas = top3.sortedBy(PrediccionTopDato::rango)
        require(ordenadas.map(PrediccionTopDato::rango) == listOf(1, 2, 3)) {
            "Los rangos top-3 deben ser 1, 2 y 3"
        }
        val primera = ordenadas.first()
        require(
            intento.predichoIndice == primera.indice &&
                intento.predichoGlosa == primera.glosa &&
                intento.confianza == primera.confianza
        ) {
            "La predicción guardada debe coincidir con el rango 1"
        }
        require(
            ordenadas.zipWithNext().all { (actual, siguiente) ->
                actual.confianza >=
                    siguiente.confianza
            }
        ) {
            "Las confianzas top-3 no pueden crecer con el rango"
        }
    }
}
