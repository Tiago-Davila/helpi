package com.helpi.evaluacion.consentimiento

import com.helpi.evaluacion.datos.ConsentimientoDao
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity

const val AVISO_VERSION_ACTUAL = "1"

class ConsentimientoNoVigenteException :
    IllegalStateException("Se requiere el consentimiento vigente para continuar")

class ConsentimientoRepositorio(private val consentimientoDao: ConsentimientoDao) {
    suspend fun obtenerVigente(video: AvisoVideoAsset?): ConsentimientoEntity? {
        val asset = video ?: return null
        if (!assetIntegro(asset)) return null
        val ultimo = consentimientoDao.ultimo() ?: return null
        return ultimo.takeIf {
            it.revocadoEn == null &&
                it.avisoVersion == AVISO_VERSION_ACTUAL &&
                it.avisoVideoSha256 == asset.sha256
        }
    }

    suspend fun exigirVigente(video: AvisoVideoAsset?): ConsentimientoEntity =
        obtenerVigente(video) ?: throw ConsentimientoNoVigenteException()

    suspend fun aceptar(video: AvisoVideoAsset, ahora: Long): ConsentimientoEntity {
        check(assetIntegro(video)) { "El video del aviso no está disponible o cambió" }
        val consentimiento = ConsentimientoEntity(
            avisoVersion = AVISO_VERSION_ACTUAL,
            avisoVideoSha256 = video.sha256,
            otorgadoEn = ahora,
            revocadoEn = null
        )
        val id = consentimientoDao.insertar(consentimiento)
        return requireNotNull(consentimientoDao.buscar(id))
    }

    private fun assetIntegro(video: AvisoVideoAsset?): Boolean = video != null &&
        video.sha256.matches(SHA256_REGEX) &&
        AvisoVideoAssetLoader.hash(video.archivo) == video.sha256

    private companion object {
        val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
    }
}
