package com.helpi.evaluacion.datos

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.helpi.evaluacion.datos.entidades.CondicionesPruebaEntity
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity
import com.helpi.evaluacion.datos.entidades.EnvioEntity
import com.helpi.evaluacion.datos.entidades.EstadoDepuracionEntity
import com.helpi.evaluacion.datos.entidades.EvaluacionTypeConverters
import com.helpi.evaluacion.datos.entidades.IntentoEntity
import com.helpi.evaluacion.datos.entidades.InterrupcionEntity
import com.helpi.evaluacion.datos.entidades.ParticipanteEntity
import com.helpi.evaluacion.datos.entidades.PrediccionTopEntity
import com.helpi.evaluacion.datos.entidades.SesionEntity

@Database(
    entities = [
        ConsentimientoEntity::class,
        ParticipanteEntity::class,
        SesionEntity::class,
        CondicionesPruebaEntity::class,
        IntentoEntity::class,
        PrediccionTopEntity::class,
        InterrupcionEntity::class,
        EnvioEntity::class,
        EstadoDepuracionEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(EvaluacionTypeConverters::class)
abstract class HelpiEvaluacionDatabase : RoomDatabase() {
    abstract fun sesionDao(): SesionDao

    abstract fun intentoDao(): IntentoDao

    abstract fun consentimientoDao(): ConsentimientoDao

    abstract fun participanteDao(): ParticipanteDao

    abstract fun envioDao(): EnvioDao

    abstract fun depuracionDao(): DepuracionDao
}
