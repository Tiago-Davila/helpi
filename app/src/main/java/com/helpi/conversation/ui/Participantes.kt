package com.helpi.conversation.ui

/**
 * Cuenta de demostración. El repositorio todavía no tiene inicio de sesión:
 * este nombre es un marcador de posición explícito, no un usuario real.
 */
internal const val CUENTA_DEMO = "Tiago"

/**
 * Cómo se rotula a la otra persona cuando no dio su nombre. Se dice en
 * palabras completas y en las dos terminaciones porque el rótulo aparece
 * arriba de cada mensaje suyo durante toda la conversación.
 */
internal const val SIN_NOMBRE = "Invitada o invitado"

/**
 * Quiénes están conversando.
 *
 * El teléfono es de la persona oyente: lo que entra por el micrófono es su
 * voz. La persona sorda es la que hace las señas y, cuando el reconocimiento
 * no alcanza, la que escribe. Por eso su nombre se pregunta al empezar y el
 * de la cuenta ya está.
 *
 * Los nombres viven en memoria del proceso y nunca se escriben en disco: la
 * conversación tampoco se guarda, y el nombre de alguien que pidió ayuda para
 * comunicarse no es un dato que esta aplicación tenga por qué conservar.
 */
internal data class Participantes(
    val cuenta: String = CUENTA_DEMO,
    val interlocutor: String = SIN_NOMBRE,
    /** La pantalla de inicio debe abrir sola el pedido de nombre. */
    val solicitarNombre: Boolean = false,
)
