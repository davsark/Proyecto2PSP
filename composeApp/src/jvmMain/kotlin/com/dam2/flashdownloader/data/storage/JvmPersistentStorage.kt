package com.dam2.flashdownloader.data.storage

import com.dam2.flashdownloader.data.repository.PersistentStorage
import java.io.File

/**
 * Implementación de almacenamiento persistente para JVM usando archivos
 */
class JvmPersistentStorage(
    private val storageDir: String
) : PersistentStorage {

    private val downloadsFile = File(storageDir, "downloads.json")
    private val partialDataDir = File(storageDir, "partial")

    init {
        // Crear directorios si no existen
        File(storageDir).mkdirs()
        partialDataDir.mkdirs()
    }

    override fun saveDownloads(data: String) {
        try {
            downloadsFile.writeText(data)
        } catch (e: Exception) {
            println("Error guardando descargas: ${e.message}")
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
            println("Error cargando descargas: ${e.message}")
            null
        }
    }

    override fun savePartialData(id: String, data: String) {
        try {
            val file = File(partialDataDir, "$id.partial")
            file.writeText(data)
        } catch (e: Exception) {
            println("Error guardando datos parciales: ${e.message}")
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
            println("Error leyendo datos parciales: ${e.message}")
            null
        }
    }
}
