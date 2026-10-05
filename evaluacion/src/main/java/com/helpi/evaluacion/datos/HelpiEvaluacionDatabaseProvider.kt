package com.helpi.evaluacion.datos

import android.content.Context
import androidx.room.Room

object HelpiEvaluacionDatabaseProvider {
    const val DATABASE_NAME = "helpi_evaluacion.db"

    @Volatile
    private var instancia: HelpiEvaluacionDatabase? = null

    fun obtener(context: Context): HelpiEvaluacionDatabase = instancia ?: synchronized(this) {
        instancia ?: Room.databaseBuilder(
            context.applicationContext,
            HelpiEvaluacionDatabase::class.java,
            DATABASE_NAME
        ).build().also { instancia = it }
    }
}
