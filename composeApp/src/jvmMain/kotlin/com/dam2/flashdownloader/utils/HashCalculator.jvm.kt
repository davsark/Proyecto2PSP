package com.dam2.flashdownloader.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Implementación JVM de HashCalculator usando java.security.MessageDigest
 */
actual class HashCalculator {

    actual suspend fun calculateFileHash(filePath: String, algorithm: HashAlgorithm): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(Exception("Archivo no encontrado: $filePath"))
                }

                val digest = MessageDigest.getInstance(algorithm.getAlgorithmName())
                val buffer = ByteArray(8192)

                FileInputStream(file).use { fis ->
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                }

                val hashBytes = digest.digest()
                val hexString = hashBytes.joinToString("") { "%02x".format(it) }

                Result.success(hexString)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    actual suspend fun verifyFileHash(
        filePath: String,
        expectedHash: String,
        algorithm: HashAlgorithm
    ): Result<Boolean> {
        val hashResult = calculateFileHash(filePath, algorithm)

        return if (hashResult.isSuccess) {
            val calculatedHash = hashResult.getOrNull() ?: ""
            val normalizedExpected = expectedHash.lowercase().trim()
            val normalizedCalculated = calculatedHash.lowercase().trim()
            Result.success(normalizedExpected == normalizedCalculated)
        } else {
            // Propagar el error
            Result.failure(hashResult.exceptionOrNull() ?: Exception("Error desconocido"))
        }
    }

    private fun HashAlgorithm.getAlgorithmName(): String {
        return when (this) {
            HashAlgorithm.MD5 -> "MD5"
            HashAlgorithm.SHA1 -> "SHA-1"
            HashAlgorithm.SHA256 -> "SHA-256"
        }
    }
}
