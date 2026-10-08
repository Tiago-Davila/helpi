@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.helpi.evaluacion.ui

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.helpi.evaluacion.R
import com.helpi.evaluacion.consentimiento.AVISO_VERSION_ACTUAL
import com.helpi.evaluacion.consentimiento.ConsentimientoNoVigenteException
import com.helpi.evaluacion.datos.AltaSesionRepositorio
import com.helpi.evaluacion.datos.CondicionesSesion
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabaseProvider
import com.helpi.evaluacion.datos.MetadatosAltaSesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AltaSesionUiState(
    val cargando: Boolean = true,
    val consentimientoVigente: Boolean = false,
    val metadatosDisponibles: Boolean = false,
    val guardando: Boolean = false,
    val error: AltaSesionError? = null,
    val sesionCreadaId: String? = null
)

internal enum class AltaSesionError {
    METADATOS,
    SIN_CONSENTIMIENTO,
    GUARDAR
}

@Composable
fun AltaSesionRoute(onVolver: () -> Unit, onSesionCreada: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val database = remember(context) { HelpiEvaluacionDatabaseProvider.obtener(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(AltaSesionUiState()) }
    var metadatos by remember { mutableStateOf<MetadatosAltaSesion?>(null) }

    LaunchedEffect(database) {
        val cargados = cargarInicio(context, database)
        metadatos = cargados.metadatos
        state = cargados.estado
    }

    AltaSesionScreen(
        state = state,
        onVolver = onVolver,
        onCrearSesion = { codigo, condiciones ->
            val metadatosActuales = metadatos ?: return@AltaSesionScreen
            if (state.guardando || state.sesionCreadaId != null) return@AltaSesionScreen
            state = state.copy(guardando = true, error = null)
            scope.launch {
                try {
                    val sesionId = withContext(Dispatchers.IO) {
                        AltaSesionRepositorio(
                            database,
                            metadatosActuales
                        ).crear(codigo, condiciones)
                    }
                    state = state.copy(guardando = false, sesionCreadaId = sesionId)
                    onSesionCreada(sesionId)
                } catch (cancelacion: CancellationException) {
                    throw cancelacion
                } catch (_: ConsentimientoNoVigenteException) {
                    state = state.copy(
                        guardando = false,
                        consentimientoVigente = false,
                        error = AltaSesionError.SIN_CONSENTIMIENTO
                    )
                } catch (_: SQLiteException) {
                    state = state.copy(guardando = false, error = AltaSesionError.GUARDAR)
                } catch (_: IllegalArgumentException) {
                    state = state.copy(guardando = false, error = AltaSesionError.GUARDAR)
                } catch (_: IllegalStateException) {
                    state = state.copy(guardando = false, error = AltaSesionError.GUARDAR)
                }
            }
        }
    )
}

private data class ResultadoCargaAlta(
    val estado: AltaSesionUiState,
    val metadatos: MetadatosAltaSesion?
)

private suspend fun cargarInicio(
    context: Context,
    database: HelpiEvaluacionDatabase
): ResultadoCargaAlta = withContext(Dispatchers.IO) {
    val metadatos = AltaSesionMetadatosLoader.cargar(context)
    val consentimiento = database.consentimientoDao().ultimo()
    val vigente = consentimiento != null &&
        database.consentimientoDao()
            .consentimientoActualVigente(consentimiento.id, AVISO_VERSION_ACTUAL)
    val error = when {
        metadatos == null -> AltaSesionError.METADATOS
        !vigente -> AltaSesionError.SIN_CONSENTIMIENTO
        else -> null
    }
    ResultadoCargaAlta(
        estado = AltaSesionUiState(
            cargando = false,
            consentimientoVigente = vigente,
            metadatosDisponibles = metadatos != null,
            error = error
        ),
        metadatos = metadatos
    )
}

@Composable
internal fun AltaSesionScreen(
    state: AltaSesionUiState,
    onCrearSesion: (String, CondicionesSesion) -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    var codigo by remember { mutableStateOf("") }
    var formulario by remember { mutableStateOf(FormularioAltaSesion()) }
    val condiciones = formulario.condiciones()
    val codigoValido = CODIGO_PARTICIPANTE.matches(codigo)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.alta_titulo), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.alta_instrucciones))
        OutlinedTextField(
            value = codigo,
            onValueChange = { codigo = it },
            modifier = Modifier.fillMaxWidth().testTag("altaCodigoParticipante"),
            label = { Text(stringResource(R.string.alta_codigo_participante)) },
            supportingText = {
                Text(
                    stringResource(
                        if (codigo.isBlank() || codigoValido) {
                            R.string.alta_codigo_patron
                        } else {
                            R.string.alta_codigo_invalido
                        }
                    )
                )
            },
            isError = codigo.isNotBlank() && !codigoValido,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
        FormularioCondiciones(formulario = formulario, onCambio = { formulario = it })
        MensajeEstadoAlta(state)
        Button(
            onClick = { condiciones?.let { onCrearSesion(codigo, it) } },
            enabled = puedeEmpezar(state, codigoValido, condiciones != null),
            modifier = Modifier.fillMaxWidth().testTag("altaEmpezar")
        ) {
            Text(
                stringResource(
                    when {
                        state.guardando -> R.string.alta_guardando
                        state.sesionCreadaId != null -> R.string.alta_sesion_creada_boton
                        else -> R.string.alta_empezar
                    }
                )
            )
        }
        OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.aviso_volver))
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun puedeEmpezar(
    state: AltaSesionUiState,
    codigoValido: Boolean,
    condicionesCompletas: Boolean
): Boolean = !state.cargando &&
    state.consentimientoVigente &&
    state.metadatosDisponibles &&
    !state.guardando &&
    state.sesionCreadaId == null &&
    codigoValido &&
    condicionesCompletas

@Composable
private fun MensajeEstadoAlta(state: AltaSesionUiState) {
    when {
        state.cargando -> Text(stringResource(R.string.alta_cargando))
        state.error == AltaSesionError.METADATOS -> MensajeError(R.string.alta_metadatos_faltantes)
        state.error == AltaSesionError.SIN_CONSENTIMIENTO -> MensajeError(
            R.string.alta_sin_consentimiento
        )
        state.error == AltaSesionError.GUARDAR -> MensajeError(R.string.alta_error_guardar)
        state.sesionCreadaId != null -> Text(
            stringResource(R.string.alta_sesion_creada, state.sesionCreadaId),
            modifier = Modifier.testTag("altaSesionCreada")
        )
    }
}

@Composable
private fun MensajeError(texto: Int) {
    Text(stringResource(texto), color = MaterialTheme.colorScheme.error)
}

private val CODIGO_PARTICIPANTE = Regex("^P-[0-9]{3,4}$")
