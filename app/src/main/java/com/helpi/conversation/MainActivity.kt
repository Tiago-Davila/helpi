package com.helpi.conversation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.view.WindowManager

/**
 * Punto de entrada. La pantalla se protege con FLAG_SECURE porque la
 * conversación contiene datos sensibles (Ley 25.326) y no debe aparecer
 * en capturas ni miniaturas del sistema.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContent {
            MaterialTheme {
                Surface {
                    Text(text = "Helpi Conversación — en construcción")
                }
            }
        }
    }
}
