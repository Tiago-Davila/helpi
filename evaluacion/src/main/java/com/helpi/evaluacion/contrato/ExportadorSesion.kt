package com.helpi.evaluacion.contrato

import androidx.room.withTransaction
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.SesionDocumentoMapper
import java.io.File
import java.time.Instant
import java.util.UUID

class ExportadorSesion(
    private val database: HelpiEvaluacionDatabase,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val mapper: SesionDocumentoMapper = SesionDocumentoMapper(database),
    private val serializer: SesionContratoSerializer = SesionContratoSerializer()
) {
    suspend fun exportar(sesionId: String, directorio: File): File {
        val exportadoEn = reloj()
        val temporal = File(directorio, "$sesionId.tmp")
        var archivoFinal: File? = null

        var exportado = false
        try {
            val resultado = database.withTransaction {
                check(directorio.exists() || directorio.mkdirs()) {
                    "No se pudo crear el directorio de exportaciones"
                }
                val documento = mapper.mapear(
                    sesionId = sesionId,
                    envioId = UUID.randomUUID(),
                    generadoEn = Instant.ofEpochMilli(exportadoEn)
                )
                val revision = documento.envio.revision
                val destino = File(directorio, "sesion-$sesionId-r$revision.json")
                temporal.writeBytes(serializer.serialize(documento))
                check(temporal.renameTo(destino)) { "No se pudo finalizar el archivo exportado" }
                archivoFinal = destino
                check(
                    database.sesionDao().actualizarExportacion(
                        sesionId = sesionId,
                        revision = revision,
                        exportadoEn = exportadoEn
                    ) == 1
                ) { "La revisión de la sesión cambió durante la exportación" }
                destino
            }
            exportado = true
            return resultado
        } finally {
            if (!exportado) {
                temporal.delete()
                archivoFinal?.delete()
            }
        }
    }
}
