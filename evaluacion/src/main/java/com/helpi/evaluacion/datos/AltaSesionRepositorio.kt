package com.helpi.evaluacion.datos

import androidx.room.withTransaction
import com.helpi.evaluacion.consentimiento.AVISO_VERSION_ACTUAL
import com.helpi.evaluacion.consentimiento.ConsentimientoNoVigenteException
import com.helpi.evaluacion.datos.entidades.CondicionesPruebaEntity
import com.helpi.evaluacion.datos.entidades.Distancia
import com.helpi.evaluacion.datos.entidades.Entorno
import com.helpi.evaluacion.datos.entidades.EstadoSesion
import com.helpi.evaluacion.datos.entidades.Iluminacion
import com.helpi.evaluacion.datos.entidades.ManoDominante
import com.helpi.evaluacion.datos.entidades.ParticipanteEntity
import com.helpi.evaluacion.datos.entidades.PerfilParticipante
import com.helpi.evaluacion.datos.entidades.SesionEntity
import com.helpi.evaluacion.datos.entidades.SoporteCamara
import com.helpi.evaluacion.datos.entidades.TipoEntorno
import com.helpi.evaluacion.dominio.protocolo.GeneradorProtocolo
import java.security.SecureRandom
import java.util.UUID

data class CondicionesSesion(
    val entorno: Entorno,
    val tipoEntorno: TipoEntorno,
    val iluminacion: Iluminacion,
    val contraluz: Boolean,
    val distancia: Distancia,
    val manoDominante: ManoDominante,
    val guantes: Boolean,
    val soporteCamara: SoporteCamara,
    val perfilParticipante: PerfilParticipante
) {
    fun aEntidad(sesionId: String) = CondicionesPruebaEntity(
        sesionId = sesionId,
        entorno = entorno,
        tipoEntorno = tipoEntorno,
        iluminacion = iluminacion,
        contraluz = contraluz,
        distancia = distancia,
        manoDominante = manoDominante,
        guantes = guantes,
        soporteCamara = soporteCamara,
        perfilParticipante = perfilParticipante
    )
}

data class MetadatosAltaSesion(
    val appVersionName: String,
    val appVersionCode: Int,
    val modeloVersion: String,
    val modeloSha256: String,
    val catalogoSha256: String,
    val contratoKeypoints: Int,
    val fabricante: String,
    val modeloDispositivo: String,
    val sdkAndroid: Int
)

class AltaSesionRepositorio(
    private val database: HelpiEvaluacionDatabase,
    private val metadatos: MetadatosAltaSesion,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val generarSemilla: () -> Long = { SEMILLA_SEGURA.nextLong() },
    private val crearSesionId: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun crear(codigoParticipante: String, condiciones: CondicionesSesion): String {
        require(CODIGO_PARTICIPANTE.matches(codigoParticipante)) {
            "El código debe cumplir el patrón P-NNN o P-NNNN"
        }
        validarMetadatos()

        return database.withTransaction {
            val consentimiento = database.consentimientoDao().ultimo()
                ?: throw ConsentimientoNoVigenteException()
            if (!database.consentimientoDao().consentimientoActualVigente(
                    consentimiento.id,
                    AVISO_VERSION_ACTUAL
                )
            ) {
                throw ConsentimientoNoVigenteException()
            }

            val participantes = database.participanteDao()
            if (participantes.buscar(codigoParticipante) == null) {
                participantes.insertar(
                    ParticipanteEntity(codigoParticipante, reloj())
                )
            }

            val sesionId = crearSesionId()
            val sesion = SesionEntity(
                id = sesionId,
                participanteCodigo = codigoParticipante,
                consentimientoId = consentimiento.id,
                protocoloVersion = GeneradorProtocolo.VERSION,
                semilla = generarSemilla(),
                estado = EstadoSesion.CREADA,
                bloqueActual = 1,
                appVersionName = metadatos.appVersionName,
                appVersionCode = metadatos.appVersionCode,
                modeloVersion = metadatos.modeloVersion,
                modeloSha256 = metadatos.modeloSha256,
                catalogoSha256 = metadatos.catalogoSha256,
                contratoKeypoints = metadatos.contratoKeypoints,
                dispositivoFabricante = metadatos.fabricante,
                dispositivoModelo = metadatos.modeloDispositivo,
                sdkAndroid = metadatos.sdkAndroid,
                creadaEn = reloj(),
                ultimoIntentoEn = null,
                ultimaExportacionEn = null,
                intentosPerdidos = 0,
                revision = 0
            )
            database.sesionDao().insertar(sesion)
            database.sesionDao().insertarCondiciones(condiciones.aEntidad(sesionId))
            sesionId
        }
    }

    private fun validarMetadatos() {
        require(metadatos.appVersionName.isNotBlank()) { "Falta la versión de la app" }
        require(metadatos.appVersionCode > 0) { "La versión de la app debe ser positiva" }
        require(metadatos.modeloVersion.isNotBlank()) { "Falta la versión del modelo" }
        require(metadatos.modeloSha256.matches(SHA256_REGEX)) { "Hash de modelo inválido" }
        require(metadatos.catalogoSha256.matches(SHA256_REGEX)) { "Hash de catálogo inválido" }
        require(metadatos.contratoKeypoints > 0) { "Contrato de keypoints inválido" }
        require(metadatos.fabricante.isNotBlank()) { "Falta el fabricante del dispositivo" }
        require(metadatos.modeloDispositivo.isNotBlank()) { "Falta el modelo del dispositivo" }
        require(metadatos.sdkAndroid > 0) { "Versión de Android inválida" }
    }

    private companion object {
        val CODIGO_PARTICIPANTE = Regex("^P-[0-9]{3,4}$")
        val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
        val SEMILLA_SEGURA = SecureRandom()
    }
}
