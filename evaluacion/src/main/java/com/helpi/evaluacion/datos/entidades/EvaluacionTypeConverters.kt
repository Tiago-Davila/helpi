package com.helpi.evaluacion.datos.entidades

import androidx.room.TypeConverter

class EvaluacionTypeConverters {
    @TypeConverter
    fun estadoSesionToString(value: EstadoSesion): String = value.name

    @TypeConverter
    fun stringToEstadoSesion(value: String): EstadoSesion = EstadoSesion.valueOf(value)

    @TypeConverter
    fun entornoToString(value: Entorno): String = value.name

    @TypeConverter
    fun stringToEntorno(value: String): Entorno = Entorno.valueOf(value)

    @TypeConverter
    fun tipoEntornoToString(value: TipoEntorno): String = value.name

    @TypeConverter
    fun stringToTipoEntorno(value: String): TipoEntorno = TipoEntorno.valueOf(value)

    @TypeConverter
    fun iluminacionToString(value: Iluminacion): String = value.name

    @TypeConverter
    fun stringToIluminacion(value: String): Iluminacion = Iluminacion.valueOf(value)

    @TypeConverter
    fun distanciaToString(value: Distancia): String = value.name

    @TypeConverter
    fun stringToDistancia(value: String): Distancia = Distancia.valueOf(value)

    @TypeConverter
    fun manoDominanteToString(value: ManoDominante): String = value.name

    @TypeConverter
    fun stringToManoDominante(value: String): ManoDominante = ManoDominante.valueOf(value)

    @TypeConverter
    fun soporteCamaraToString(value: SoporteCamara): String = value.name

    @TypeConverter
    fun stringToSoporteCamara(value: String): SoporteCamara = SoporteCamara.valueOf(value)

    @TypeConverter
    fun perfilParticipanteToString(value: PerfilParticipante): String = value.name

    @TypeConverter
    fun stringToPerfilParticipante(value: String): PerfilParticipante =
        PerfilParticipante.valueOf(value)

    @TypeConverter
    fun resultadoIntentoToString(value: ResultadoIntento): String = value.name

    @TypeConverter
    fun stringToResultadoIntento(value: String): ResultadoIntento = ResultadoIntento.valueOf(value)

    @TypeConverter
    fun causaSinResultadoToString(value: CausaSinResultado): String = value.name

    @TypeConverter
    fun stringToCausaSinResultado(value: String): CausaSinResultado =
        CausaSinResultado.valueOf(value)

    @TypeConverter
    fun causaInterrupcionToString(value: CausaInterrupcion): String = value.name

    @TypeConverter
    fun stringToCausaInterrupcion(value: String): CausaInterrupcion =
        CausaInterrupcion.valueOf(value)

    @TypeConverter
    fun estadoEnvioToString(value: EstadoEnvio): String = value.name

    @TypeConverter
    fun stringToEstadoEnvio(value: String): EstadoEnvio = EstadoEnvio.valueOf(value)
}
