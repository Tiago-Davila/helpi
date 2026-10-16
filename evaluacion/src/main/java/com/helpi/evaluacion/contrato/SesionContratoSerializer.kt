package com.helpi.evaluacion.contrato

import android.util.JsonWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class SesionContratoSerializer {
    fun serialize(documento: SesionDocumento): ByteArray {
        val output = ByteArrayOutputStream()
        JsonWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
            writer.beginObject()
            writer.name("contrato").beginObject()
            writer.name("nombre").value(CONTRATO_NOMBRE)
            writer.name("version").value(CONTRATO_VERSION)
            writer.endObject()

            writer.name("envio").beginObject()
            writer.name("envioId").value(documento.envio.envioId.toString())
            writer.name("revision").value(documento.envio.revision)
            writer.writeInstant("generadoEn", documento.envio.generadoEn)
            writer.endObject()

            writer.name("sesion").beginObject()
            writer.name("sesionId").value(documento.sesion.sesionId.toString())
            writer.name("estado").value(documento.sesion.estado.name)
            writer.writeInstant("creadaEn", documento.sesion.creadaEn)
            writer.writeInstantOrNull("ultimoIntentoEn", documento.sesion.ultimoIntentoEn)
            writer.name("intentosPerdidos").value(documento.sesion.intentosPerdidos)
            writer.endObject()

            writer.name("participante").beginObject()
            writer.name("codigo").value(documento.participante.codigo)
            writer.endObject()

            writer.name("consentimiento").beginObject()
            writer.name("avisoVersion").value(documento.consentimiento.avisoVersion)
            writer.writeInstant("otorgadoEn", documento.consentimiento.otorgadoEn)
            writer.endObject()

            writer.name("condiciones").beginObject()
            writer.name("entorno").value(documento.condiciones.entorno)
            writer.name("tipoEntorno").value(documento.condiciones.tipoEntorno)
            writer.name("iluminacion").value(documento.condiciones.iluminacion)
            writer.name("contraluz").value(documento.condiciones.contraluz)
            writer.name("distancia").value(documento.condiciones.distancia)
            writer.name("manoDominante").value(documento.condiciones.manoDominante)
            writer.name("guantes").value(documento.condiciones.guantes)
            writer.name("soporteCamara").value(documento.condiciones.soporteCamara)
            writer.name("perfilParticipante").value(documento.condiciones.perfilParticipante)
            writer.endObject()

            writer.name("versiones").beginObject()
            writer.name("appVersionName").value(documento.versiones.appVersionName)
            writer.name("appVersionCode").value(documento.versiones.appVersionCode)
            writer.name("modelo").value(documento.versiones.modelo)
            writer.name("modeloSha256").value(documento.versiones.modeloSha256)
            writer.name("catalogoSha256").value(documento.versiones.catalogoSha256)
            writer.name("contratoKeypoints").value(documento.versiones.contratoKeypoints)
            writer.name("protocolo").value(documento.versiones.protocolo)
            writer.endObject()

            writer.name("dispositivo").beginObject()
            writer.name("fabricante").value(documento.dispositivo.fabricante)
            writer.name("modelo").value(documento.dispositivo.modelo)
            writer.name("sdkAndroid").value(documento.dispositivo.sdkAndroid)
            writer.endObject()

            writeProtocol(writer, documento.protocolo)
            writeAttempts(writer, documento.intentos)
            writeInterruptions(writer, documento.interrupciones)
            writer.endObject()
        }
        return output.toByteArray()
    }

    private fun writeProtocol(writer: JsonWriter, protocolo: ProtocoloDocumento) {
        writer.name("protocolo").beginObject()
        writer.name("version").value(protocolo.version)
        writer.name("semilla").value(protocolo.semilla.toString())
        writer.name("senas").value(protocolo.senas)
        writer.name("repeticiones").value(protocolo.repeticiones)
        writer.name("bloques").value(protocolo.bloques)
        writer.name("intentosPorBloque").value(protocolo.intentosPorBloque)
        writer.name("secuencia").beginArray()
        protocolo.secuencia.forEach { indice -> writer.value(indice) }
        writer.endArray()
        writer.endObject()
    }

    private fun writeAttempts(writer: JsonWriter, intentos: List<IntentoDocumento>) {
        writer.name("intentos").beginArray()
        intentos.forEach { intento ->
            writer.beginObject()
            writer.name("posicion").value(intento.posicion)
            writer.name("bloque").value(intento.bloque)
            writer.name("numeroIntento").value(intento.numeroIntento)
            writer.name("reemplazado").value(intento.reemplazado)
            writer.name("senaEsperada").beginObject()
            writer.name("indice").value(intento.senaEsperada.indice)
            writer.name("glosa").value(intento.senaEsperada.glosa)
            writer.endObject()
            writer.name("vioVideo").value(intento.vioVideo)
            writer.name("resultado").value(intento.resultado.name)
            writer.name("causaSinResultado")
            val causa = intento.causaSinResultado?.name
            if (causa == null) {
                writer.nullValue()
            } else {
                writer.value(causa)
            }
            writePrediction(writer, intento.prediccion)
            writer.name("top3").beginArray()
            intento.top3.forEach { prediction ->
                writer.beginObject()
                writer.name("rango").value(prediction.rango)
                writer.name("indice").value(prediction.indice)
                writer.name("glosa").value(prediction.glosa)
                writer.writeProbability("confianza", prediction.confianza)
                writer.endObject()
            }
            writer.endArray()
            writer.writeProbability("umbral", intento.umbral)
            writer.name("superoUmbral").value(intento.superoUmbral)
            writer.name("correcto").value(intento.correcto)
            writer.name("descartado").value(intento.descartado)
            writer.name("loHiceMal").value(intento.loHiceMal)
            writer.name("inicioRelMs").value(intento.inicioRelMs)
            writer.name("duracionSegmentoMs")
            val duracion = intento.duracionSegmentoMs
            if (duracion == null) {
                writer.nullValue()
            } else {
                writer.value(duracion)
            }
            writer.endObject()
        }
        writer.endArray()
    }

    private fun writePrediction(writer: JsonWriter, prediccion: PrediccionDocumento?) {
        writer.name("prediccion")
        if (prediccion == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        writer.name("indice").value(prediccion.indice)
        writer.name("glosa").value(prediccion.glosa)
        writer.writeProbability("confianza", prediccion.confianza)
        writer.endObject()
    }

    private fun writeInterruptions(
        writer: JsonWriter,
        interrupciones: List<InterrupcionDocumento>
    ) {
        writer.name("interrupciones").beginArray()
        interrupciones.forEach { interrupcion ->
            writer.beginObject()
            writer.name("bloque").value(interrupcion.bloque)
            writer.name("posicionPendiente").value(interrupcion.posicionPendiente)
            writer.name("causa").value(interrupcion.causa.name)
            writer.writeInstant("ocurrioEn", interrupcion.ocurrioEn)
            writer.endObject()
        }
        writer.endArray()
    }

    private fun JsonWriter.writeProbability(name: String, value: Double) {
        require(value.isFinite()) { "$name debe ser un número finito" }
        this.name(name)
        this.value(BigDecimal(value.toString()).setScale(PROBABILITY_SCALE, RoundingMode.HALF_EVEN))
    }

    private fun JsonWriter.writeInstant(name: String, value: Instant) {
        this.name(name)
        this.value(INSTANT_FORMATTER.format(value))
    }

    private fun JsonWriter.writeInstantOrNull(name: String, value: Instant?) {
        this.name(name)
        if (value == null) {
            this.nullValue()
        } else {
            this.value(INSTANT_FORMATTER.format(value))
        }
    }

    private companion object {
        const val CONTRATO_NOMBRE = "helpi.evaluacion.sesion"
        const val CONTRATO_VERSION = "1.0.0"
        const val PROBABILITY_SCALE = 6
        val INSTANT_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)
    }
}
