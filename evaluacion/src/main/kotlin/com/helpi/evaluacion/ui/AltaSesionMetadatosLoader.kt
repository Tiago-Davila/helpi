package com.helpi.evaluacion.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.helpi.evaluacion.datos.MetadatosAltaSesion
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONException
import org.json.JSONObject

internal object AltaSesionMetadatosLoader {
    fun cargar(context: Context): MetadatosAltaSesion? = try {
        leerMetadatos(context)
    } catch (_: IOException) {
        null
    } catch (_: JSONException) {
        null
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun leerMetadatos(context: Context): MetadatosAltaSesion {
        val manifiesto = JSONObject(
            context.assets.open(MANIFIESTO_MODELO).use { entrada ->
                entrada.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        )
        val hashModelo = hashAsset(context, MODELO_ASSET)
        val hashCatalogo = hashAsset(context, CATALOGO_ASSET)
        require(hashModelo.equals(manifiesto.getString("modelSha256"), ignoreCase = true))
        require(hashCatalogo.equals(manifiesto.getString("catalogSha256"), ignoreCase = true))
        val informacionPaquete = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            informacionPaquete.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            informacionPaquete.versionCode
        }
        return MetadatosAltaSesion(
            appVersionName = informacionPaquete.versionName.orEmpty(),
            appVersionCode = versionCode,
            modeloVersion = manifiesto.getString("modelVersion"),
            modeloSha256 = hashModelo,
            catalogoSha256 = hashCatalogo,
            contratoKeypoints = manifiesto.getInt("contractVersion"),
            fabricante = Build.MANUFACTURER,
            modeloDispositivo = Build.MODEL,
            sdkAndroid = Build.VERSION.SDK_INT
        )
    }

    private fun hashAsset(context: Context, ruta: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(ruta).use { entrada ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val cantidad = entrada.read(buffer)
                if (cantidad < 0) break
                digest.update(buffer, 0, cantidad)
            }
        }
        return digest.digest().joinToString("") {
            "%02x".format(Locale.ROOT, it.toInt() and HEX_BYTE_MASK)
        }
    }

    private const val MANIFIESTO_MODELO = "lsa/lsa-manifest.json"
    private const val MODELO_ASSET = "lsa/modelo_lsa.tflite"
    private const val CATALOGO_ASSET = "lsa/catalogo_senas.json"
    private const val HASH_BUFFER_SIZE = 8 * 1024
    private const val HEX_BYTE_MASK = 0xff
}
