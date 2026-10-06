package com.helpi.evaluacion.consentimiento

import android.content.Context
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.helpi.evaluacion.R
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabaseProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("ktlint:standard:function-naming")
fun ConsentimientoRoute(onVolver: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repositorio = remember(context) {
        ConsentimientoRepositorio(
            HelpiEvaluacionDatabaseProvider.obtener(context).consentimientoDao()
        )
    }
    val scope = rememberCoroutineScope()
    var video by remember { mutableStateOf<AvisoVideoAsset?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var consentimientoVigente by remember { mutableStateOf(false) }
    var falloAceptacion by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(repositorio) {
        val asset = withContext(Dispatchers.IO) { AvisoVideoAssetLoader.cargar(context) }
        video = asset
        consentimientoVigente = withContext(Dispatchers.IO) {
            repositorio.obtenerVigente(asset) != null
        }
        cargando = false
    }

    if (cargando) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cargando el aviso de consentimiento…")
        }
    } else {
        ConsentimientoScreen(
            video = video,
            consentimientoVigente = consentimientoVigente,
            onAceptar = {
                val asset = video ?: return@ConsentimientoScreen
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            repositorio.aceptar(asset, System.currentTimeMillis())
                        }
                        consentimientoVigente = true
                    } catch (cancelacion: CancellationException) {
                        throw cancelacion
                    } catch (_: Exception) {
                        falloAceptacion = true
                    }
                }
            },
            onVolver = onVolver,
            modifier = Modifier.padding(top = 40.dp),
            aceptacionFallida = falloAceptacion
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun ConsentimientoScreen(
    video: AvisoVideoAsset?,
    consentimientoVigente: Boolean,
    onAceptar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    aceptacionFallida: Boolean = false
) {
    var videoSolicitado by remember(video) { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
            if (video == null) {
                Text(
                    text = stringResource(R.string.aviso_video_faltante),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("consentVideoMissing")
                )
            }
            if (aceptacionFallida) {
                Text(
                    text = stringResource(R.string.aviso_aceptacion_fallo),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = onAceptar,
                enabled = video != null && !consentimientoVigente,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("consentAcceptButton")
            ) {
                Text(
                    text = stringResource(
                        if (consentimientoVigente) {
                            R.string.aviso_consentimiento_vigente
                        } else {
                            R.string.aviso_aceptar
                        }
                    )
                )
            }
            OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.aviso_volver))
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.aviso_video_titulo),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))
            if (videoSolicitado && video != null) {
                AndroidView(
                    factory = { context: Context ->
                        VideoView(context).apply {
                            setVideoPath(video.archivo.absolutePath)
                            setOnPreparedListener { reproductor -> reproductor.start() }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .testTag("consentVideoPlayer"),
                    onRelease = VideoView::stopPlayback
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.aviso_video_no_reproducido))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { videoSolicitado = true },
                enabled = video != null && !videoSolicitado,
                modifier = Modifier.testTag("consentPlayVideoButton")
            ) {
                Text(stringResource(R.string.aviso_reproducir_video))
            }
            Text(
                text = stringResource(R.string.aviso_video_atribucion),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
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
