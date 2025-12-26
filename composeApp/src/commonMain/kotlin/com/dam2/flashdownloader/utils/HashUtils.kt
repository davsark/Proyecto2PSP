package com.dam2.flashdownloader.utils

/**
 * Expect declaration for HashUtils
 * Platform-specific implementations in androidMain and jvmMain
 */
expect object HashUtils {
    /**
     * Calcula SHA-256 del archivo
     */
    fun calculateSHA256(filePath: String): String?
    
    /**
     * Verifica si hay suficiente espacio en disco
     */
    fun hasEnoughDiskSpace(path: String, requiredBytes: Long): Boolean
    
    /**
     * Obtiene el espacio disponible en disco
     */
    fun getAvailableSpace(path: String): Long
}
