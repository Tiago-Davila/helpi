@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.helpi.evaluacion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.helpi.evaluacion.R
import com.helpi.evaluacion.datos.CondicionesSesion
import com.helpi.evaluacion.datos.entidades.Distancia
import com.helpi.evaluacion.datos.entidades.Entorno
import com.helpi.evaluacion.datos.entidades.Iluminacion
import com.helpi.evaluacion.datos.entidades.ManoDominante
import com.helpi.evaluacion.datos.entidades.PerfilParticipante
import com.helpi.evaluacion.datos.entidades.SoporteCamara
import com.helpi.evaluacion.datos.entidades.TipoEntorno

internal data class FormularioAltaSesion(
    val entorno: Entorno? = null,
    val tipoEntorno: TipoEntorno? = null,
    val iluminacion: Iluminacion? = null,
    val contraluz: Boolean? = null,
    val distancia: Distancia? = null,
    val manoDominante: ManoDominante? = null,
    val guantes: Boolean? = null,
    val soporteCamara: SoporteCamara? = null,
    val perfilParticipante: PerfilParticipante? = null
) {
    fun condiciones(): CondicionesSesion? = if (valores().any { it == null }) {
        null
    } else {
        CondicionesSesion(
            requireNotNull(entorno),
            requireNotNull(tipoEntorno),
            requireNotNull(iluminacion),
            requireNotNull(contraluz),
            requireNotNull(distancia),
            requireNotNull(manoDominante),
            requireNotNull(guantes),
            requireNotNull(soporteCamara),
            requireNotNull(perfilParticipante)
        )
    }

    private fun valores(): List<Any?> = listOf(
        entorno,
        tipoEntorno,
        iluminacion,
        contraluz,
        distancia,
        manoDominante,
        guantes,
        soporteCamara,
        perfilParticipante
    )
}

private data class Opcion<T>(val valor: T, val etiqueta: Int)

@Composable
internal fun FormularioCondiciones(
    formulario: FormularioAltaSesion,
    onCambio: (FormularioAltaSesion) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectoresEntorno(formulario, onCambio)
        SelectoresCondicionesCaptura(formulario, onCambio)
        SelectoresParticipante(formulario, onCambio)
        SelectorPerfil(formulario, onCambio)
    }
}

@Composable
private fun SelectoresEntorno(
    formulario: FormularioAltaSesion,
    onCambio: (FormularioAltaSesion) -> Unit
) {
    Selector(
        campo = "entorno",
        titulo = R.string.alta_entorno,
        opciones = listOf(
            Opcion(Entorno.INTERIOR, R.string.alta_interior),
            Opcion(Entorno.EXTERIOR, R.string.alta_exterior)
        ),
        seleccionado = formulario.entorno,
        onSeleccionar = { onCambio(formulario.copy(entorno = it)) }
    )
    Selector(
        campo = "tipoEntorno",
        titulo = R.string.alta_tipo_entorno,
        opciones = listOf(
            Opcion(TipoEntorno.CASA, R.string.alta_casa),
            Opcion(TipoEntorno.AULA, R.string.alta_aula),
            Opcion(TipoEntorno.OFICINA, R.string.alta_oficina),
            Opcion(TipoEntorno.CALLE, R.string.alta_calle),
            Opcion(TipoEntorno.TRANSPORTE, R.string.alta_transporte),
            Opcion(TipoEntorno.OTRO, R.string.alta_otro)
        ),
        seleccionado = formulario.tipoEntorno,
        onSeleccionar = { onCambio(formulario.copy(tipoEntorno = it)) }
    )
}

@Composable
private fun SelectoresCondicionesCaptura(
    formulario: FormularioAltaSesion,
    onCambio: (FormularioAltaSesion) -> Unit
) {
    Selector(
        campo = "iluminacion",
        titulo = R.string.alta_iluminacion,
        opciones = listOf(
            Opcion(Iluminacion.BUENA, R.string.alta_buena),
            Opcion(Iluminacion.MEDIA, R.string.alta_media),
            Opcion(Iluminacion.BAJA, R.string.alta_baja)
        ),
        seleccionado = formulario.iluminacion,
        onSeleccionar = { onCambio(formulario.copy(iluminacion = it)) }
    )
    Selector(
        campo = "contraluz",
        titulo = R.string.alta_contraluz,
        opciones = opcionesBooleanas(),
        seleccionado = formulario.contraluz,
        onSeleccionar = { onCambio(formulario.copy(contraluz = it)) }
    )
    Selector(
        campo = "distancia",
        titulo = R.string.alta_distancia,
        opciones = listOf(
            Opcion(Distancia.MENOS_DE_1M, R.string.alta_menos_1m),
            Opcion(Distancia.ENTRE_1_Y_2M, R.string.alta_1_a_2m),
            Opcion(Distancia.MAS_DE_2M, R.string.alta_mas_2m)
        ),
        seleccionado = formulario.distancia,
        onSeleccionar = { onCambio(formulario.copy(distancia = it)) }
    )
}

@Composable
private fun SelectoresParticipante(
    formulario: FormularioAltaSesion,
    onCambio: (FormularioAltaSesion) -> Unit
) {
    Selector(
        campo = "manoDominante",
        titulo = R.string.alta_mano_dominante,
        opciones = listOf(
            Opcion(ManoDominante.DIESTRA, R.string.alta_diestra),
            Opcion(ManoDominante.ZURDA, R.string.alta_zurda),
            Opcion(ManoDominante.AMBIDIESTRA, R.string.alta_ambidiestra)
        ),
        seleccionado = formulario.manoDominante,
        onSeleccionar = { onCambio(formulario.copy(manoDominante = it)) }
    )
    Selector(
        campo = "guantes",
        titulo = R.string.alta_guantes,
        opciones = opcionesBooleanas(),
        seleccionado = formulario.guantes,
        onSeleccionar = { onCambio(formulario.copy(guantes = it)) }
    )
    Selector(
        campo = "soporteCamara",
        titulo = R.string.alta_soporte,
        opciones = listOf(
            Opcion(SoporteCamara.TRIPODE, R.string.alta_tripode),
            Opcion(SoporteCamara.APOYADO, R.string.alta_apoyado),
            Opcion(SoporteCamara.EN_MANO, R.string.alta_en_mano)
        ),
        seleccionado = formulario.soporteCamara,
        onSeleccionar = { onCambio(formulario.copy(soporteCamara = it)) }
    )
}

@Composable
private fun SelectorPerfil(
    formulario: FormularioAltaSesion,
    onCambio: (FormularioAltaSesion) -> Unit
) {
    Selector(
        campo = "perfilParticipante",
        titulo = R.string.alta_perfil,
        opciones = listOf(
            Opcion(PerfilParticipante.SORDA_SENANTE, R.string.alta_sorda_senante),
            Opcion(PerfilParticipante.INTERPRETE_LSA, R.string.alta_interprete),
            Opcion(PerfilParticipante.EQUIPO, R.string.alta_equipo)
        ),
        seleccionado = formulario.perfilParticipante,
        onSeleccionar = { onCambio(formulario.copy(perfilParticipante = it)) }
    )
}

@Composable
private fun opcionesBooleanas(): List<Opcion<Boolean>> = listOf(
    Opcion(true, R.string.alta_si),
    Opcion(false, R.string.alta_no)
)

@Composable
private fun <T> Selector(
    campo: String,
    titulo: Int,
    opciones: List<Opcion<T>>,
    seleccionado: T?,
    onSeleccionar: (T) -> Unit
) {
    var expandido by remember(campo) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(titulo), style = MaterialTheme.typography.labelLarge)
        Button(
            onClick = { expandido = true },
            modifier = Modifier.fillMaxWidth().testTag("condicion-$campo")
        ) {
            val etiqueta = opciones.firstOrNull { it.valor == seleccionado }?.etiqueta
            Text(stringResource(etiqueta ?: R.string.alta_seleccionar))
        }
        DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            opciones.forEachIndexed { indice, opcion ->
                DropdownMenuItem(
                    text = { Text(stringResource(opcion.etiqueta)) },
                    onClick = {
                        onSeleccionar(opcion.valor)
                        expandido = false
                    },
                    modifier = Modifier.testTag("opcion-$campo-$indice")
                )
            }
        }
    }
}
