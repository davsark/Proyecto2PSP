package com.dam2.flashdownloader.utils

/**
 * Algoritmos de hash soportados
 */
enum class HashAlgorithm {
    MD5,
    SHA1,
    SHA256
}

/**
 * Calculadora de hash específica de plataforma
 */
expect class HashCalculator() {
    /**
     * Calcula el hash de un archivo
     * @param filePath Ruta del archivo
     * @param algorithm Algoritmo de hash a usar
     * @return Hash en formato hexadecimal (lowercase)
     */
    suspend fun calculateFileHash(filePath: String, algorithm: HashAlgorithm): Result<String>

    /**
     * Verifica si un archivo coincide con un hash esperado
     * @param filePath Ruta del archivo
     * @param expectedHash Hash esperado en formato hexadecimal
     * @param algorithm Algoritmo de hash a usar
     * @return true si coincide, false si no
     */
    suspend fun verifyFileHash(filePath: String, expectedHash: String, algorithm: HashAlgorithm): Result<Boolean>
}
