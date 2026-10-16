package com.helpi.evaluacion.registro

/** Puertos de escritura serial para intentos del protocolo. */
interface EscritorIntentos {
    fun trySend(registro: RegistroIntento): Boolean

    fun marcarLoHiceMal(sesionId: String, posicion: Int, numeroIntento: Int): Boolean
}
