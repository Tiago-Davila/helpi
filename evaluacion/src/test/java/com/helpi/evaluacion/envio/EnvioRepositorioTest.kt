package com.helpi.evaluacion.envio

import androidx.room.Room
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.helpi.evaluacion.consentimiento.AVISO_VERSION_ACTUAL
import com.helpi.evaluacion.consentimiento.ConsentimientoNoVigenteException
import com.helpi.evaluacion.contrato.SesionContratoSerializer
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.SesionDocumentoMapper
import com.helpi.evaluacion.datos.entidades.CondicionesPruebaEntity
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity
import com.helpi.evaluacion.datos.entidades.Distancia
import com.helpi.evaluacion.datos.entidades.Entorno
import com.helpi.evaluacion.datos.entidades.EnvioEntity
import com.helpi.evaluacion.datos.entidades.EstadoEnvio
import com.helpi.evaluacion.datos.entidades.EstadoSesion
import com.helpi.evaluacion.datos.entidades.Iluminacion
import com.helpi.evaluacion.datos.entidades.ManoDominante
import com.helpi.evaluacion.datos.entidades.ParticipanteEntity
import com.helpi.evaluacion.datos.entidades.PerfilParticipante
import com.helpi.evaluacion.datos.entidades.SesionEntity
import com.helpi.evaluacion.datos.entidades.SoporteCamara
import com.helpi.evaluacion.datos.entidades.TipoEntorno
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnvioRepositorioTest {
    private lateinit var database: HelpiEvaluacionDatabase
    private lateinit var workManager: WorkManager
    private val sessionId = "a7c4e2d9-5b13-4f8a-9e26-1d0b3c8f7a45"
    private val envioId = UUID.fromString("3f2b8c1e-9a47-4d2e-8b61-0c5e7a9d4f12")
    private val ahora = 1_791_213_907_000L

    @Before
    fun abrirBase() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(
            context,
            HelpiEvaluacionDatabase::class.java
        ).allowMainThreadQueries().build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun cerrarBase() {
        database.close()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun persisteLosBytesExactosQueSerializaYEncolaElTrabajoUnico() = runBlocking {
        crearSesion()
        val mapper = SesionDocumentoMapper(database)
        val serializer = SesionContratoSerializer()
        val payloadEsperado = serializer.serialize(
            mapper.mapear(sessionId, envioId, Instant.ofEpochMilli(ahora))
        )

        val guardado = repositorio(mapper, serializer).encolar(sessionId)

        assertEquals(envioId.toString(), guardado.envioId)
        assertEquals(1, guardado.revision)
        assertEquals(ahora, guardado.creadoEn)
        assertArrayEquals(payloadEsperado, guardado.payload)
        assertEquals(payloadEsperado.sha256(), guardado.sha256)
        assertEquals(EstadoEnvio.PENDIENTE, guardado.estado)
        assertEquals(1, requireNotNull(database.sesionDao().buscar(sessionId)).revision)
        assertEquals(
            1,
            workManager.getWorkInfosForUniqueWork(EnvioRepositorio.NOMBRE_TRABAJO)
                .get(5, TimeUnit.SECONDS).size
        )
    }

    @Test
    fun rechazaElEnvioNumeroVeintiunoSinDescartarLosPendientesExistentes() = runBlocking {
        crearSesion()
        repeat(20) { indice ->
            database.envioDao().insertar(
                EnvioEntity(
                    envioId = UUID.randomUUID().toString(),
                    sesionId = sessionId,
                    revision = indice + 1,
                    payload = byteArrayOf(indice.toByte()),
                    sha256 = "a".repeat(64),
                    estado = EstadoEnvio.PENDIENTE,
                    motivo = null,
                    intentos = 0,
                    creadoEn = ahora,
                    ultimoIntentoEn = null
                )
            )
        }

        assertThrows(ColaEnviosLlenaException::class.java) {
            runBlocking { repositorio().encolar(sessionId) }
        }

        assertEquals(20, database.envioDao().cantidad(EstadoEnvio.PENDIENTE))
        assertEquals(0, requireNotNull(database.sesionDao().buscar(sessionId)).revision)
    }

    @Test
    fun rechazaElEnvioSinConsentimientoVigente() = runBlocking {
        crearSesion(revocadoEn = ahora - 1)

        assertThrows(ConsentimientoNoVigenteException::class.java) {
            runBlocking { repositorio().encolar(sessionId) }
        }

        assertEquals(0, database.envioDao().cantidad(EstadoEnvio.PENDIENTE))
        assertEquals(0, requireNotNull(database.sesionDao().buscar(sessionId)).revision)
    }

    private fun repositorio(
        mapper: SesionDocumentoMapper = SesionDocumentoMapper(database),
        serializer: SesionContratoSerializer = SesionContratoSerializer()
    ) = EnvioRepositorio(
        database = database,
        workManager = workManager,
        crearTrabajo = ::solicitudDePrueba,
        configuracion = EnvioRepositorioConfiguracion(
            mapper = mapper,
            serializer = serializer,
            crearEnvioId = { envioId },
            reloj = { ahora }
        )
    )

    private suspend fun crearSesion(revocadoEn: Long? = null) {
        val consentimientoId = database.consentimientoDao().insertar(
            ConsentimientoEntity(
                avisoVersion = AVISO_VERSION_ACTUAL,
                avisoVideoSha256 = "a".repeat(64),
                otorgadoEn = ahora - 1_000,
                revocadoEn = revocadoEn
            )
        )
        database.participanteDao().insertar(ParticipanteEntity("P-007", ahora - 2_000))
        database.sesionDao().crearConCondiciones(
            SesionEntity(
                id = sessionId,
                participanteCodigo = "P-007",
                consentimientoId = consentimientoId,
                protocoloVersion = "1.0.0",
                semilla = 20261005L,
                estado = EstadoSesion.INTERRUMPIDA,
                bloqueActual = 1,
                appVersionName = "0.1.0",
                appVersionCode = 1,
                modeloVersion = "eva-lsa64-v3",
                modeloSha256 = "e231d3962e2eb8d081e9a95d61d6600a6b489db06db03f971530bc7da8f0d488",
                catalogoSha256 = "377a02e97bd57b0133962dbe961c853ca3a205d0f985eceb31b358ea323df412",
                contratoKeypoints = 3,
                dispositivoFabricante = "motorola",
                dispositivoModelo = "moto g54 5G",
                sdkAndroid = 34,
                creadaEn = ahora - 10_000,
                ultimoIntentoEn = null,
                ultimaExportacionEn = null,
                intentosPerdidos = 0,
                revision = 0
            ),
            CondicionesPruebaEntity(
                sesionId = sessionId,
                entorno = Entorno.INTERIOR,
                tipoEntorno = TipoEntorno.AULA,
                iluminacion = Iluminacion.MEDIA,
                contraluz = false,
                distancia = Distancia.ENTRE_1_Y_2M,
                manoDominante = ManoDominante.DIESTRA,
                guantes = false,
                soporteCamara = SoporteCamara.TRIPODE,
                perfilParticipante = PerfilParticipante.SORDA_SENANTE
            )
        )
    }

    private fun solicitudDePrueba() =
        OneTimeWorkRequest.Builder(EnvioRepositorioTestWorker::class.java).build()

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private class EnvioRepositorioTestWorker(
        context: android.content.Context,
        parameters: WorkerParameters
    ) : Worker(context, parameters) {
        override fun doWork(): Result = Result.success()
    }
}
