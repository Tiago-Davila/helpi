@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.helpi.evaluacion.ui.protocolo

import android.os.SystemClock
import com.helpi.evaluacion.datos.PrediccionTopDato
import com.helpi.evaluacion.datos.entidades.CausaSinResultado
import com.helpi.evaluacion.datos.entidades.IntentoEntity
import com.helpi.evaluacion.datos.entidades.ResultadoIntento
import com.helpi.evaluacion.datos.entidades.ResultadoIntento.SIN_RESULTADO
import com.helpi.evaluacion.datos.entidades.ResultadoIntento.SOBRE_UMBRAL
import com.helpi.evaluacion.dominio.protocolo.GeneradorProtocolo
import com.helpi.evaluacion.registro.EscritorIntentos
import com.helpi.evaluacion.registro.RegistroEvento
import com.helpi.evaluacion.registro.RegistroIntento
import com.helpi.evaluacion.registro.RegistroObserver
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val UMBRAL_PREDETERMINADO = 0.9f

data class ProtocoloConfiguracion(
    val sesionId: String,
    val secuencia: List<Int>,
    val glosas: List<String>,
    val inicioSesionMs: Long,
    val umbral: Float = UMBRAL_PREDETERMINADO,
    val posicionInicial: Int = 1,
    val numeroIntentoInicial: Int = 1,
    val vioVideoInicial: Boolean = false
) {
    init {
        require(sesionId.isNotBlank()) { "sesionId no puede estar vacío" }
        require(secuencia.size == GeneradorProtocolo.INTENTOS) {
            "La secuencia debe contener ${GeneradorProtocolo.INTENTOS} intentos"
        }
        require(
            secuencia.all {
                it in glosas.indices
            }
        ) { "La secuencia contiene índices desconocidos" }
        require(glosas.size == GeneradorProtocolo.CANTIDAD_SENAS) {
            "El catálogo debe contener ${GeneradorProtocolo.CANTIDAD_SENAS} glosas"
        }
        require(posicionInicial in 1..GeneradorProtocolo.INTENTOS) {
            "La posición inicial debe estar entre 1 y ${GeneradorProtocolo.INTENTOS}"
        }
        require(numeroIntentoInicial > 0) { "numeroIntentoInicial debe ser positivo" }
        require(umbral.isFinite() && umbral in 0f..1f) { "umbral debe estar entre 0 y 1" }
    }
}

interface RelojProtocolo {
    val elapsedMs: Long
    val wallTimeMs: Long

    suspend fun esperarHasta(elapsedDeadlineMs: Long)
}

object RelojProtocoloSistema : RelojProtocolo {
    override val elapsedMs: Long
        get() = SystemClock.elapsedRealtime()

    override val wallTimeMs: Long
        get() = System.currentTimeMillis()

    override suspend fun esperarHasta(elapsedDeadlineMs: Long) {
        val restante = elapsedDeadlineMs - elapsedMs
        if (restante > 0) delay(restante)
    }
}

enum class EtapaProtocolo {
    ESPERANDO,
    RESULTADO_PENDIENTE,
    RESULTADO,
    SIN_RESULTADO,
    PAUSA_BLOQUE,
    COMPLETA,
    RETOMAR_OTRO_DIA
}

data class ProtocoloUiState(
    val etapa: EtapaProtocolo,
    val posicion: Int,
    val bloque: Int,
    val numeroIntento: Int,
    val senaEsperadaIndice: Int,
    val glosaEsperada: String,
    val resultado: ResultadoIntento? = null,
    val causaSinResultado: CausaSinResultado? = null,
    val glosaPredicha: String? = null,
    val confianza: Float? = null,
    val puedeDescartar: Boolean = false,
    val puedeRepetir: Boolean = false,
    val loHiceMal: Boolean = false,
    val vioVideo: Boolean = false,
    val intentoEncolado: Boolean = false
)

/** Único componente que convierte observaciones de Eva en intentos resueltos del protocolo. */
@Suppress("TooManyFunctions")
class ProtocoloViewModel(
    private val configuracion: ProtocoloConfiguracion,
    observer: RegistroObserver,
    private val escritor: EscritorIntentos,
    private val reloj: RelojProtocolo = RelojProtocoloSistema,
    private val scope: CoroutineScope
) : Closeable {
    private data class IntentoPendiente(
        val registro: RegistroIntento,
        val plazoDeshacerMs: Long? = null,
        val encolado: Boolean = false
    )

    private val _state = MutableStateFlow(estadoInicial())
    val state: StateFlow<ProtocoloUiState> = _state.asStateFlow()

    private var inicioIntentoMs = reloj.elapsedMs
    private var inicioSegmentoMs: Long? = null
    private var intentoPendiente: IntentoPendiente? = null
    private var timeoutJob: Job? = null
    private var deshacerJob: Job? = null
    private var generacion = 0
    private val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        observer.eventos.collect(::procesarEvento)
    }

    init {
        programarTiempoAgotado()
    }

    fun marcarVideoVisto(): Boolean {
        if (_state.value.etapa != EtapaProtocolo.ESPERANDO) return false
        _state.value = _state.value.copy(vioVideo = true)
        return true
    }

    @Suppress("ReturnCount")
    fun descartar(): Boolean {
        val pendiente = intentoPendiente ?: return false
        val estado = _state.value
        if (estado.etapa != EtapaProtocolo.RESULTADO_PENDIENTE || !estado.puedeDescartar) {
            return false
        }
        if (pendiente.plazoDeshacerMs?.let { reloj.elapsedMs >= it } != false) return false

        deshacerJob?.cancel()
        val registro = pendiente.registro.copy(
            intento = pendiente.registro.intento.copy(descartado = true)
        )
        guardar(registro)
        _state.value = estado.copy(
            etapa = EtapaProtocolo.RESULTADO,
            glosaPredicha = null,
            puedeDescartar = false,
            intentoEncolado = intentoPendiente?.encolado == true
        )
        return true
    }

    @Suppress("ReturnCount")
    fun marcarLoHiceMal(): Boolean {
        val pendiente = intentoPendiente ?: return false
        val estado = _state.value
        if (estado.etapa !in setOf(
                EtapaProtocolo.RESULTADO_PENDIENTE,
                EtapaProtocolo.RESULTADO,
                EtapaProtocolo.SIN_RESULTADO
            ) ||
            estado.loHiceMal
        ) {
            return false
        }
        val actualizado = pendiente.registro.copy(
            intento = pendiente.registro.intento.copy(loHiceMal = true)
        )
        if (pendiente.encolado && !encolarMarca(actualizado.intento)) return false
        intentoPendiente = pendiente.copy(registro = actualizado)
        _state.value = estado.copy(loHiceMal = true)
        return true
    }

    fun repetirIntento(): Boolean {
        if (_state.value.etapa != EtapaProtocolo.SIN_RESULTADO) return false
        val estado = _state.value
        iniciarIntento(estado.posicion, estado.numeroIntento + 1, false)
        return true
    }

    fun continuar(): Boolean {
        val estado = _state.value
        if (estado.etapa != EtapaProtocolo.RESULTADO) return false
        if (estado.posicion == GeneradorProtocolo.INTENTOS) {
            cancelarTemporizadores()
            _state.value = estado.copy(etapa = EtapaProtocolo.COMPLETA, puedeDescartar = false)
        } else if (estado.posicion % GeneradorProtocolo.INTENTOS_POR_BLOQUE == 0) {
            cancelarTemporizadores()
            _state.value = estado.copy(etapa = EtapaProtocolo.PAUSA_BLOQUE)
        } else {
            iniciarIntento(estado.posicion + 1, 1, false)
        }
        return true
    }

    fun seguir(): Boolean {
        val estado = _state.value
        if (estado.etapa != EtapaProtocolo.PAUSA_BLOQUE ||
            estado.posicion >= GeneradorProtocolo.INTENTOS
        ) {
            return false
        }
        iniciarIntento(estado.posicion + 1, 1, false)
        return true
    }

    fun retomarOtroDia(): Boolean {
        val estado = _state.value
        if (estado.etapa != EtapaProtocolo.PAUSA_BLOQUE) return false
        cancelarTemporizadores()
        _state.value = estado.copy(etapa = EtapaProtocolo.RETOMAR_OTRO_DIA)
        return true
    }

    override fun close() {
        cancelarTemporizadores()
        collector.cancel()
    }

    private fun procesarEvento(evento: RegistroEvento) {
        when (evento) {
            is RegistroEvento.SegmentoIniciado -> iniciarSegmento(evento.inicioMs)
            is RegistroEvento.SinResultado -> resolverSinResultado(
                CausaSinResultado.valueOf(evento.causa.name)
            )
            is RegistroEvento.Decision -> resolverDecision(evento)
        }
    }

    private fun iniciarSegmento(inicioMs: Long) {
        if (_state.value.etapa != EtapaProtocolo.ESPERANDO) return
        timeoutJob?.cancel()
        timeoutJob = null
        inicioSegmentoMs = inicioMs
    }

    private fun resolverDecision(evento: RegistroEvento.Decision) {
        if (_state.value.etapa != EtapaProtocolo.ESPERANDO) return
        if (!decisionValida(evento)) {
            resolverSinResultado(CausaSinResultado.ERROR_MODELO)
            return
        }
        val superaUmbral = evento.confianza >= evento.umbral
        val resultado = if (superaUmbral) SOBRE_UMBRAL else ResultadoIntento.BAJO_UMBRAL
        val indiceEsperado = indiceEsperado(_state.value.posicion)
        val registro = crearRegistroDecision(evento, resultado, superaUmbral, indiceEsperado)
        val plazo = if (resultado == SOBRE_UMBRAL) reloj.elapsedMs + VENTANA_DESHACER_MS else null
        intentoPendiente = IntentoPendiente(registro, plazo)
        timeoutJob?.cancel()
        timeoutJob = null
        val etapaResultado = if (plazo == null) {
            EtapaProtocolo.RESULTADO
        } else {
            EtapaProtocolo.RESULTADO_PENDIENTE
        }
        _state.value = _state.value.copy(
            etapa = etapaResultado,
            resultado = resultado,
            causaSinResultado = null,
            glosaPredicha = if (superaUmbral) prediccionGlosa(evento.indice) else null,
            confianza = evento.confianza.takeIf { superaUmbral },
            puedeDescartar = plazo != null,
            puedeRepetir = false,
            loHiceMal = false,
            intentoEncolado = false
        )
        if (plazo == null) {
            persistirPendiente()
        } else {
            programarDeshacer(plazo)
        }
    }

    private fun crearRegistroDecision(
        evento: RegistroEvento.Decision,
        resultado: ResultadoIntento,
        superaUmbral: Boolean,
        indiceEsperado: Int
    ): RegistroIntento {
        val estado = _state.value
        val top3 = evento.predicciones.take(TOP_PREDICCIONES).mapIndexed { indice, opcion ->
            PrediccionTopDato(
                rango = indice + 1,
                indice = opcion.indice,
                glosa = configuracion.glosas[opcion.indice],
                confianza = opcion.confianza
            )
        }
        val intento = IntentoEntity(
            sesionId = configuracion.sesionId,
            posicion = estado.posicion,
            bloque = estado.bloque,
            numeroIntento = estado.numeroIntento,
            reemplazado = false,
            senaEsperadaIndice = indiceEsperado,
            senaEsperadaGlosa = configuracion.glosas[indiceEsperado],
            vioVideo = estado.vioVideo,
            resultado = resultado,
            causaSinResultado = null,
            predichoIndice = evento.indice,
            predichoGlosa = configuracion.glosas[evento.indice],
            confianza = evento.confianza,
            umbral = evento.umbral,
            superoUmbral = superaUmbral,
            correcto = resultado == SOBRE_UMBRAL && evento.indice == indiceEsperado,
            descartado = false,
            loHiceMal = false,
            inicioRelMs = (inicioIntentoMs - configuracion.inicioSesionMs).coerceAtLeast(0),
            duracionSegmentoMs = duracionSegmento(evento.finSegmentoMs)
        )
        return RegistroIntento(intento, top3, reloj.wallTimeMs)
    }

    private fun decisionValida(evento: RegistroEvento.Decision): Boolean {
        val top3 = evento.predicciones.take(TOP_PREDICCIONES)
        return evento.razon.name != RAZON_SALIDA_INVALIDA &&
            evento.indice in configuracion.glosas.indices &&
            evento.umbral.isFinite() &&
            evento.umbral in 0f..1f &&
            evento.confianza.isFinite() &&
            evento.confianza in 0f..1f &&
            evento.aceptada == (evento.confianza >= evento.umbral) &&
            evento.predicciones.size == TOP_PREDICCIONES &&
            top3.firstOrNull()?.let {
                it.indice == evento.indice && it.confianza == evento.confianza
            } == true &&
            top3.all { it.indice in configuracion.glosas.indices && it.confianza.isFinite() } &&
            top3.zipWithNext().all { (actual, siguiente) ->
                actual.confianza >= siguiente.confianza
            }
    }

    private fun resolverSinResultado(causa: CausaSinResultado) {
        if (_state.value.etapa != EtapaProtocolo.ESPERANDO) return
        cancelarTemporizadores()
        val estado = _state.value
        val intento = IntentoEntity(
            sesionId = configuracion.sesionId,
            posicion = estado.posicion,
            bloque = estado.bloque,
            numeroIntento = estado.numeroIntento,
            reemplazado = false,
            senaEsperadaIndice = estado.senaEsperadaIndice,
            senaEsperadaGlosa = estado.glosaEsperada,
            vioVideo = estado.vioVideo,
            resultado = SIN_RESULTADO,
            causaSinResultado = causa,
            predichoIndice = null,
            predichoGlosa = null,
            confianza = null,
            umbral = configuracion.umbral,
            superoUmbral = false,
            correcto = false,
            descartado = false,
            loHiceMal = false,
            inicioRelMs = (inicioIntentoMs - configuracion.inicioSesionMs).coerceAtLeast(0),
            duracionSegmentoMs = null
        )
        intentoPendiente = IntentoPendiente(
            RegistroIntento(intento, emptyList(), reloj.wallTimeMs)
        )
        _state.value = estado.copy(
            etapa = EtapaProtocolo.SIN_RESULTADO,
            resultado = SIN_RESULTADO,
            causaSinResultado = causa,
            glosaPredicha = null,
            confianza = null,
            puedeDescartar = false,
            puedeRepetir = true,
            intentoEncolado = false
        )
        persistirPendiente()
    }

    private fun programarTiempoAgotado() {
        val generacionActual = generacion
        val plazo = inicioIntentoMs + TIEMPO_AGOTADO_MS
        timeoutJob = scope.launch {
            reloj.esperarHasta(plazo)
            if (generacion == generacionActual &&
                _state.value.etapa == EtapaProtocolo.ESPERANDO
            ) {
                resolverSinResultado(CausaSinResultado.TIEMPO_AGOTADO)
            }
        }
    }

    private fun programarDeshacer(plazo: Long) {
        val generacionActual = generacion
        deshacerJob = scope.launch {
            reloj.esperarHasta(plazo)
            val pendiente = intentoPendiente
            if (generacion == generacionActual && pendiente?.plazoDeshacerMs == plazo) {
                persistirPendiente()
                _state.value = _state.value.copy(
                    etapa = EtapaProtocolo.RESULTADO,
                    puedeDescartar = false,
                    intentoEncolado = intentoPendiente?.encolado == true
                )
            }
        }
    }

    private fun persistirPendiente() {
        val pendiente = intentoPendiente ?: return
        if (pendiente.encolado) return
        val encolado = escritor.trySend(pendiente.registro)
        intentoPendiente = pendiente.copy(encolado = encolado)
        _state.value = _state.value.copy(intentoEncolado = encolado)
    }

    private fun guardar(registro: RegistroIntento) {
        val pendiente = intentoPendiente ?: return
        if (pendiente.encolado) return
        val encolado = escritor.trySend(registro)
        intentoPendiente = pendiente.copy(registro = registro, encolado = encolado)
    }

    private fun encolarMarca(intento: IntentoEntity): Boolean =
        escritor.marcarLoHiceMal(intento.sesionId, intento.posicion, intento.numeroIntento)

    private fun iniciarIntento(posicion: Int, numeroIntento: Int, vioVideo: Boolean) {
        cancelarTemporizadores()
        generacion++
        inicioIntentoMs = reloj.elapsedMs
        inicioSegmentoMs = null
        intentoPendiente = null
        val indice = indiceEsperado(posicion)
        _state.value = ProtocoloUiState(
            etapa = EtapaProtocolo.ESPERANDO,
            posicion = posicion,
            bloque = bloqueDe(posicion),
            numeroIntento = numeroIntento,
            senaEsperadaIndice = indice,
            glosaEsperada = configuracion.glosas[indice],
            vioVideo = vioVideo
        )
        programarTiempoAgotado()
    }

    private fun cancelarTemporizadores() {
        timeoutJob?.cancel()
        timeoutJob = null
        deshacerJob?.cancel()
        deshacerJob = null
    }

    private fun duracionSegmento(finMs: Long): Long? = inicioSegmentoMs
        ?.let { finMs - it }
        ?.takeIf { it >= 0 }

    private fun indiceEsperado(posicion: Int): Int = configuracion.secuencia[posicion - 1]

    private fun prediccionGlosa(indice: Int): String? = configuracion.glosas.getOrNull(indice)

    private fun estadoInicial(): ProtocoloUiState {
        val indice = indiceEsperado(configuracion.posicionInicial)
        return ProtocoloUiState(
            etapa = EtapaProtocolo.ESPERANDO,
            posicion = configuracion.posicionInicial,
            bloque = bloqueDe(configuracion.posicionInicial),
            numeroIntento = configuracion.numeroIntentoInicial,
            senaEsperadaIndice = indice,
            glosaEsperada = configuracion.glosas[indice],
            vioVideo = configuracion.vioVideoInicial
        )
    }

    private companion object {
        const val VENTANA_DESHACER_MS = 3_000L
        const val TIEMPO_AGOTADO_MS = 15_000L
        const val TOP_PREDICCIONES = 3
        const val RAZON_SALIDA_INVALIDA = "INVALID_OUTPUT"

        fun bloqueDe(posicion: Int): Int =
            (posicion - 1) / GeneradorProtocolo.INTENTOS_POR_BLOQUE + 1
    }
}
