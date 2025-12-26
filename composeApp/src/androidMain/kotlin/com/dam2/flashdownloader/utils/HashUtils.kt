package com.dam2.flashdownloader.utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * ✅ Utilidad mejorada para hashes y verificación de espacio (Android)
 */
actual object HashUtils {
    /**
     * Calcula SHA-256 con buffer grande para archivos grandes
     */
    actual fun calculateSHA256(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                return null
            }
            
            val digest = MessageDigest.getInstance("SHA-256")
            // ✅ Buffer de 64KB (estándar industria)
            val buffer = ByteArray(64 * 1024)
            
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            println("Error calculating SHA-256: ${e.message}")
            null
        }
    }
    
    /**
     * ✅ FIX: Verificación de espacio mejorada con margen de seguridad
     */
    actual fun hasEnoughDiskSpace(path: String, requiredBytes: Long): Boolean {
        return try {
            val directory = File(path)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            
            // ✅ Usar getFreeSpace() en lugar de usableSpace
            val freeSpace = directory.freeSpace
            
            // ✅ Añadir 10% de margen de seguridad
            val safetyMargin = (requiredBytes * 0.1).toLong()
            val totalRequired = requiredBytes + safetyMargin
            
            val hasSpace = freeSpace >= totalRequired
            
            if (!hasSpace) {
                val freeGB = freeSpace / (1024.0 * 1024.0 * 1024.0)
                val requiredGB = totalRequired / (1024.0 * 1024.0 * 1024.0)
                println("Insufficient space: Free=${String.format("%.2f", freeGB)}GB, Required=${String.format("%.2f", requiredGB)}GB")
            }
            
            hasSpace
        } catch (e: Exception) {
            println("Error checking disk space: ${e.message}")
            // ✅ En caso de error, asumir que hay espacio (evitar bloqueo)
            true
        }
    }
    
    /**
     * ✅ NUEVO: Verifica si hay espacio sin lanzar excepciones
     */
    actual fun getAvailableSpace(path: String): Long {
        return try {
            val directory = File(path)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            directory.freeSpace
        } catch (e: Exception) {
            println("Error getting available space: ${e.message}")
            Long.MAX_VALUE // Retornar valor grande para evitar bloqueos
        }
    }
}
