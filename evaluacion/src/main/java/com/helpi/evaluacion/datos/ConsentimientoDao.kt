package com.helpi.evaluacion.datos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity

@Dao
interface ConsentimientoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(consentimiento: ConsentimientoEntity): Long

    @Query("SELECT * FROM consentimiento WHERE id = :id")
    suspend fun buscar(id: Long): ConsentimientoEntity?

    @Query("SELECT * FROM consentimiento ORDER BY id")
    suspend fun listar(): List<ConsentimientoEntity>

    @Query("SELECT * FROM consentimiento ORDER BY id DESC LIMIT 1")
    suspend fun ultimo(): ConsentimientoEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM consentimiento
            WHERE id = (SELECT MAX(id) FROM consentimiento)
                AND revocadoEn IS NULL
                AND avisoVersion = :avisoVersion
                AND avisoVideoSha256 = :avisoVideoSha256
        )
        """
    )
    suspend fun tieneConsentimientoVigente(avisoVersion: String, avisoVideoSha256: String): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM consentimiento
            WHERE id = :consentimientoId
                AND id = (SELECT MAX(id) FROM consentimiento)
                AND revocadoEn IS NULL
                AND avisoVersion = :avisoVersion
        )
        """
    )
    suspend fun consentimientoActualVigente(consentimientoId: Long, avisoVersion: String): Boolean

    @Query(
        "UPDATE consentimiento SET revocadoEn = :revocadoEn WHERE id = :id AND revocadoEn IS NULL"
    )
    suspend fun revocar(id: Long, revocadoEn: Long): Int
}
