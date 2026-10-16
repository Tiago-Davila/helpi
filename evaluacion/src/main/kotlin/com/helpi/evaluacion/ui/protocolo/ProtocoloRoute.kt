@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.helpi.evaluacion.ui.protocolo

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helpi.evaluacion.R
import com.helpi.evaluacion.consentimiento.AVISO_VERSION_ACTUAL
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabaseProvider
import com.helpi.evaluacion.datos.entidades.CausaInterrupcion
import com.helpi.evaluacion.datos.entidades.EstadoSesion
import com.helpi.evaluacion.datos.entidades.InterrupcionEntity
import com.helpi.evaluacion.datos.entidades.ResultadoIntento
import com.helpi.evaluacion.dominio.protocolo.GeneradorProtocolo
import com.helpi.evaluacion.dominio.sesion.CalculoPosicionRetoma
import com.helpi.evaluacion.registro.EscritorRegistro
import com.helpi.evaluacion.registro.RegistroObserver
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun ProtocoloRoute(
    sessionId: String?,
    camara: ProtocoloCamaraUiState,
    observer: RegistroObserver,
    cameraPreview: @Composable () -> Unit,
    onStartRecognition: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val camaraActualizada = rememberUpdatedState(camara)
    var host by remember(sessionId) { mutableStateOf<HostState>(HostState.Loading) }
    var completionSaved by remember(sessionId) { mutableStateOf(false) }
    val completionSavedActualizado = rememberUpdatedState(completionSaved)

    LaunchedEffect(sessionId, observer) {
        if (sessionId.isNullOrBlank()) {
            host = HostState.Error(context.getString(R.string.protocolo_error_sesion))
            return@LaunchedEffect
        }

        onStartRecognition()
        val preparado = snapshotFlow {
            camaraActualizada.value.preparacionTerminada to
                camaraActualizada.value.listoParaReconocer
        }.first { (terminada, lista) -> terminada || lista }
        if (!preparado.second) {
            host = HostState.Error(context.getString(R.string.protocolo_error_camara))
            return@LaunchedEffect
        }

        try {
            host = prepararProtocolo(
                context = context,
                sessionId = sessionId,
                observer = observer,
                scope = scope,
                confidenceThreshold = camaraActualizada.value.umbral
            )
        } catch (cancelacion: CancellationException) {
            throw cancelacion
        } catch (_: Exception) {
            host = HostState.Error(context.getString(R.string.protocolo_error_sesion))
        }
    }

    val active = (host as? HostState.Active)?.sesion
    DisposableEffect(active) {
        onDispose { active?.cerrarAlSalir() }
    }

    if (active == null) {
        val mensaje = when (val actual = host) {
            HostState.Loading -> stringResource(R.string.protocolo_cargando)
            is HostState.Error -> actual.mensaje
            is HostState.Active -> error("Estado activo sin sesión")
        }
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(mensaje, style = MaterialTheme.typography.bodyLarge)
            if (host is HostState.Error) {
                OutlinedButton(onClick = onExit) {
                    Text(stringResource(R.string.protocolo_salir))
                }
            }
        }
        return
    }

    val state by active.viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(active, state.etapa) {
        if (state.etapa == EtapaProtocolo.COMPLETA && !completionSaved) {
            active.cerrar()
            val updated = active.database.sesionDao().marcarCompletaSiEnCurso(active.sessionId)
            completionSaved = updated == 1 ||
                active.database.sesionDao().estado(active.sessionId) == EstadoSesion.COMPLETA
        }
    }

    val callbacks = remember(active) {
        ProtocoloAcciones(
            onDescartar = { active.viewModel.descartar() },
            onRepetir = { active.viewModel.repetirIntento() },
            onLoHiceMal = { active.viewModel.marcarLoHiceMal() },
            onContinuar = { active.viewModel.continuar() },
            onSeguir = {
                scope.launch {
                    val siguienteBloque = active.viewModel.state.value.bloque + 1
                    val actualizo = active.database.sesionDao().actualizarBloqueEnCurso(
                        active.sessionId,
                        siguienteBloque
                    )
                    if (actualizo == 1) active.viewModel.seguir()
                }
            },
            onRetomarOtroDia = {
                if (active.viewModel.retomarOtroDia()) {
                    pausarYSalir(active, scope, onExit, siguienteBloque = true)
                }
            },
            onPausar = { pausarYSalir(active, scope, onExit, siguienteBloque = false) },
            onTerminarSesion = {
                scope.launch {
                    active.cerrar()
                    active.database.sesionDao().marcarTerminadaAntesSiEnCurso(active.sessionId)
                    onExit()
                }
            },
            onSalir = {
                scope.launch {
                    if (!completionSavedActualizado.value) active.cerrar()
                    onExit()
                }
            }
        )
    }

    ProtocoloScreen(
        state = state,
        camara = camara,
        cameraPreview = cameraPreview,
        acciones = callbacks,
        modifier = modifier
    )
}

private sealed interface HostState {
    data object Loading : HostState

    data class Error(val mensaje: String) : HostState

    data class Active(val sesion: SesionActiva) : HostState
}

private class SesionActiva(
    val sessionId: String,
    val database: HelpiEvaluacionDatabase,
    val writer: EscritorRegistro,
    val viewModel: ProtocoloViewModel
) {
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val disposed = AtomicBoolean(false)
    private var cerrado = false

    suspend fun cerrar() {
        mutex.withLock {
            if (!cerrado) {
                viewModel.close()
                writer.cerrarYEsperar()
                cerrado = true
            }
        }
    }

    fun cerrarAlSalir() {
        if (disposed.compareAndSet(false, true)) {
            cleanupScope.launch {
                cerrar()
                cleanupScope.cancel()
            }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
private suspend fun prepararProtocolo(
    context: Context,
    sessionId: String,
    observer: RegistroObserver,
    scope: CoroutineScope,
    confidenceThreshold: Float
): HostState {
    val database = HelpiEvaluacionDatabaseProvider.obtener(context)
    val sesion = database.sesionDao().buscar(sessionId)
        ?: return HostState.Error(context.getString(R.string.protocolo_error_sesion))
    if (sesion.estado !in setOf(
            EstadoSesion.CREADA,
            EstadoSesion.EN_CURSO,
            EstadoSesion.PAUSADA,
            EstadoSesion.INTERRUMPIDA
        )
    ) {
        return HostState.Error(context.getString(R.string.protocolo_error_sesion))
    }

    val consentimiento = database.consentimientoDao().buscar(sesion.consentimientoId)
        ?: return HostState.Error(context.getString(R.string.protocolo_error_sesion))
    if (consentimiento.revocadoEn != null ||
        !database.consentimientoDao().consentimientoActualVigente(
            consentimiento.id,
            AVISO_VERSION_ACTUAL
        )
    ) {
        return HostState.Error(context.getString(R.string.protocolo_error_sesion))
    }

    val glosas = context.assets.open(CATALOGO_ASSET).bufferedReader(Charsets.UTF_8).use {
        val array = JSONObject(it.readText()).getJSONArray("glosas")
        List(array.length()) { indice -> array.getString(indice) }
    }
    val protocolo = GeneradorProtocolo.generar(sesion.semilla)
    val interrupcion = database.sesionDao().interrupciones(sessionId).lastOrNull()
    val bloque = interrupcion?.bloque?.takeIf {
        sesion.estado == EstadoSesion.PAUSADA || sesion.estado == EstadoSesion.INTERRUMPIDA
    } ?: sesion.bloqueActual
    val intentosPrevios = database.intentoDao().listarDeSesion(sessionId)
    val respondidas = intentosPrevios.asSequence()
        .filter { it.bloque == bloque && it.resultado != ResultadoIntento.SIN_RESULTADO }
        .map { it.posicion }
        .toSet()
    val posicion = interrupcion?.posicionPendiente?.takeIf {
        sesion.estado == EstadoSesion.PAUSADA || sesion.estado == EstadoSesion.INTERRUMPIDA
    } ?: CalculoPosicionRetoma.primerIntentoSinRespuesta(bloque, respondidas)
        .orElse((bloque - 1) * GeneradorProtocolo.INTENTOS_POR_BLOQUE + 1)
    val intentoAnterior = database.intentoDao().ultimoDePosicion(sessionId, posicion)
    val numeroIntento = if (intentoAnterior?.resultado == ResultadoIntento.SIN_RESULTADO) {
        intentoAnterior.numeroIntento + 1
    } else {
        1
    }
    require(glosas.size == GeneradorProtocolo.CANTIDAD_SENAS) { "Catálogo de señas inválido" }
    require(posicion in 1..GeneradorProtocolo.INTENTOS) { "Posición pendiente inválida" }

    if (sesion.estado != EstadoSesion.EN_CURSO &&
        !database.sesionDao().iniciarBloque(
            sessionId,
            bloque,
            consentimiento.avisoVersion,
            consentimiento.avisoVideoSha256
        )
    ) {
        return HostState.Error(context.getString(R.string.protocolo_error_sesion))
    }

    val writer = EscritorRegistro(database.intentoDao())
    val antiguedadMs = (System.currentTimeMillis() - sesion.creadaEn).coerceAtLeast(0L)
    val inicioSesion = (SystemClock.elapsedRealtime() - antiguedadMs).coerceAtLeast(0L)
    val viewModel = ProtocoloViewModel(
        configuracion = ProtocoloConfiguracion(
            sesionId = sessionId,
            secuencia = protocolo.secuencia,
            glosas = glosas,
            inicioSesionMs = inicioSesion,
            umbral = confidenceThreshold,
            posicionInicial = posicion,
            numeroIntentoInicial = numeroIntento,
            vioVideoInicial = intentoAnterior?.vioVideo ?: false
        ),
        observer = observer,
        escritor = writer,
        scope = scope
    )
    return HostState.Active(SesionActiva(sessionId, database, writer, viewModel))
}

private fun pausarYSalir(
    active: SesionActiva,
    scope: CoroutineScope,
    onExit: () -> Unit,
    siguienteBloque: Boolean
) {
    scope.launch {
        active.cerrar()
        val state = active.viewModel.state.value
        var bloque: Int
        var posicionPendiente: Int
        if (siguienteBloque || state.etapa == EtapaProtocolo.PAUSA_BLOQUE) {
            bloque = state.bloque + 1
            posicionPendiente = (bloque - 1) * GeneradorProtocolo.INTENTOS_POR_BLOQUE + 1
        } else {
            val respondidas = active.database.intentoDao().listarDeSesion(active.sessionId)
                .asSequence()
                .filter {
                    it.bloque == state.bloque && it.resultado != ResultadoIntento.SIN_RESULTADO
                }
                .map { it.posicion }
                .toSet()
            val pendiente =
                CalculoPosicionRetoma.primerIntentoSinRespuesta(state.bloque, respondidas)
            if (pendiente.isPresent) {
                bloque = state.bloque
                posicionPendiente = pendiente.asInt
            } else if (state.bloque < GeneradorProtocolo.CANTIDAD_BLOQUES) {
                bloque = state.bloque + 1
                posicionPendiente = (bloque - 1) * GeneradorProtocolo.INTENTOS_POR_BLOQUE + 1
            } else {
                active.database.sesionDao().marcarCompletaSiEnCurso(active.sessionId)
                onExit()
                return@launch
            }
        }
        val pausada = active.database.sesionDao().pausarSiEnCurso(
            InterrupcionEntity(
                sesionId = active.sessionId,
                bloque = bloque,
                posicionPendiente = posicionPendiente,
                causa = CausaInterrupcion.PERSONA_PAUSO_DIA,
                ocurrioEn = System.currentTimeMillis()
            )
        )
        if (pausada) onExit()
    }
}

private const val CATALOGO_ASSET = "lsa/catalogo_senas.json"
