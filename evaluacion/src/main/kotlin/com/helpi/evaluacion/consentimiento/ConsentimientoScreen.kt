package com.helpi.evaluacion.consentimiento

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helpi.evaluacion.R
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabaseProvider
import com.helpi.evaluacion.ui.ANCHO_MAXIMO_CONTENIDO
import com.helpi.evaluacion.ui.esApaisado
import com.helpi.evaluacion.ui.margenLateral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ancho mínimo desde el cual el aviso se reparte en dos columnas en horizontal. */
private val ANCHO_DOS_COLUMNAS = 600.dp

@Composable
@Suppress("ktlint:standard:function-naming")
fun ConsentimientoRoute(
    onVolver: () -> Unit,
    onAjustes: () -> Unit = {},
    onContinuar: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val repositorio = remember(context) {
        ConsentimientoRepositorio(
            HelpiEvaluacionDatabaseProvider.obtener(context).consentimientoDao()
        )
    }
    val scope = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(true) }
    var consentimientoVigente by remember { mutableStateOf(false) }
    var falloAceptacion by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(repositorio) {
        consentimientoVigente = withContext(Dispatchers.IO) {
            repositorio.obtenerVigente() != null
        }
        // El aviso se acepta una sola vez: con un consentimiento vigente no se
        // vuelve a mostrar. Para revisarlo o revocarlo están los ajustes.
        if (consentimientoVigente) onContinuar() else cargando = false
    }

    if (cargando) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cargando el aviso de consentimiento…")
        }
    } else {
        ConsentimientoScreen(
            consentimientoVigente = consentimientoVigente,
            onAceptar = {
                if (consentimientoVigente) {
                    onContinuar()
                } else {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                repositorio.aceptar(System.currentTimeMillis())
                            }
                            consentimientoVigente = true
                            onContinuar()
                        } catch (cancelacion: CancellationException) {
                            throw cancelacion
                        } catch (_: Exception) {
                            falloAceptacion = true
                        }
                    }
                }
            },
            onVolver = onVolver,
            onAjustes = onAjustes,
            aceptacionFallida = falloAceptacion
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun ConsentimientoScreen(
    consentimientoVigente: Boolean,
    onAceptar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    aceptacionFallida: Boolean = false,
    onAjustes: () -> Unit = {}
) {
    val contenido = ContenidoAviso(
        consentimientoVigente = consentimientoVigente,
        aceptacionFallida = aceptacionFallida,
        onAceptar = onAceptar,
        onVolver = onVolver,
        onAjustes = onAjustes
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        val margen = maxWidth.margenLateral()
        if (esApaisado(maxWidth, maxHeight) && maxWidth >= ANCHO_DOS_COLUMNAS) {
            AvisoEnDosColumnas(contenido, margen)
        } else {
            AvisoEnUnaColumna(contenido, margen)
        }
    }
}

/** Lo que muestra el aviso y cómo responde, agrupado para pasarlo a cada disposición. */
private class ContenidoAviso(
    val consentimientoVigente: Boolean,
    val aceptacionFallida: Boolean,
    val onAceptar: () -> Unit,
    val onVolver: () -> Unit,
    val onAjustes: () -> Unit
)

/** Horizontal con ancho suficiente: el texto a un lado, los botones al otro. */
@Composable
@Suppress("ktlint:standard:function-naming")
private fun AvisoEnDosColumnas(contenido: ContenidoAviso, margen: Dp) {
    Row(
        modifier = Modifier.fillMaxSize().padding(margen),
        horizontalArrangement = Arrangement.spacedBy(margen)
    ) {
        AvisoTexto(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        )
        AvisoBotones(
            contenido,
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        )
    }
}

/** Vertical o ancho chico: el texto se desplaza y los botones quedan siempre a la vista. */
@Composable
@Suppress("ktlint:standard:function-naming")
private fun AvisoEnUnaColumna(contenido: ContenidoAviso, margen: Dp) {
    Column(
        modifier = Modifier
            .widthIn(max = ANCHO_MAXIMO_CONTENIDO)
            .fillMaxSize()
            .padding(margen),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AvisoTexto(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        )
        AvisoBotones(contenido, Modifier.fillMaxWidth())
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun AvisoTexto(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.aviso_titulo),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        AvisoParrafo(R.string.aviso_que_se_registra)
        AvisoParrafo(R.string.aviso_que_no_se_registra)
        AvisoParrafo(R.string.aviso_para_que)
        AvisoParrafo(R.string.aviso_donde_queda)
        AvisoParrafo(R.string.aviso_retencion)
        AvisoParrafo(R.string.aviso_quien_accede)
        AvisoParrafo(R.string.aviso_a_quienes_cubre)
        AvisoParrafo(R.string.aviso_revocar_y_borrar)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun AvisoBotones(contenido: ContenidoAviso, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (contenido.aceptacionFallida) {
            Text(
                text = stringResource(R.string.aviso_aceptacion_fallo),
                color = MaterialTheme.colorScheme.error
            )
        }
        Button(
            onClick = contenido.onAceptar,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("consentAcceptButton")
        ) {
            Text(
                text = stringResource(
                    if (contenido.consentimientoVigente) {
                        R.string.aviso_continuar
                    } else {
                        R.string.aviso_aceptar
                    }
                )
            )
        }
        OutlinedButton(onClick = contenido.onVolver, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.aviso_volver))
        }
        OutlinedButton(onClick = contenido.onAjustes, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ajustes_evaluacion_abrir))
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun AvisoParrafo(textResource: Int) {
    Text(
        text = stringResource(textResource),
        style = MaterialTheme.typography.bodyLarge
    )
}
