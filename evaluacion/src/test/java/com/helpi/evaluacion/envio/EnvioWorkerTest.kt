package com.helpi.evaluacion.envio

import androidx.room.Room
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.helpi.evaluacion.consentimiento.AVISO_VERSION_ACTUAL
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EnvioWorkerTest {
    private lateinit var database: HelpiEvaluacionDatabase
    private lateinit var server: MockWebServer
    private lateinit var socketFactoryOriginal: javax.net.ssl.SSLSocketFactory
    private lateinit var hostnameVerifierOriginal: javax.net.ssl.HostnameVerifier
    private val ahora = 1_791_213_907_000L
    private var numeroParticipante = 0

    @Before
    fun abrirRecursos() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(
            context,
            HelpiEvaluacionDatabase::class.java
        ).allowMainThreadQueries().build()

        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val testCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        socketFactoryOriginal = HttpsURLConnection.getDefaultSSLSocketFactory()
        hostnameVerifierOriginal = HttpsURLConnection.getDefaultHostnameVerifier()
        HttpsURLConnection.setDefaultSSLSocketFactory(testCertificates.sslSocketFactory())
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            start()
        }
    }

    @After
    fun cerrarRecursos() {
        server.shutdown()
        HttpsURLConnection.setDefaultSSLSocketFactory(socketFactoryOriginal)
        HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifierOriginal)
        database.close()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun solicitudExigeRedYUsaRetrocesoExponencialDeUnMinuto() {
        val solicitud = EnvioWorker.solicitud()

        assertEquals(NetworkType.CONNECTED, solicitud.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, solicitud.workSpec.backoffPolicy)
        assertEquals(TimeUnit.MINUTES.toMillis(1), solicitud.workSpec.backoffDelayDuration)
    }

    @Test
    fun sinConexionElTrabajoQuedaPendienteYSinEnviar() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build()
        )
        val workManager = WorkManager.getInstance(context)
        val solicitud = EnvioWorker.solicitud()

        workManager.enqueueUniqueWork(
            EnvioRepositorio.NOMBRE_TRABAJO,
            androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
            solicitud
        )

        val informacion = requireNotNull(
            workManager.getWorkInfoById(solicitud.id).get(5, TimeUnit.SECONDS)
        )
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, informacion.state)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun respuestaTransitoriaConservaElEnvioYSolicitaReintento() = runBlocking {
        val envio = guardarEnvio()
        server.enqueue(
            MockResponse().setResponseCode(500).setBody("{\"estado\":\"ERROR_INTERNO\"}")
        )

        val resultado = procesador().procesar()

        assertEquals(EnvioColaProcesador.Decision.REINTENTAR, resultado)
        assertEquals(EstadoEnvio.PENDIENTE, database.envioDao().buscar(envio.envioId)?.estado)
        assertNull(database.sesionDao().buscar(envio.sesionId)?.ultimaExportacionEn)
    }

    @Test
    fun confirmacionBorraElEnvioYMarcaLaExportacion() = runBlocking {
        val envio = guardarEnvio()
        server.enqueue(MockResponse().setBody(cuerpoRespuesta("RECIBIDO", envio)))

        val resultado = procesador().procesar()

        assertEquals(EnvioColaProcesador.Decision.COMPLETADO, resultado)
        assertNull(database.envioDao().buscar(envio.envioId))
        assertEquals(ahora, database.sesionDao().buscar(envio.sesionId)?.ultimaExportacionEn)
    }

    @Test
    fun sha256DistintoEnLaConfirmacionConservaElEnvioYReintenta() = runBlocking {
        val envio = guardarEnvio()
        server.enqueue(
            MockResponse().setBody(
                """{"estado":"RECIBIDO","envioId":"${envio.envioId}","sha256":"${"0".repeat(
                    64
                )}"}"""
            )
        )

        val resultado = procesador().procesar()

        assertEquals(EnvioColaProcesador.Decision.REINTENTAR, resultado)
        assertEquals(EstadoEnvio.PENDIENTE, database.envioDao().buscar(envio.envioId)?.estado)
        assertNull(database.sesionDao().buscar(envio.sesionId)?.ultimaExportacionEn)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun rechazoPermanenteGuardaEstadoYMotivoSinReintentar() = runBlocking {
        val envio = guardarEnvio()
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("{\"estado\":\"ESQUEMA_INVALIDO\"}")
        )

        val resultado = procesador().procesar()
        val guardado = database.envioDao().buscar(envio.envioId)

        assertEquals(EnvioColaProcesador.Decision.COMPLETADO, resultado)
        assertEquals(EstadoEnvio.RECHAZADO, guardado?.estado)
        assertEquals("ESQUEMA_INVALIDO", guardado?.motivo)
    }

    @Test
    fun credencialInvalidaBloqueaElEnvioSinReintentar() = runBlocking {
        val envio = guardarEnvio()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"estado\":\"NO_AUTORIZADO\"}")
        )

        val resultado = procesador().procesar()
        val guardado = database.envioDao().buscar(envio.envioId)

        assertEquals(EnvioColaProcesador.Decision.COMPLETADO, resultado)
        assertEquals(EstadoEnvio.BLOQUEADO, guardado?.estado)
        assertEquals("NO_AUTORIZADO", guardado?.motivo)
    }

    @Test
    fun procesaLosEnviosPendientesDelMasAntiguoAlMasNuevo() = runBlocking {
        val primero = guardarEnvio(ENVIO_ID, creadoEn = ahora - 100)
        val segundo = guardarEnvio(ENVIO_ID_SECUNDARIO, creadoEn = ahora - 50)
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(422)
                    .setBody("{\"estado\":\"ESQUEMA_INVALIDO\"}")
            )
        }

        procesador().procesar()

        val envioPrimero = server.takeRequest(1, TimeUnit.SECONDS)?.body?.readUtf8()
        val envioSegundo = server.takeRequest(1, TimeUnit.SECONDS)?.body?.readUtf8()
        assertTrue(requireNotNull(envioPrimero).contains(primero.envioId))
        assertTrue(requireNotNull(envioSegundo).contains(segundo.envioId))
    }

    private suspend fun guardarEnvio(
        envioId: String = ENVIO_ID,
        sha256: String = sha256(payloadDePrueba(envioId)),
        creadoEn: Long = ahora
    ): EnvioEntity {
        val consentimientoId = database.consentimientoDao().insertar(
            ConsentimientoEntity(
                avisoVersion = AVISO_VERSION_ACTUAL,
                avisoVideoSha256 = "a".repeat(64),
                otorgadoEn = ahora - 1_000,
                revocadoEn = null
            )
        )
        val sessionId = UUID.randomUUID().toString()
        numeroParticipante += 1
        val participante = "P-${numeroParticipante.toString().padStart(3, '0')}"
        database.participanteDao().insertar(ParticipanteEntity(participante, ahora - 2_000))
        insertarSesion(sessionId, participante, consentimientoId)
        return EnvioEntity(
            envioId = envioId,
            sesionId = sessionId,
            revision = 1,
            payload = payloadDePrueba(envioId),
            sha256 = sha256,
            estado = EstadoEnvio.PENDIENTE,
            motivo = null,
            intentos = 0,
            creadoEn = creadoEn,
            ultimoIntentoEn = null
        ).also { database.envioDao().insertar(it) }
    }

    private suspend fun insertarSesion(
        sessionId: String,
        participante: String,
        consentimientoId: Long
    ) {
        database.sesionDao().crearConCondiciones(
            SesionEntity(
                id = sessionId,
                participanteCodigo = participante,
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

    private fun procesador() = EnvioColaProcesador(
        database = database,
        cliente = ReceptorCliente(
            urlReceptor = server.url("/").toString(),
            claveEnvio = "clave-test",
            appVersionName = "0.1.0-evaluacion",
            appVersionCode = 7
        ),
        reloj = { ahora }
    )

    private fun cuerpoRespuesta(estado: String, envio: EnvioEntity): String =
        """{"estado":"$estado","envioId":"${envio.envioId}","sha256":"${envio.sha256}"}"""

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val ENVIO_ID = "3f2b8c1e-9a47-4d2e-8b61-0c5e7a9d4f12"
        const val ENVIO_ID_SECUNDARIO = "a7c4e2d9-5b13-4f8a-9e26-1d0b3c8f7a45"
        fun payloadDePrueba(envioId: String) = (
            "{\"contrato\":{\"nombre\":\"helpi.evaluacion.sesion\",\"version\":\"1.0.0\"}," +
                "\"envio\":{\"envioId\":\"$envioId\"}}"
            ).toByteArray(StandardCharsets.UTF_8)
    }
}
