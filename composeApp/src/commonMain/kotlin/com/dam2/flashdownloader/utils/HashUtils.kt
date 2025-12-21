package com.dam2.flashdownloader.utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Utilidad para calcular hashes SHA-256 de archivos
 */
object HashUtils {
    /**
     * Calcula el hash SHA-256 de un archivo
     * @param filePath Ruta completa del archivo
     * @return Hash en formato hexadecimal o null si hay error
     */
    fun calculateSHA256(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                return null
            }
            
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            
            // Convertir bytes a hexadecimal
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            println("Error calculating SHA-256: ${e.message}")
            null
        }
    }
    
    /**
     * Verifica si hay suficiente espacio en disco para un archivo
     * @param path Directorio donde se guardará el archivo
     * @param requiredBytes Bytes necesarios
     * @return true si hay suficiente espacio
     */
    fun hasEnoughDiskSpace(path: String, requiredBytes: Long): Boolean {
        return try {
            val directory = File(path)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val usableSpace = directory.usableSpace
            usableSpace >= requiredBytes
        } catch (e: Exception) {
            println("Error checking disk space: ${e.message}")
            false
        }
    }
}
