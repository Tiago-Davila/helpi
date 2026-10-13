@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.helpi.evaluacion.ui.protocolo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.helpi.evaluacion.R

/** Placeholder until the versioned reference-video package is supplied by helpi-ml. */
@Composable
fun ReferenciaVideoPlayer(modifier: Modifier = Modifier) {
    var referenciaSolicitada by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .testTag("referenceVideoPlaceholder"),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(
                        if (referenciaSolicitada) {
                            R.string.protocolo_referencia_faltante
                        } else {
                            R.string.protocolo_referencia_no_solicitada
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        OutlinedButton(
            onClick = { referenciaSolicitada = true },
            enabled = !referenciaSolicitada,
            modifier = Modifier.testTag("referenceVideoRequestButton")
        ) {
            Text(stringResource(R.string.protocolo_ver_referencia))
        }
        Text(
            text = stringResource(R.string.protocolo_referencia_atribucion),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("referenceVideoAttribution")
        )
    }
}
