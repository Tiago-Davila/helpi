package com.helpi.evaluacion.datos.entidades

enum class EstadoSesion {
    CREADA,
    EN_CURSO,
    PAUSADA,
    INTERRUMPIDA,
    COMPLETA,
    TERMINADA_ANTES
}

enum class Entorno {
    INTERIOR,
    EXTERIOR
}

enum class TipoEntorno {
    CASA,
    AULA,
    OFICINA,
    CALLE,
    TRANSPORTE,
    OTRO
}

enum class Iluminacion {
    BUENA,
    MEDIA,
    BAJA
}

enum class Distancia {
    MENOS_DE_1M,
    ENTRE_1_Y_2M,
    MAS_DE_2M
}

enum class ManoDominante {
    DIESTRA,
    ZURDA,
    AMBIDIESTRA
}

enum class SoporteCamara {
    TRIPODE,
    APOYADO,
    EN_MANO
}

enum class PerfilParticipante {
    SORDA_SENANTE,
    INTERPRETE_LSA,
    EQUIPO
}

enum class ResultadoIntento {
    SOBRE_UMBRAL,
    BAJO_UMBRAL,
    SIN_RESULTADO
}

enum class CausaSinResultado {
    SIN_HOMBROS,
    SEGMENTO_CORTO,
    SEGMENTO_LARGO,
    SEGUIMIENTO_PERDIDO,
    ORIENTACION_CAMBIO,
    ERROR_MODELO,
    TIEMPO_AGOTADO
}

enum class CausaInterrupcion {
    SEGUNDO_PLANO,
    PROCESO_TERMINADO,
    REVOCACION,
    PERSONA_PAUSO_DIA
}

enum class EstadoEnvio {
    PENDIENTE,
    RECHAZADO,
    BLOQUEADO
}
