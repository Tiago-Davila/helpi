package com.helpi.evaluacion.envio

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

sealed interface ResultadoEnvio {
    data class Confirmado(val motivo: String) : ResultadoEnvio

    data class Rechazado(val motivo: String) : ResultadoEnvio

    data class Bloqueado(val motivo: String) : ResultadoEnvio

    data class Transitorio(val motivo: String) : ResultadoEnvio
}

class ReceptorCliente(
    private val urlReceptor: String,
    private val claveEnvio: String,
    private val appVersionName: String,
    private val appVersionCode: Int
) {
    fun enviar(envioId: String, payload: ByteArray, sha256: String): ResultadoEnvio {
        if (payload.size > MAX_REQUEST_BYTES) return ResultadoEnvio.Rechazado("DEMASIADO_GRANDE")
        if (sha256Bytes(payload) != sha256) {
            return ResultadoEnvio.Transitorio("SHA256_LOCAL_NO_COINCIDE")
        }

        val identity = payloadIdentity(payload)
            ?: return ResultadoEnvio.Transitorio("PAYLOAD_LOCAL_INVALIDO")
        if (identity.envioId != envioId) {
            return ResultadoEnvio.Transitorio("ENVIO_ID_LOCAL_NO_COINCIDE")
        }

        val connection = openConnection()
            ?: return ResultadoEnvio.Transitorio("HTTPS_REQUERIDO")

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.useCaches = false
            connection.setFixedLengthStreamingMode(payload.size)
            connection.setRequestProperty("Content-Type", CONTENT_TYPE)
            connection.setRequestProperty("X-Helpi-Contrato", identity.contrato)
            connection.setRequestProperty("Idempotency-Key", envioId)
            connection.setRequestProperty("X-Helpi-Clave-Evaluacion", claveEnvio)
            connection.setRequestProperty(
                "User-Agent",
                "HelpiEvaluacion/$appVersionName ($appVersionCode)"
            )
            connection.outputStream.use { output -> output.write(payload) }
            val status = connection.responseCode
            classify(
                status,
                readResponse(connection, status),
                envioId,
                sha256
            )
        } catch (exception: IOException) {
            ResultadoEnvio.Transitorio(exception.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(): HttpsURLConnection? = try {
        val endpoint = "${urlReceptor.trimEnd('/')}$PATH"
        val url = URL(endpoint)
        if (url.protocol == "https") url.openConnection() as? HttpsURLConnection else null
    } catch (_: IOException) {
        null
    }

    private fun readResponse(connection: HttpsURLConnection, status: Int): JSONObject? {
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return null
        val bytes = ByteArrayOutputStream()
        stream.use { input ->
            val buffer = ByteArray(RESPONSE_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (bytes.size() + count > MAX_RESPONSE_BYTES) return null
                bytes.write(buffer, 0, count)
            }
        }
        return try {
            JSONObject(String(bytes.toByteArray(), StandardCharsets.UTF_8))
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun classify(
        status: Int,
        response: JSONObject?,
        expectedEnvioId: String,
        expectedSha256: String
    ): ResultadoEnvio {
        val motivo = response?.optString("estado")?.takeIf { it.isNotBlank() && it != "null" }
        return when (status) {
            HttpURLConnection.HTTP_OK -> {
                val confirmedState = motivo == "RECIBIDO" || motivo == "DUPLICADO"
                val matchesEnvioId = response?.optString("envioId") == expectedEnvioId
                val matchesSha256 = response?.optString("sha256") == expectedSha256
                if (confirmedState && matchesEnvioId && matchesSha256) {
                    ResultadoEnvio.Confirmado(checkNotNull(motivo))
                } else {
                    ResultadoEnvio.Transitorio(motivo ?: "CONFIRMACION_INVALIDA")
                }
            }

            HttpURLConnection.HTTP_BAD_REQUEST ->
                ResultadoEnvio.Rechazado(motivo ?: "JSON_INVALIDO")

            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_FORBIDDEN ->
                ResultadoEnvio.Bloqueado(motivo ?: "NO_AUTORIZADO")

            HttpURLConnection.HTTP_CONFLICT -> ResultadoEnvio.Rechazado(motivo ?: "CONFLICTO")
            HttpURLConnection.HTTP_ENTITY_TOO_LARGE ->
                ResultadoEnvio.Rechazado(motivo ?: "DEMASIADO_GRANDE")

            HttpURLConnection.HTTP_UNSUPPORTED_TYPE ->
                ResultadoEnvio.Rechazado(motivo ?: "TIPO_NO_SOPORTADO")

            429 -> ResultadoEnvio.Transitorio(motivo ?: "LIMITE")
            422 -> ResultadoEnvio.Rechazado(motivo ?: "HTTP_422")
            in 500..599 -> ResultadoEnvio.Transitorio(motivo ?: "ERROR_INTERNO")
            in 300..399 -> ResultadoEnvio.Transitorio(motivo ?: "HTTP_$status")
            else -> ResultadoEnvio.Transitorio(motivo ?: "HTTP_$status")
        }
    }

    private fun payloadIdentity(payload: ByteArray): PayloadIdentity? = try {
        val root = JSONObject(String(payload, StandardCharsets.UTF_8))
        val contract = root.getJSONObject("contrato")
        val envioId = root.getJSONObject("envio").getString("envioId")
        PayloadIdentity(
            contrato = "${contract.getString("nombre")}/${contract.getString("version")}",
            envioId = envioId
        )
    } catch (_: RuntimeException) {
        null
    }

    private fun sha256Bytes(payload: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(payload)
        .joinToString("") { byte -> "%02x".format(byte) }

    private data class PayloadIdentity(val contrato: String, val envioId: String)

    internal companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val MAX_REQUEST_BYTES = 1_048_576
        const val MAX_RESPONSE_BYTES = 65_536
        const val RESPONSE_BUFFER_SIZE = 4_096
        const val CONTENT_TYPE = "application/json; charset=utf-8"
        const val PATH = "/v1/sesiones-evaluacion"
    }
}
