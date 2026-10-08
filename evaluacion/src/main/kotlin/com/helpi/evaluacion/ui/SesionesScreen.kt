@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.helpi.evaluacion.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.SQLException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkManager
import com.helpi.evaluacion.BuildConfig
import com.helpi.evaluacion.R
import com.helpi.evaluacion.consentimiento.ConsentimientoNoVigenteException
import com.helpi.evaluacion.contrato.ExportadorSesion
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabaseProvider
import com.helpi.evaluacion.datos.entidades.EstadoEnvio
import com.helpi.evaluacion.datos.entidades.EstadoSesion
import com.helpi.evaluacion.datos.entidades.ResultadoIntento
import com.helpi.evaluacion.envio.ColaEnviosLlenaException
import com.helpi.evaluacion.envio.EnvioRepositorio
import com.helpi.evaluacion.envio.EnvioWorker
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TOTAL_INTENTOS = 192

internal data class SesionListaItem(
    val sesionId: String,
    val participante: String,
    val estado: EstadoSesion,
    val avance: Int,
    val envioPendiente: Boolean,
    val envioVencido: Boolean,
    val ultimaExportacionEn: Long?
) {
    val puedeRetomar: Boolean
        get() = estado == EstadoSesion.PAUSADA || estado == EstadoSesion.INTERRUMPIDA
}

internal data class SesionesUiState(
    val cargando: Boolean = true,
    val sesiones: List<SesionListaItem> = emptyList(),
    val sesionEnOperacion: String? = null,
    val mensaje: String? = null,
    val error: String? = null,
    val envioHabilitado: Boolean = false
)

internal data class SesionesAcciones(
    val onVolver: () -> Unit,
    val onRetomar: (String) -> Unit,
    val onExportar: (String) -> Unit,
    val onEnviar: (String) -> Unit
)

@Composable
@Suppress("LongMethod", "ThrowsCount", "CyclomaticComplexMethod")
fun SesionesRoute(onVolver: () -> Unit, onRetomar: (String) -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { HelpiEvaluacionDatabaseProvider.obtener(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var revisionCarga by remember { mutableIntStateOf(0) }
    var state by remember {
        mutableStateOf(SesionesUiState(envioHabilitado = BuildConfig.ENVIO_HABILITADO))
    }

    LaunchedEffect(database, revisionCarga, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state = state.copy(cargando = true)
            try {
                val sesiones = cargarSesiones(database)
                state = state.copy(cargando = false, sesiones = sesiones, error = null)
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (_: SQLException) {
                state =
                    state.copy(
                        cargando = false,
                        error = context.getString(R.string.sesiones_error_cargar)
                    )
            } catch (_: IllegalStateException) {
                state =
                    state.copy(
                        cargando = false,
                        error = context.getString(R.string.sesiones_error_cargar)
                    )
            }
        }
    }

    SesionesScreen(
        state = state,
        acciones = SesionesAcciones(
            onVolver = onVolver,
            onRetomar = onRetomar,
            onExportar = { sesionId ->
                scope.launch {
                    state = state.copy(sesionEnOperacion = sesionId, error = null, mensaje = null)
                    try {
                        val archivo = withContext(Dispatchers.IO) {
                            ExportadorSesion(database).exportar(
                                sesionId,
                                File(context.cacheDir, DIRECTORIO_EXPORTACIONES)
                            )
                        }
                        compartirArchivo(context, archivo)
                        state =
                            state.copy(
                                mensaje = context.getString(R.string.sesiones_exportacion_lista)
                            )
                        revisionCarga++
                    } catch (cancelacion: CancellationException) {
                        throw cancelacion
                    } catch (_: ActivityNotFoundException) {
                        state =
                            state.copy(
                                error = context.getString(R.string.sesiones_error_exportar)
                            )
                    } catch (_: IOException) {
                        state =
                            state.copy(
                                error = context.getString(R.string.sesiones_error_exportar)
                            )
                    } catch (_: SQLException) {
                        state =
                            state.copy(
                                error = context.getString(R.string.sesiones_error_exportar)
                            )
                    } catch (_: IllegalArgumentException) {
                        state =
                            state.copy(
                                error = context.getString(R.string.sesiones_error_exportar)
                            )
                    } catch (_: IllegalStateException) {
                        state =
                            state.copy(error = context.getString(R.string.sesiones_error_exportar))
                    } catch (_: SecurityException) {
                        state =
                            state.copy(error = context.getString(R.string.sesiones_error_exportar))
                    } finally {
                        state = state.copy(sesionEnOperacion = null)
                    }
                }
            },
            onEnviar = { sesionId ->
                scope.launch {
                    state = state.copy(sesionEnOperacion = sesionId, error = null, mensaje = null)
                    try {
                        withContext(Dispatchers.IO) {
                            EnvioRepositorio(
                                database = database,
                                workManager = WorkManager.getInstance(context),
                                crearTrabajo = { EnvioWorker.solicitud() }
                            ).encolar(sesionId)
                        }
                        state =
                            state.copy(
                                mensaje = context.getString(R.string.sesiones_envio_encolado)
                            )
                        revisionCarga++
                    } catch (cancelacion: CancellationException) {
                        throw cancelacion
                    } catch (_: ConsentimientoNoVigenteException) {
                        state =
                            state.copy(
                                error = context.getString(
                                    R.string.sesiones_error_consentimiento
                                )
                            )
                    } catch (_: ColaEnviosLlenaException) {
                        state = state.copy(error = context.getString(R.string.sesiones_error_cola))
                    } catch (_: SQLException) {
                        state =
                            state.copy(error = context.getString(R.string.sesiones_error_enviar))
                    } catch (_: IllegalStateException) {
                        state =
                            state.copy(error = context.getString(R.string.sesiones_error_enviar))
                    } catch (_: SecurityException) {
                        state =
                            state.copy(
                                error = context.getString(R.string.sesiones_error_enviar)
                            )
                    } finally {
                        state = state.copy(sesionEnOperacion = null)
                    }
                }
            }
        )
    )
}

@Composable
internal fun SesionesScreen(
    state: SesionesUiState,
    acciones: SesionesAcciones,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.sesiones_titulo),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(stringResource(R.string.sesiones_descripcion))
        OutlinedButton(onClick = acciones.onVolver, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.aviso_volver))
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.mensaje?.let { Text(it, modifier = Modifier.testTag("sessionsMessage")) }
        when {
            state.cargando -> Text(stringResource(R.string.sesiones_cargando))
            state.sesiones.isEmpty() -> Text(stringResource(R.string.sesiones_vacias))
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("sessionsList"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.sesiones, key = SesionListaItem::sesionId) { sesion ->
                    SesionCard(
                        sesion = sesion,
                        operando = state.sesionEnOperacion == sesion.sesionId,
                        envioHabilitado = state.envioHabilitado,
                        acciones = acciones
                    )
                }
            }
        }
    }
}

@Composable
private fun SesionCard(
    sesion: SesionListaItem,
    operando: Boolean,
    envioHabilitado: Boolean,
    acciones: SesionesAcciones
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("sessionCard-${sesion.sesionId}")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(sesion.participante, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(estadoSesionTexto(sesion.estado)))
            Text(
                pluralStringResource(
                    R.plurals.sesiones_avance,
                    sesion.avance,
                    sesion.avance,
                    TOTAL_INTENTOS
                )
            )
            LinearProgressIndicator(
                progress = { sesion.avance.toFloat() / TOTAL_INTENTOS },
                modifier = Modifier.fillMaxWidth().testTag("sessionProgress-${sesion.sesionId}")
            )
            if (sesion.envioPendiente) Text(stringResource(R.string.sesiones_envio_pendiente))
            if (sesion.envioVencido) Text(stringResource(R.string.sesiones_envio_vencido))
            Text(
                stringResource(
                    R.string.sesiones_ultima_exportacion,
                    textoFecha(sesion.ultimaExportacionEn)
                )
            )
            if (sesion.puedeRetomar) {
                Button(
                    onClick = { acciones.onRetomar(sesion.sesionId) },
                    enabled = !operando,
                    modifier = Modifier.fillMaxWidth().testTag("sessionResume-${sesion.sesionId}")
                ) {
                    Text(stringResource(R.string.sesiones_retomar))
                }
            }
            OutlinedButton(
                onClick = { acciones.onExportar(sesion.sesionId) },
                enabled = !operando,
                modifier = Modifier.fillMaxWidth().testTag("sessionExport-${sesion.sesionId}")
            ) {
                Text(stringResource(R.string.sesiones_exportar))
            }
            if (envioHabilitado) {
                OutlinedButton(
                    onClick = { acciones.onEnviar(sesion.sesionId) },
                    enabled = !operando,
                    modifier = Modifier.fillMaxWidth().testTag("sessionSend-${sesion.sesionId}")
                ) {
                    Text(stringResource(R.string.sesiones_enviar))
                }
            }
        }
    }
}

@Composable
private fun estadoSesionTexto(estado: EstadoSesion): Int = when (estado) {
    EstadoSesion.CREADA -> R.string.sesiones_estado_creada
    EstadoSesion.EN_CURSO -> R.string.sesiones_estado_en_curso
    EstadoSesion.PAUSADA -> R.string.sesiones_estado_pausada
    EstadoSesion.INTERRUMPIDA -> R.string.sesiones_estado_interrumpida
    EstadoSesion.COMPLETA -> R.string.sesiones_estado_completa
    EstadoSesion.TERMINADA_ANTES -> R.string.sesiones_estado_terminada
}

private fun textoFecha(milisegundos: Long?): String = milisegundos?.let {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
} ?: "—"

private suspend fun cargarSesiones(database: HelpiEvaluacionDatabase): List<SesionListaItem> =
    withContext(Dispatchers.IO) {
        val sesionDao = database.sesionDao()
        sesionDao.listarTodas().sortedByDescending { it.creadaEn }.map { sesion ->
            val intentos = database.intentoDao().listarDeSesion(sesion.id)
            val respondidas = intentos.asSequence()
                .filter { !it.reemplazado && it.resultado != ResultadoIntento.SIN_RESULTADO }
                .map { it.posicion }
                .distinct()
                .count()
            val envioPendiente = database.envioDao().listarDeSesion(sesion.id)
                .any { it.estado == EstadoEnvio.PENDIENTE }
            SesionListaItem(
                sesionId = sesion.id,
                participante = sesion.participanteCodigo,
                estado = sesion.estado,
                avance = respondidas,
                envioPendiente = envioPendiente,
                envioVencido = sesion.envioVencido,
                ultimaExportacionEn = sesion.ultimaExportacionEn
            )
        }
    }

private fun compartirArchivo(context: Context, archivo: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.evaluation.files",
        archivo
    )
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = MIME_JSON
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, archivo.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(envio, context.getString(R.string.sesiones_compartir_titulo))
    )
}

private const val DIRECTORIO_EXPORTACIONES = "exports"
private const val MIME_JSON = "application/json"
