package com.helpi.conversation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.helpi.conversation.lsa.SignLanguage
import com.helpi.conversation.session.SessionCoordinator
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiType

/**
 * Elección de la lengua de señas que reconoce Eva, con las limitaciones y la
 * atribución del dataset de la lengua elegida. Solo hay un modelo cargado a la vez.
 */
@Composable
internal fun SelectorDeLengua(
    state: SessionCoordinator.UiState,
    onSignLanguage: (SignLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.selectableGroup()) {
            SignLanguage.values().forEach { language ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = language == state.signLanguage,
                            onClick = { onSignLanguage(language) },
                            role = Role.RadioButton
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = language == state.signLanguage, onClick = null)
                    Text(
                        "${language.code().uppercase()} · ${language.displayName()}",
                        style = HelpiType.BodyM,
                        color = HelpiColors.LedSoft
                    )
                }
            }
        }
        Text(
            limitaciones(state.signLanguage),
            style = HelpiType.BodyS,
            color = HelpiColors.LedMuted
        )
        if (state.modelAttribution.isNotBlank()) {
            Text(
                "${state.modelAttribution} · ${state.modelLicense}",
                style = HelpiType.CaptionDisclaimer,
                color = HelpiColors.LedMuted
            )
        }
    }
}

/** Limitaciones conocidas de cada lengua; se comunican, no se disimulan. */
private fun limitaciones(language: SignLanguage): String = when (language) {
    SignLanguage.LSA ->
        "Vocabulario cerrado de 94 señas. Aprendió de personas oyentes y diestras en " +
            "laboratorio: falla con señantes zurdos. Incluye frases y voz."
    SignLanguage.ASL ->
        "Vocabulario cerrado de Google ISLR y PopSign, pensado para niños. Las glosas se " +
            "muestran y se dicen en español. Sin modo frase."
    SignLanguage.LSE ->
        "Vocabulario cerrado de 300 señas de salud (SWL-LSE). Regionalismos de España sin " +
            "validar. Sin modo frase."
}
