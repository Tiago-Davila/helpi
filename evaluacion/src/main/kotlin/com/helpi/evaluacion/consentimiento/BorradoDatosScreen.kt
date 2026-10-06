package com.helpi.evaluacion.consentimiento

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.helpi.evaluacion.R
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabaseProvider
import com.helpi.evaluacion.datos.entidades.ParticipanteEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("ktlint:standard:function-naming")
fun BorradoDatosRoute(onVolver: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val database = remember(context) { HelpiEvaluacionDatabaseProvider.obtener(context) }
    val repositorio = remember(database) { BorradoDatos(database) }
    val scope = rememberCoroutineScope()
    var participantes by remember { mutableStateOf(emptyList<ParticipanteEntity>()) }
    var participanteSeleccionado by remember { mutableStateOf<String?>(null) }
    var resumen by remember { mutableStateOf(ResumenBorrado(0, 0, 0, emptyList())) }
    var cargando by remember { mutableStateOf(true) }
    var borradoCompletado by remember { mutableStateOf(false) }
    var falloBorrado by remember { mutableStateOf(false) }

    LaunchedEffect(database) {
        participantes = withContext(Dispatchers.IO) { repositorio.listarParticipantes() }
        resumen = withContext(Dispatchers.IO) { repositorio.previsualizar(null) }
        cargando = false
    }

    LaunchedEffect(participanteSeleccionado, cargando, borradoCompletado) {
        if (!cargando && !borradoCompletado) {
            resumen = withContext(Dispatchers.IO) {
                repositorio.previsualizar(participanteSeleccionado)
            }
        }
    }

    BorradoDatosScreen(
        participantes = participantes,
        participanteSeleccionado = participanteSeleccionado,
        resumen = resumen,
        cargando = cargando,
        borradoCompletado = borradoCompletado,
        falloBorrado = falloBorrado,
        onSeleccionar = {
            participanteSeleccionado = it
            falloBorrado = false
        },
        onConfirmarBorrado = {
            scope.launch {
                try {
                    resumen = withContext(Dispatchers.IO) {
                        repositorio.borrar(participanteSeleccionado)
                    }
                    borradoCompletado = true
                    falloBorrado = false
                } catch (cancelacion: CancellationException) {
                    throw cancelacion
                } catch (_: Exception) {
                    falloBorrado = true
                }
            }
        },
        onVolver = onVolver
    )
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun BorradoDatosScreen(
    participantes: List<ParticipanteEntity>,
    participanteSeleccionado: String?,
    resumen: ResumenBorrado,
    cargando: Boolean,
    borradoCompletado: Boolean,
    falloBorrado: Boolean,
    onSeleccionar: (String?) -> Unit,
    onConfirmarBorrado: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pedirConfirmacion by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.borrado_titulo),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        if (cargando) {
            Text(stringResource(R.string.borrado_cargando))
        } else if (borradoCompletado) {
            Text(stringResource(R.string.borrado_completado))
            MostrarResumen(resumen)
            MostrarCopiasCompartidas(resumen.sesionesYaCompartidas)
            OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.borrado_hecho))
            }
        } else {
            Text(stringResource(R.string.borrado_instrucciones))
            SelectorBorrado(
                participantes = participantes,
                participanteSeleccionado = participanteSeleccionado,
                onSeleccionar = onSeleccionar
            )
            MostrarResumen(resumen)
            MostrarCopiasCompartidas(resumen.sesionesYaCompartidas)
            if (falloBorrado) {
                Text(
                    stringResource(R.string.borrado_fallo),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = { pedirConfirmacion = true },
                enabled = !cargando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.borrado_boton))
            }
            OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.aviso_volver))
            }
        }
    }

    if (pedirConfirmacion) {
        AlertDialog(
            onDismissRequest = { pedirConfirmacion = false },
            title = { Text(stringResource(R.string.borrado_confirmar_titulo)) },
            text = { Text(stringResource(R.string.borrado_confirmar_detalle)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pedirConfirmacion = false
                        onConfirmarBorrado()
                    }
                ) {
                    Text(stringResource(R.string.borrado_confirmar_accion))
                }
            },
            dismissButton = {
                TextButton(onClick = { pedirConfirmacion = false }) {
                    Text(stringResource(R.string.revocacion_cancelar))
                }
            }
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun SelectorBorrado(
    participantes: List<ParticipanteEntity>,
    participanteSeleccionado: String?,
    onSeleccionar: (String?) -> Unit
) {
    Text(stringResource(R.string.borrado_elegir))
    OpcionBorrado(
        texto = stringResource(R.string.borrado_todas),
        seleccionada = participanteSeleccionado == null,
        onSeleccionar = { onSeleccionar(null) }
    )
    participantes.forEach { participante ->
        OpcionBorrado(
            texto = stringResource(R.string.borrado_participante, participante.codigo),
            seleccionada = participanteSeleccionado == participante.codigo,
            onSeleccionar = { onSeleccionar(participante.codigo) }
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun OpcionBorrado(texto: String, seleccionada: Boolean, onSeleccionar: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSeleccionar),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = seleccionada, onClick = onSeleccionar)
        Text(texto)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun MostrarResumen(resumen: ResumenBorrado) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            pluralStringResource(
                R.plurals.borrado_sesiones,
                resumen.sesiones,
                resumen.sesiones
            ),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            pluralStringResource(R.plurals.borrado_intentos, resumen.intentos, resumen.intentos)
        )
        Text(
            pluralStringResource(
                R.plurals.borrado_envios,
                resumen.enviosPendientes,
                resumen.enviosPendientes
            )
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun MostrarCopiasCompartidas(sesiones: List<String>) {
    if (sesiones.isEmpty()) {
        Text(stringResource(R.string.borrado_sin_copias))
    } else {
        Text(stringResource(R.string.borrado_copias, sesiones.joinToString(separator = "\n")))
        Text(stringResource(R.string.borrado_solicitud_supresion))
    }
}
