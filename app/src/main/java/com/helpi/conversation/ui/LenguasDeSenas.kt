package com.helpi.conversation.ui

import android.content.Context
import com.helpi.conversation.lsa.ModelBundle
import com.helpi.conversation.lsa.SignLanguage

/**
 * Lo que la persona necesita saber de una lengua antes de elegirla: qué
 * vocabulario reconoce y en qué condiciones se grabó. Los límites se dicen
 * acá y no en la documentación, porque condicionan cómo se usa Helpi.
 */
internal data class DescripcionDeLengua(
    val sigla: String,
    val pais: String,
    val vocabulario: String,
    val limitaciones: String,
    /** Dataset y licencia para cuando la lengua todavía no trae su manifiesto. */
    val datasetSinModelo: CreditoDeDataset
) {
    val nombre: String get() = "$sigla · $pais"
}

/** Atribución de un dataset tal como la declara el manifiesto de su modelo. */
internal data class CreditoDeDataset(val atribucion: String, val licencia: String)

/** Crédito de una lengua y si su modelo está instalado en esta versión de la app. */
internal data class CreditoDeLengua(
    val lengua: SignLanguage,
    val credito: CreditoDeDataset,
    val modeloInstalado: Boolean
)

/** Descripción fija de cada lengua; el vocabulario sale de cómo se entrenó cada modelo. */
internal fun descripcion(lengua: SignLanguage): DescripcionDeLengua = when (lengua) {
    SignLanguage.LSA -> DescripcionDeLengua(
        sigla = "LSA",
        pais = "Argentina",
        vocabulario = "94 señas aisladas de lengua de señas argentina (incluye hoy, ayer, " +
            "mañana y el abecedario).",
        limitaciones = "Aprendió de personas oyentes y diestras grabadas en un laboratorio: " +
            "falla de forma sistemática con señantes zurdos y puede comportarse distinto " +
            "fuera de esas condiciones.",
        datasetSinModelo = CreditoDeDataset(
            "LSA64: Ronchetti et al., LIDI, Universidad Nacional de La Plata.",
            "CC BY-NC-SA 4.0"
        )
    )
    SignLanguage.ASL -> DescripcionDeLengua(
        sigla = "ASL",
        pais = "Estados Unidos",
        vocabulario = "250 señas aisladas del vocabulario de un juego infantil (PopSign).",
        limitaciones = "Es vocabulario de un juego, grabado con celular: no fue pensado para " +
            "una conversación ni para la atención en salud.",
        datasetSinModelo = CreditoDeDataset(
            "Google Isolated Sign Language Recognition (PopSign ASL).",
            "CC BY 4.0"
        )
    )
    SignLanguage.LSE -> DescripcionDeLengua(
        sigla = "LSE",
        pais = "España",
        vocabulario = "299 señas aisladas de lengua de signos española del ámbito de la salud " +
            "(SWL-LSE).",
        limitaciones = "Lo grabaron pocas personas y el texto de las señas todavía no lo " +
            "revisaron señantes de LSE. Exige menos confianza que LSA para comunicar una seña.",
        datasetSinModelo = CreditoDeDataset(
            "SWL-LSE: SignaMed. Zenodo, registro 13691887.",
            "CC BY 4.0"
        )
    )
}

/**
 * Créditos de las tres lenguas. El texto sale del manifiesto de cada modelo;
 * una lengua sin modelo instalado conserva su atribución igual, porque la
 * interfaz la nombra entre las opciones.
 */
internal fun creditosDeLenguas(
    leerManifiesto: (SignLanguage) -> ModelBundle.Manifest?
): List<CreditoDeLengua> = SignLanguage.values().map { lengua ->
    val manifiesto = leerManifiesto(lengua)
    CreditoDeLengua(
        lengua = lengua,
        credito = manifiesto?.let { CreditoDeDataset(it.attribution, it.license) }
            ?: descripcion(lengua).datasetSinModelo,
        modeloInstalado = manifiesto != null
    )
}

/** Créditos leyendo los manifiestos empaquetados en la app. */
internal fun creditosDeLenguas(context: Context): List<CreditoDeLengua> =
    creditosDeLenguas { ModelBundle.readManifest(context, it) }
