package com.helpi.evaluacion.consentimiento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.helpi.evaluacion.R
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabaseProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("ktlint:standard:function-naming")
fun AjustesEvaluacionRoute(onVolver: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val database = remember(context) { HelpiEvaluacionDatabaseProvider.obtener(context) }
    val revocacion = remember(database) { Revocacion(database) }
    val scope = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(true) }
    var consentimientoVigente by remember { mutableStateOf(false) }
    var confirmandoRevocacion by remember { mutableStateOf(false) }
    var falloRevocacion by remember { mutableStateOf(false) }

    LaunchedEffect(database) {
        consentimientoVigente = withContext(Dispatchers.IO) {
            database.consentimientoDao().ultimo()?.let { it.revocadoEn == null } == true
        }
        cargando = false
    }

    AjustesEvaluacionScreen(
        cargando = cargando,
        consentimientoVigente = consentimientoVigente,
        falloRevocacion = falloRevocacion,
        onVolver = onVolver,
        onPedirRevocacion = { confirmandoRevocacion = true }
    )

    if (confirmandoRevocacion) {
        AlertDialog(
            onDismissRequest = { confirmandoRevocacion = false },
            title = { Text(stringResource(R.string.revocacion_confirmar_titulo)) },
            text = { Text(stringResource(R.string.revocacion_confirmar_detalle)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmandoRevocacion = false
                        scope.launch {
                            try {
                                val revocada = withContext(Dispatchers.IO) { revocacion.revocar() }
                                consentimientoVigente = !revocada &&
                                    withContext(Dispatchers.IO) {
                                        database.consentimientoDao().ultimo()
                                            ?.let { it.revocadoEn == null } == true
                                    }
                                falloRevocacion = !revocada
                            } catch (cancelacion: CancellationException) {
                                throw cancelacion
                            } catch (_: Exception) {
                                falloRevocacion = true
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.revocacion_confirmar_accion))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmandoRevocacion = false }) {
                    Text(stringResource(R.string.revocacion_cancelar))
                }
            }
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun AjustesEvaluacionScreen(
    cargando: Boolean,
    consentimientoVigente: Boolean,
    falloRevocacion: Boolean,
    onVolver: () -> Unit,
    onPedirRevocacion: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.ajustes_evaluacion_titulo),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(
                if (consentimientoVigente) {
                    R.string.ajustes_consentimiento_vigente
                } else {
                    R.string.ajustes_consentimiento_no_vigente
                }
            )
        )
        Text(
            stringResource(R.string.ajustes_revocacion_detalle),
            modifier = Modifier.padding(top = 12.dp)
        )
        if (falloRevocacion) {
            Text(
                stringResource(R.string.revocacion_fallo),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onPedirRevocacion,
            enabled = !cargando && consentimientoVigente,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.ajustes_revocar))
        }
        OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.aviso_volver))
        }
    }
}
