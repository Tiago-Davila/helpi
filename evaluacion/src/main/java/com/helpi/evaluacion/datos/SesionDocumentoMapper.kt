package com.helpi.evaluacion.datos

import com.helpi.evaluacion.contrato.CausaInterrupcionDocumento
import com.helpi.evaluacion.contrato.CausaSinResultadoDocumento
import com.helpi.evaluacion.contrato.CondicionesDocumento
import com.helpi.evaluacion.contrato.ConsentimientoDocumento
import com.helpi.evaluacion.contrato.DispositivoDocumento
import com.helpi.evaluacion.contrato.EnvioDocumento
import com.helpi.evaluacion.contrato.EstadoSesionDocumento
import com.helpi.evaluacion.contrato.IntentoDocumento
import com.helpi.evaluacion.contrato.InterrupcionDocumento
import com.helpi.evaluacion.contrato.ParticipanteDocumento
import com.helpi.evaluacion.contrato.PrediccionDocumento
import com.helpi.evaluacion.contrato.PrediccionTopDocumento
import com.helpi.evaluacion.contrato.ProtocoloDocumento
import com.helpi.evaluacion.contrato.ResultadoIntentoDocumento
import com.helpi.evaluacion.contrato.SeniaDocumento
import com.helpi.evaluacion.contrato.SesionDocumento
import com.helpi.evaluacion.contrato.SesionRegistroDocumento
import com.helpi.evaluacion.contrato.VersionesDocumento
import com.helpi.evaluacion.datos.entidades.CondicionesPruebaEntity
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity
import com.helpi.evaluacion.datos.entidades.IntentoEntity
import com.helpi.evaluacion.datos.entidades.ParticipanteEntity
import com.helpi.evaluacion.datos.entidades.SesionEntity
import com.helpi.evaluacion.dominio.protocolo.GeneradorProtocolo
import java.time.Instant
import java.util.UUID

class SesionDocumentoMapper(private val database: HelpiEvaluacionDatabase) {
    suspend fun mapear(sesionId: String, envioId: UUID, generadoEn: Instant): SesionDocumento {
        val datos = buscarDatosSesion(sesionId)
        val sesion = datos.sesion
        check(sesion.protocoloVersion == GeneradorProtocolo.VERSION) {
            "Versión de protocolo no soportada: ${sesion.protocoloVersion}"
        }

        return SesionDocumento(
            envio = EnvioDocumento(
                envioId = envioId,
                revision = Math.addExact(sesion.revision, 1),
                generadoEn = generadoEn
            ),
            sesion = mapearSesion(sesion),
            participante = ParticipanteDocumento(datos.participante.codigo),
            consentimiento = ConsentimientoDocumento(
                avisoVersion = datos.consentimiento.avisoVersion,
                otorgadoEn = Instant.ofEpochMilli(datos.consentimiento.otorgadoEn)
            ),
            condiciones = mapearCondiciones(datos.condiciones),
            versiones = mapearVersiones(sesion),
            dispositivo = DispositivoDocumento(
                fabricante = sesion.dispositivoFabricante,
                modelo = sesion.dispositivoModelo,
                sdkAndroid = sesion.sdkAndroid
            ),
            protocolo = mapearProtocolo(sesion),
            intentos = mapearIntentos(sesionId),
            interrupciones = mapearInterrupciones(sesionId)
        )
    }

    private suspend fun buscarDatosSesion(sesionId: String): DatosSesion {
        val sesion = encontrado(database.sesionDao().buscar(sesionId)) {
            "No existe la sesión $sesionId"
        }
        val condiciones = encontrado(database.sesionDao().condiciones(sesionId)) {
            "La sesión $sesionId no tiene condiciones de prueba"
        }
        val participante =
            encontrado(database.participanteDao().buscar(sesion.participanteCodigo)) {
                "No existe el participante ${sesion.participanteCodigo}"
            }
        val consentimiento =
            encontrado(database.consentimientoDao().buscar(sesion.consentimientoId)) {
                "No existe el consentimiento ${sesion.consentimientoId} de la sesión $sesionId"
            }
        return DatosSesion(sesion, condiciones, participante, consentimiento)
    }

    private fun mapearSesion(sesion: SesionEntity) = SesionRegistroDocumento(
        sesionId = UUID.fromString(sesion.id),
        estado = EstadoSesionDocumento.valueOf(sesion.estado.name),
        creadaEn = Instant.ofEpochMilli(sesion.creadaEn),
        ultimoIntentoEn = sesion.ultimoIntentoEn?.let(Instant::ofEpochMilli),
        intentosPerdidos = sesion.intentosPerdidos
    )

    private fun mapearCondiciones(condiciones: CondicionesPruebaEntity) = CondicionesDocumento(
        entorno = condiciones.entorno.name,
        tipoEntorno = condiciones.tipoEntorno.name,
        iluminacion = condiciones.iluminacion.name,
        contraluz = condiciones.contraluz,
        distancia = condiciones.distancia.name,
        manoDominante = condiciones.manoDominante.name,
        guantes = condiciones.guantes,
        soporteCamara = condiciones.soporteCamara.name,
        perfilParticipante = condiciones.perfilParticipante.name
    )

    private fun mapearVersiones(sesion: SesionEntity) = VersionesDocumento(
        appVersionName = sesion.appVersionName,
        appVersionCode = sesion.appVersionCode,
        modelo = sesion.modeloVersion,
        modeloSha256 = sesion.modeloSha256.lowercase(),
        catalogoSha256 = sesion.catalogoSha256.lowercase(),
        contratoKeypoints = sesion.contratoKeypoints,
        protocolo = sesion.protocoloVersion
    )

    private fun mapearProtocolo(sesion: SesionEntity): ProtocoloDocumento {
        val protocolo = GeneradorProtocolo.generar(sesion.semilla)
        return ProtocoloDocumento(
            version = protocolo.version(),
            semilla = protocolo.semilla(),
            senas = GeneradorProtocolo.CANTIDAD_SENAS,
            repeticiones = GeneradorProtocolo.REPETICIONES,
            bloques = GeneradorProtocolo.CANTIDAD_BLOQUES,
            intentosPorBloque = GeneradorProtocolo.INTENTOS_POR_BLOQUE,
            secuencia = protocolo.secuencia()
        )
    }

    private suspend fun mapearIntentos(sesionId: String): List<IntentoDocumento> {
        val dao = database.intentoDao()
        return dao.listarDeSesion(sesionId).map { intento -> mapearIntento(dao, intento) }
    }

    private suspend fun mapearIntento(dao: IntentoDao, intento: IntentoEntity): IntentoDocumento {
        val top3 = dao.predicciones(intento.id).map { prediccion ->
            PrediccionTopDocumento(
                rango = prediccion.rango,
                indice = prediccion.indice,
                glosa = prediccion.glosa,
                confianza = prediccion.confianza.toDouble()
            )
        }
        val prediccion = intento.predichoIndice?.let { indice ->
            PrediccionDocumento(
                indice = indice,
                glosa = checkNotNull(intento.predichoGlosa) {
                    "El intento ${intento.id} no tiene glosa para su predicción"
                },
                confianza = checkNotNull(intento.confianza) {
                    "El intento ${intento.id} no tiene confianza para su predicción"
                }.toDouble()
            )
        }
        return IntentoDocumento(
            posicion = intento.posicion,
            bloque = intento.bloque,
            numeroIntento = intento.numeroIntento,
            reemplazado = intento.reemplazado,
            senaEsperada = SeniaDocumento(intento.senaEsperadaIndice, intento.senaEsperadaGlosa),
            vioVideo = intento.vioVideo,
            resultado = ResultadoIntentoDocumento.valueOf(intento.resultado.name),
            causaSinResultado = intento.causaSinResultado?.let {
                CausaSinResultadoDocumento.valueOf(it.name)
            },
            prediccion = prediccion,
            top3 = top3,
            umbral = intento.umbral.toDouble(),
            superoUmbral = intento.superoUmbral,
            correcto = intento.correcto,
            descartado = intento.descartado,
            loHiceMal = intento.loHiceMal,
            inicioRelMs = intento.inicioRelMs,
            duracionSegmentoMs = intento.duracionSegmentoMs
        )
    }

    private suspend fun mapearInterrupciones(sesionId: String) =
        database.sesionDao().interrupciones(sesionId).map { interrupcion ->
            InterrupcionDocumento(
                bloque = interrupcion.bloque,
                posicionPendiente = interrupcion.posicionPendiente,
                causa = CausaInterrupcionDocumento.valueOf(interrupcion.causa.name),
                ocurrioEn = Instant.ofEpochMilli(interrupcion.ocurrioEn)
            )
        }

    private inline fun <T : Any> encontrado(valor: T?, lazyMessage: () -> String): T =
        checkNotNull(valor, lazyMessage)

    private data class DatosSesion(
        val sesion: SesionEntity,
        val condiciones: CondicionesPruebaEntity,
        val participante: ParticipanteEntity,
        val consentimiento: ConsentimientoEntity
    )
}
