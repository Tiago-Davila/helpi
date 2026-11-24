package com.helpi.evaluacion.consentimiento

import com.helpi.evaluacion.datos.ConsentimientoDao
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity

/** La versión 2 es el aviso solo en texto; la 1 exigía además un video en LSA. */
const val AVISO_VERSION_ACTUAL = "2"

/** SHA-256 de cero bytes: deja constancia de que se aceptó sin que se mostrara un video. */
const val AVISO_SIN_VIDEO_SHA256 =
    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

class ConsentimientoNoVigenteException :
    IllegalStateException("Se requiere el consentimiento vigente para continuar")

class ConsentimientoRepositorio(private val consentimientoDao: ConsentimientoDao) {
    suspend fun obtenerVigente(): ConsentimientoEntity? = consentimientoDao.ultimo()?.takeIf {
        it.revocadoEn == null && it.avisoVersion == AVISO_VERSION_ACTUAL
    }

    suspend fun exigirVigente(): ConsentimientoEntity =
        obtenerVigente() ?: throw ConsentimientoNoVigenteException()

    suspend fun aceptar(ahora: Long): ConsentimientoEntity {
        val consentimiento = ConsentimientoEntity(
            avisoVersion = AVISO_VERSION_ACTUAL,
            avisoVideoSha256 = AVISO_SIN_VIDEO_SHA256,
            otorgadoEn = ahora,
            revocadoEn = null
        )
        val id = consentimientoDao.insertar(consentimiento)
        return requireNotNull(consentimientoDao.buscar(id))
    }
}
