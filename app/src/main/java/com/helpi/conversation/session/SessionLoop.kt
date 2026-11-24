package com.helpi.conversation.session

import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Executor serial de la sesión (único escritor del estado) y su generación:
 * los callbacks tardíos de otra generación se descartan.
 */
internal class SessionLoop {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    @Volatile
    var generation = 0
        private set

    /** Reloj monotónico de la sesión. */
    fun now(): Long = SystemClock.elapsedRealtime()

    /** Encola un bloque que solo corre si la generación no cambió desde que se pidió. */
    fun submit(gen: Int = generation, block: () -> Unit) =
        enqueue { if (gen == generation) block() }

    /** Encola en el executor serial; si ya se cerró, no hace nada. */
    fun enqueue(block: () -> Unit) {
        if (executor.isShutdown) return
        try {
            executor.execute { block() }
        } catch (_: RejectedExecutionException) {
            // Callback de un sensor que terminó durante onCleared.
        }
    }

    /** Repite un bloque con un intervalo fijo mientras dure la generación actual. */
    fun every(periodMs: Long, block: () -> Unit): ScheduledFuture<*> {
        val gen = generation
        return executor.scheduleWithFixedDelay(
            { if (gen == generation) submit(gen, block) },
            periodMs,
            periodMs,
            TimeUnit.MILLISECONDS
        )
    }

    /** Invalida todo lo encolado por la generación anterior. */
    fun startNewGeneration() {
        generation++
    }

    /** Lee un valor desde el executor y espera el resultado; para tests. */
    fun <T> query(block: () -> T): T = executor.submit<T> { block() }.get()

    fun shutdown() = executor.shutdown()
}
