package com.dam2.flashdownloader.data.storage

import android.content.Context
import com.dam2.flashdownloader.data.repository.PersistentStorage
import java.io.File

/**
 * Implementación de almacenamiento persistente para Android
 */
class AndroidPersistentStorage(
    private val context: Context
) : PersistentStorage {

    private val downloadsFile = File(context.filesDir, "downloads.json")
    private val partialDataDir = File(context.filesDir, "partial")

    init {
        partialDataDir.mkdirs()
    }

    override fun saveDownloads(data: String) {
        try {
            downloadsFile.writeText(data)
        } catch (e: Exception) {
            android.util.Log.e("AndroidStorage", "Error guardando descargas", e)
        }
    }

    override fun loadDownloads(): String? {
        return try {
            if (downloadsFile.exists()) {
                downloadsFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AndroidStorage", "Error cargando descargas", e)
            null
        }
    }

    override fun savePartialData(id: String, data: String) {
        try {
            val file = File(partialDataDir, "$id.partial")
            file.writeText(data)
        } catch (e: Exception) {
            android.util.Log.e("AndroidStorage", "Error guardando datos parciales", e)
        }
    }

    override fun getPartialData(id: String): String? {
        return try {
            val file = File(partialDataDir, "$id.partial")
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AndroidStorage", "Error leyendo datos parciales", e)
            null
        }
    }
}
