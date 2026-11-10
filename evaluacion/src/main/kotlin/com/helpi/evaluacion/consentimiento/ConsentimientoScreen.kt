package com.helpi.evaluacion.consentimiento

import android.content.Context
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

/** Proporción del video de aviso. */
private const val PROPORCION_VIDEO = 16f / 9f

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
            video = video,
            consentimientoVigente = consentimientoVigente,
            onAceptar = {
                if (consentimientoVigente) {
                    onContinuar()
                } else {
                    video?.let { asset ->
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    repositorio.aceptar(asset, System.currentTimeMillis())
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
    video: AvisoVideoAsset?,
    consentimientoVigente: Boolean,
    onAceptar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    aceptacionFallida: Boolean = false,
    onAjustes: () -> Unit = {}
) {
    var videoSolicitado by remember(video) { mutableStateOf(false) }
    val contenido = ContenidoAviso(
        video = video,
        videoSolicitado = videoSolicitado,
        consentimientoVigente = consentimientoVigente,
        aceptacionFallida = aceptacionFallida,
        onReproducir = { videoSolicitado = true },
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
@Suppress("LongParameterList")
private class ContenidoAviso(
    val video: AvisoVideoAsset?,
    val videoSolicitado: Boolean,
    val consentimientoVigente: Boolean,
    val aceptacionFallida: Boolean,
    val onReproducir: () -> Unit,
    val onAceptar: () -> Unit,
    val onVolver: () -> Unit,
    val onAjustes: () -> Unit
) {
    val puedeAceptar: Boolean get() = consentimientoVigente || video != null
}

/** Horizontal con ancho suficiente: texto y botones a un lado, el video al otro. */
@Composable
@Suppress("ktlint:standard:function-naming")
private fun AvisoEnDosColumnas(contenido: ContenidoAviso, margen: Dp) {
    Row(
        modifier = Modifier.fillMaxSize().padding(margen),
        horizontalArrangement = Arrangement.spacedBy(margen)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvisoTexto(contenido.video, contenido.aceptacionFallida)
            AvisoBotones(contenido)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            AvisoVideo(contenido.video, contenido.videoSolicitado, contenido.onReproducir)
        }
    }
}

/** Vertical o ancho chico: el texto, después el video y al final los botones. */
@Composable
@Suppress("ktlint:standard:function-naming")
private fun AvisoEnUnaColumna(contenido: ContenidoAviso, margen: Dp) {
    Column(
        modifier = Modifier
            .widthIn(max = ANCHO_MAXIMO_CONTENIDO)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(margen),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AvisoTexto(contenido.video, contenido.aceptacionFallida)
        AvisoVideo(contenido.video, contenido.videoSolicitado, contenido.onReproducir)
        AvisoBotones(contenido)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ColumnScope.AvisoTexto(video: AvisoVideoAsset?, aceptacionFallida: Boolean) {
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
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ColumnScope.AvisoBotones(contenido: ContenidoAviso) {
    Button(
        onClick = contenido.onAceptar,
        enabled = contenido.puedeAceptar,
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

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ColumnScope.AvisoVideo(
    video: AvisoVideoAsset?,
    videoSolicitado: Boolean,
    onReproducir: () -> Unit
) {
    Text(
        text = stringResource(R.string.aviso_video_titulo),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
    Spacer(Modifier.height(12.dp))
    // El video ocupa el ancho de su columna y conserva la proporción 16:9.
    val marco = Modifier
        .fillMaxWidth()
        .aspectRatio(PROPORCION_VIDEO)
    if (videoSolicitado && video != null) {
        AndroidView(
            factory = { context: Context ->
                VideoView(context).apply {
                    setVideoPath(video.archivo.absolutePath)
                    setOnPreparedListener { reproductor -> reproductor.start() }
                }
            },
            modifier = marco.testTag("consentVideoPlayer"),
            onRelease = VideoView::stopPlayback
        )
    } else {
        Box(
            modifier = marco.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.aviso_video_no_reproducido),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onReproducir,
        enabled = video != null && !videoSolicitado,
        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("consentPlayVideoButton")
    ) {
        Text(stringResource(R.string.aviso_reproducir_video))
    }
    Text(
        text = stringResource(R.string.aviso_video_atribucion),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun AvisoParrafo(textResource: Int) {
    Text(
        text = stringResource(textResource),
        style = MaterialTheme.typography.bodyLarge
    )
}
