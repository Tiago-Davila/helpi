package com.helpi.conversation

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.helpi.conversation.observation.RecognitionObserver
import com.helpi.conversation.session.SessionCoordinator

/** Production wiring keeps recognition and delivery behavior unchanged. */
@Suppress("ComposableNaming")
object VariantBindings {
    val recognitionObserver: RecognitionObserver = RecognitionObserver.NO_OP
    val deliveryMode = SessionCoordinator.DeliveryMode.CONVERSATION

    @Composable
    fun applicationContent(modifier: Modifier, content: @Composable () -> Unit) {
        Box(modifier) { content() }
    }

    @Composable
    @Suppress("UnusedParameter")
    fun applicationContent(
        modifier: Modifier,
        sessionCoordinator: SessionCoordinator,
        cameraPreview: @Composable () -> Unit,
        onStartRecognition: () -> Unit,
        content: @Composable () -> Unit
    ) {
        Box(modifier) { content() }
    }

    @Composable
    fun evaluationBadge(modifier: Modifier = Modifier) = Unit

    @Composable
    fun validationMenuEntry() {
        Spacer(Modifier.height(24.dp))
    }

    fun createSessionCoordinator(context: Context) =
        SessionCoordinator(context, recognitionObserver, deliveryMode)
}
