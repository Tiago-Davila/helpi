package com.helpi.evaluacion.envio

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReceptorClienteTest {
    private lateinit var server: MockWebServer
    private lateinit var defaultSocketFactory: javax.net.ssl.SSLSocketFactory
    private lateinit var defaultHostnameVerifier: javax.net.ssl.HostnameVerifier

    @Before
    fun setUp() {
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
        defaultSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        defaultHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        HttpsURLConnection.setDefaultSSLSocketFactory(testCertificates.sslSocketFactory())

        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        HttpsURLConnection.setDefaultSSLSocketFactory(defaultSocketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier(defaultHostnameVerifier)
    }

    @Test
    fun clasificaCadaFilaDeLaTablaDelContrato() {
        val cases = listOf(
            ResponseCase(200, "RECIBIDO", ResultadoEsperado.CONFIRMADO),
            ResponseCase(200, "DUPLICADO", ResultadoEsperado.CONFIRMADO),
            ResponseCase(400, "JSON_INVALIDO", ResultadoEsperado.RECHAZADO),
            ResponseCase(401, "NO_AUTORIZADO", ResultadoEsperado.BLOQUEADO),
            ResponseCase(403, "NO_AUTORIZADO", ResultadoEsperado.BLOQUEADO),
            ResponseCase(409, "CONFLICTO", ResultadoEsperado.RECHAZADO),
            ResponseCase(413, "DEMASIADO_GRANDE", ResultadoEsperado.RECHAZADO),
            ResponseCase(415, "TIPO_NO_SOPORTADO", ResultadoEsperado.RECHAZADO),
            ResponseCase(422, "ESQUEMA_INVALIDO", ResultadoEsperado.RECHAZADO),
            ResponseCase(422, "SEMANTICA_INVALIDA", ResultadoEsperado.RECHAZADO),
            ResponseCase(422, "VERSION_NO_SOPORTADA", ResultadoEsperado.RECHAZADO),
            ResponseCase(429, "LIMITE", ResultadoEsperado.TRANSITORIO),
            ResponseCase(500, "ERROR_INTERNO", ResultadoEsperado.TRANSITORIO)
        )

        cases.forEach { case ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(case.status)
                    .setBody(responseBody(case.estado))
            )

            val result = client().enviar(ENVIO_ID, PAYLOAD, payloadSha256())

            assertEquals(case.estado, case.motivo(result))
            assertEquals(case.resultado, result.tipoEsperado())
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        }
    }

    @Test
    fun confirmacionConSha256DistintoEsTransitoria() {
        server.enqueue(
            MockResponse().setBody(responseBody("RECIBIDO", sha256 = "0".repeat(64)))
        )

        val result = client().enviar(ENVIO_ID, PAYLOAD, payloadSha256())

        assertTrue(result is ResultadoEnvio.Transitorio)
    }

    @Test
    fun confirmacionConEnvioIdDistintoEsTransitoria() {
        server.enqueue(
            MockResponse().setBody(responseBody("RECIBIDO", envioId = OTHER_ENVIO_ID))
        )

        val result = client().enviar(ENVIO_ID, PAYLOAD, payloadSha256())

        assertTrue(result is ResultadoEnvio.Transitorio)
    }

    @Test
    fun encabezadosDeIdempotenciaYContratoCoincidenConElCuerpo() {
        server.enqueue(MockResponse().setBody(responseBody("RECIBIDO")))

        val result = client().enviar(ENVIO_ID, PAYLOAD, payloadSha256())
        val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))

        assertTrue(result is ResultadoEnvio.Confirmado)
        assertEquals("POST", request.method)
        assertEquals("/v1/sesiones-evaluacion", request.path)
        assertEquals("helpi.evaluacion.sesion/1.0.0", request.getHeader("X-Helpi-Contrato"))
        assertEquals(ENVIO_ID, request.getHeader("Idempotency-Key"))
        assertEquals("clave-test", request.getHeader("X-Helpi-Clave-Evaluacion"))
        assertEquals("HelpiEvaluacion/0.1.0-evaluacion (7)", request.getHeader("User-Agent"))
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertEquals(PAYLOAD.toString(StandardCharsets.UTF_8), request.body.readUtf8())
    }

    @Test
    fun redireccionNoSeSigueYEsTransitoria() {
        server.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_MOVED_TEMP)
                .addHeader("Location", server.url("/redirigido"))
        )

        val result = client().enviar(ENVIO_ID, PAYLOAD, payloadSha256())

        assertEquals(ResultadoEnvio.Transitorio("HTTP_302"), result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun fallaTlsEsTransitoria() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE))

        val result = client().enviar(ENVIO_ID, PAYLOAD, payloadSha256())

        assertTrue(result is ResultadoEnvio.Transitorio)
    }

    @Test
    fun conexionCerradaEsTransitoria() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = client().enviar(ENVIO_ID, PAYLOAD, payloadSha256())

        assertTrue(result is ResultadoEnvio.Transitorio)
    }

    @Test
    fun payloadDemasiadoGrandeSeRechazaSinEnviar() {
        val payloadGrande = ByteArray(1_048_577)

        val result = client().enviar(ENVIO_ID, payloadGrande, sha256(payloadGrande))

        assertEquals(ResultadoEnvio.Rechazado("DEMASIADO_GRANDE"), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun respetalosTimeoutsDelContrato() {
        assertEquals(15_000, ReceptorCliente.CONNECT_TIMEOUT_MILLIS)
        assertEquals(30_000, ReceptorCliente.READ_TIMEOUT_MILLIS)
    }

    private fun client(): ReceptorCliente = ReceptorCliente(
        urlReceptor = server.url("/").toString(),
        claveEnvio = "clave-test",
        appVersionName = "0.1.0-evaluacion",
        appVersionCode = 7
    )

    private fun responseBody(
        estado: String,
        envioId: String = ENVIO_ID,
        sha256: String = payloadSha256()
    ): String = """{"estado":"$estado","envioId":"$envioId","sha256":"$sha256"}"""

    private fun payloadSha256(): String = sha256(PAYLOAD)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun assertNotNull(value: Any?) {
        assertTrue("El servidor no recibió el envío", value != null)
    }

    private fun ResultadoEnvio.tipoEsperado(): ResultadoEsperado = when (this) {
        is ResultadoEnvio.Confirmado -> ResultadoEsperado.CONFIRMADO
        is ResultadoEnvio.Rechazado -> ResultadoEsperado.RECHAZADO
        is ResultadoEnvio.Bloqueado -> ResultadoEsperado.BLOQUEADO
        is ResultadoEnvio.Transitorio -> ResultadoEsperado.TRANSITORIO
    }

    private fun ResponseCase.motivo(result: ResultadoEnvio): String = when (result) {
        is ResultadoEnvio.Confirmado -> result.motivo
        is ResultadoEnvio.Rechazado -> result.motivo
        is ResultadoEnvio.Bloqueado -> result.motivo
        is ResultadoEnvio.Transitorio -> result.motivo
    }

    private data class ResponseCase(
        val status: Int,
        val estado: String,
        val resultado: ResultadoEsperado
    )

    private enum class ResultadoEsperado {
        CONFIRMADO,
        RECHAZADO,
        BLOQUEADO,
        TRANSITORIO
    }

    private companion object {
        const val ENVIO_ID = "3f2b8c1e-9a47-4d2e-8b61-0c5e7a9d4f12"
        const val OTHER_ENVIO_ID = "a7c4e2d9-5b13-4f8a-9e26-1d0b3c8f7a45"
        val PAYLOAD = (
            "{\"contrato\":{\"nombre\":\"helpi.evaluacion.sesion\",\"version\":\"1.0.0\"}," +
                "\"envio\":{\"envioId\":\"$ENVIO_ID\"}}"
            ).toByteArray(StandardCharsets.UTF_8)
    }
}
