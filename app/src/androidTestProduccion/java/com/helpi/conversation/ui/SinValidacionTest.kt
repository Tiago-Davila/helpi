package com.helpi.conversation.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.helpi.conversation.VariantBindings
import com.helpi.conversation.session.SessionCoordinator
import com.helpi.conversation.ui.theme.HelpiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SinValidacionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun produccionNoMuestraControlDeRegistroNiDistintivo() {
        compose.setContent {
            HelpiTheme {
                VariantBindings.applicationContent(Modifier.fillMaxSize()) {
                    ConversationContent(
                        state = SessionCoordinator.UiState(),
                        actions = ConversationActions()
                    )
                }
            }
        }

        compose.onNodeWithText("Cuenta").performClick()
        compose.onNodeWithText("Validación").assertDoesNotExist()
        compose.onNodeWithTag("validationEntry").assertDoesNotExist()
        compose.onNodeWithTag("evaluationBadge").assertDoesNotExist()
        compose.onNodeWithText("EVALUACIÓN — registra lo que se seña").assertDoesNotExist()
    }
}
