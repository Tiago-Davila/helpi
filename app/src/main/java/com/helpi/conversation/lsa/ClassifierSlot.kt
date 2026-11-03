package com.helpi.conversation.lsa

import java.io.Closeable

/**
 * Único clasificador cargado en memoria. Reemplazarlo cierra el anterior con
 * [closeLater] (el executor de LiteRT, para no cerrar un intérprete en uso) y
 * cambia [epoch], de modo que un resultado lanzado antes del cambio se
 * reconozca como ajeno y se descarte.
 */
class ClassifierSlot<T : Closeable>(private val closeLater: (Runnable) -> Unit) {

    var current: T? = null
        private set

    var epoch: Int = 0
        private set

    /** Cierra el clasificador anterior y deja [next] (o ninguno) como el único cargado. */
    fun replace(next: T?) {
        current?.let { closing -> closeLater(Runnable { closing.close() }) }
        current = next
        epoch++
    }
}
