package com.helpi.evaluacion.envio

import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.helpi.evaluacion.consentimiento.AVISO_VERSION_ACTUAL
import com.helpi.evaluacion.consentimiento.ConsentimientoNoVigenteException
import com.helpi.evaluacion.contrato.SesionContratoSerializer
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.SesionDocumentoMapper
import com.helpi.evaluacion.datos.entidades.EnvioEntity
import com.helpi.evaluacion.datos.entidades.EstadoEnvio
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class EnvioRepositorio(
    private val database: HelpiEvaluacionDatabase,
    private val workManager: WorkManager,
    private val crearTrabajo: () -> OneTimeWorkRequest,
    private val configuracion: EnvioRepositorioConfiguracion = EnvioRepositorioConfiguracion()
) {
    private val mapper = configuracion.mapper ?: SesionDocumentoMapper(database)
    private val serializer = configuracion.serializer
    private val crearEnvioId = configuracion.crearEnvioId
    private val reloj = configuracion.reloj

    suspend fun encolar(sesionId: String): EnvioEntity = database.withTransaction {
        val sesionDao = database.sesionDao()
        val sesion = checkNotNull(sesionDao.buscar(sesionId)) {
            "No existe la sesión $sesionId"
        }
        if (database.envioDao().cantidad(EstadoEnvio.PENDIENTE) >= MAXIMO_PENDIENTES) {
            throw ColaEnviosLlenaException()
        }
        if (!database.consentimientoDao().consentimientoActualVigente(
                sesion.consentimientoId,
                AVISO_VERSION_ACTUAL
            )
        ) {
            throw ConsentimientoNoVigenteException()
        }

        val generadoEn = reloj()
        val envioId = crearEnvioId()
        check(envioId.version() == UUID_VERSION_4) { "envioId debe ser un UUID v4" }
        val documento = mapper.mapear(sesionId, envioId, Instant.ofEpochMilli(generadoEn))
        val payload = serializer.serialize(documento)
        val envio = EnvioEntity(
            envioId = envioId.toString(),
            sesionId = sesionId,
            revision = documento.envio.revision,
            payload = payload,
            sha256 = payload.sha256(),
            estado = EstadoEnvio.PENDIENTE,
            motivo = null,
            intentos = 0,
            creadoEn = generadoEn,
            ultimoIntentoEn = null
        )

        check(sesionDao.actualizarRevisionDeEnvio(sesionId, envio.revision) == 1) {
            "No se pudo incrementar la revisión de la sesión $sesionId"
        }
        database.envioDao().insertar(envio)
        workManager.enqueueUniqueWork(
            NOMBRE_TRABAJO,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            crearTrabajo()
        )
        envio
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance(SHA256).digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val NOMBRE_TRABAJO = "envio-sesiones-evaluacion"
        private const val MAXIMO_PENDIENTES = 20
        private const val UUID_VERSION_4 = 4
        private const val SHA256 = "SHA-256"
    }
}

class ColaEnviosLlenaException :
    IllegalStateException(
        "Se alcanzó el máximo de 20 envíos pendientes en este dispositivo"
    )

data class EnvioRepositorioConfiguracion(
    val mapper: SesionDocumentoMapper? = null,
    val serializer: SesionContratoSerializer = SesionContratoSerializer(),
    val crearEnvioId: () -> UUID = { UUID.randomUUID() },
    val reloj: () -> Long = System::currentTimeMillis
)
