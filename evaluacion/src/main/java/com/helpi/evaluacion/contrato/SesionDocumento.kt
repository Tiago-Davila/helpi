package com.helpi.evaluacion.contrato

import java.time.Instant
import java.util.UUID

data class SesionDocumento(
    val envio: EnvioDocumento,
    val sesion: SesionRegistroDocumento,
    val participante: ParticipanteDocumento,
    val consentimiento: ConsentimientoDocumento,
    val condiciones: CondicionesDocumento,
    val versiones: VersionesDocumento,
    val dispositivo: DispositivoDocumento,
    val protocolo: ProtocoloDocumento,
    val intentos: List<IntentoDocumento>,
    val interrupciones: List<InterrupcionDocumento>
)

data class EnvioDocumento(val envioId: UUID, val revision: Int, val generadoEn: Instant)

data class SesionRegistroDocumento(
    val sesionId: UUID,
    val estado: EstadoSesionDocumento,
    val creadaEn: Instant,
    val ultimoIntentoEn: Instant?,
    val intentosPerdidos: Int
)

enum class EstadoSesionDocumento {
    CREADA,
    EN_CURSO,
    PAUSADA,
    INTERRUMPIDA,
    COMPLETA,
    TERMINADA_ANTES
}

data class ParticipanteDocumento(val codigo: String)

data class ConsentimientoDocumento(val avisoVersion: String, val otorgadoEn: Instant)

data class CondicionesDocumento(
    val entorno: String,
    val tipoEntorno: String,
    val iluminacion: String,
    val contraluz: Boolean,
    val distancia: String,
    val manoDominante: String,
    val guantes: Boolean,
    val soporteCamara: String,
    val perfilParticipante: String
)

data class VersionesDocumento(
    val appVersionName: String,
    val appVersionCode: Int,
    val modelo: String,
    val modeloSha256: String,
    val catalogoSha256: String,
    val contratoKeypoints: Int,
    val protocolo: String
)

data class DispositivoDocumento(val fabricante: String, val modelo: String, val sdkAndroid: Int)

data class ProtocoloDocumento(
    val version: String,
    val semilla: Long,
    val senas: Int,
    val repeticiones: Int,
    val bloques: Int,
    val intentosPorBloque: Int,
    val secuencia: List<Int>
)

data class SeniaDocumento(val indice: Int, val glosa: String)

data class PrediccionDocumento(val indice: Int, val glosa: String, val confianza: Double)

data class PrediccionTopDocumento(
    val rango: Int,
    val indice: Int,
    val glosa: String,
    val confianza: Double
)

data class IntentoDocumento(
    val posicion: Int,
    val bloque: Int,
    val numeroIntento: Int,
    val reemplazado: Boolean,
    val senaEsperada: SeniaDocumento,
    val vioVideo: Boolean,
    val resultado: ResultadoIntentoDocumento,
    val causaSinResultado: CausaSinResultadoDocumento?,
    val prediccion: PrediccionDocumento?,
    val top3: List<PrediccionTopDocumento>,
    val umbral: Double,
    val superoUmbral: Boolean,
    val correcto: Boolean,
    val descartado: Boolean,
    val loHiceMal: Boolean,
    val inicioRelMs: Long,
    val duracionSegmentoMs: Long?
)

enum class ResultadoIntentoDocumento {
    SOBRE_UMBRAL,
    BAJO_UMBRAL,
    SIN_RESULTADO
}

enum class CausaSinResultadoDocumento {
    SIN_HOMBROS,
    SEGMENTO_CORTO,
    SEGMENTO_LARGO,
    SEGUIMIENTO_PERDIDO,
    ORIENTACION_CAMBIO,
    ERROR_MODELO,
    TIEMPO_AGOTADO
}

data class InterrupcionDocumento(
    val bloque: Int,
    val posicionPendiente: Int,
    val causa: CausaInterrupcionDocumento,
    val ocurrioEn: Instant
)

enum class CausaInterrupcionDocumento {
    SEGUNDO_PLANO,
    PROCESO_TERMINADO,
    REVOCACION,
    PERSONA_PAUSO_DIA
}
