package com.dam2.flashdownloader.data.network

import com.dam2.flashdownloader.domain.model.DownloadMetadata
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Cliente de red para realizar descargas HTTP
 * ✅ OPTIMIZADO para archivos grandes
 */
class DownloadClient(private val httpClient: HttpClient) {

    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speed: Long
    )

    suspend fun getFileMetadata(url: String): Result<DownloadMetadata> {
        return try {
            val response = httpClient.head(url)
            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
            val mimeType = response.headers[HttpHeaders.ContentType]
            val supportsRange = response.headers[HttpHeaders.AcceptRanges] == "bytes"
            val fileName = extractFileNameFromHeaders(response.headers, url)
            val lastModified = response.headers[HttpHeaders.LastModified]

            Result.success(
                DownloadMetadata(
                    totalBytes = contentLength,
                    mimeType = mimeType,
                    supportsRangeRequests = supportsRange,
                    serverFileName = fileName,
                    lastModified = lastModified
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun downloadFile(
        url: String,
        outputPath: String,
        startByte: Long = 0L,
        speedLimitBytesPerSecond: Long? = null,
        fileWriter: FileWriter
    ): Flow<DownloadProgress> = flow {
        var totalBytesDownloaded = startByte
        var lastEmitTime = System.currentTimeMillis()
        var bytesDownloadedSinceLastEmit = 0L
        var currentSpeed = 0L

        // ✅ FIX: Token Bucket para límite de velocidad
        var tokenBucket = 0.0
        var lastTokenRefill = System.currentTimeMillis()

        try {
            val response = httpClient.prepareGet(url) {
                if (startByte > 0) {
                    header(HttpHeaders.Range, "bytes=$startByte-")
                }
            }.execute()

            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
            val totalBytes = if (startByte > 0 && contentLength > 0) {
                startByte + contentLength
            } else {
                contentLength
            }

            val channel: ByteReadChannel = response.bodyAsChannel()
            
            // ✅ FIX: Buffer GRANDE para archivos grandes (1MB)
            val buffer = ByteArray(LARGE_BUFFER_SIZE)

            fileWriter.openForWrite(outputPath, startByte > 0)

            while (!channel.isClosedForRead && coroutineContext.isActive) {
                val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                if (bytesRead <= 0) break

                // ✅ FIX: Aplicar límite de velocidad CORRECTAMENTE
                if (speedLimitBytesPerSecond != null && speedLimitBytesPerSecond > 0) {
                    val currentTime = System.currentTimeMillis()
                    val timeDelta = (currentTime - lastTokenRefill) / 1000.0

                    // Rellenar tokens
                    tokenBucket += timeDelta * speedLimitBytesPerSecond
                    if (tokenBucket > speedLimitBytesPerSecond.toDouble()) {
                        tokenBucket = speedLimitBytesPerSecond.toDouble()
                    }
                    lastTokenRefill = currentTime

                    // Consumir tokens
                    tokenBucket -= bytesRead

                    // Si no hay tokens, esperar
                    if (tokenBucket < 0) {
                        val deficit = -tokenBucket
                        val delayMs = ((deficit / speedLimitBytesPerSecond) * 1000).toLong()
                        if (delayMs > 0) {
                            delay(delayMs)
                        }
                        tokenBucket = 0.0
                    }
                }

                // Escribir datos
                fileWriter.write(buffer, 0, bytesRead)
                totalBytesDownloaded += bytesRead
                bytesDownloadedSinceLastEmit += bytesRead

                // ✅ FIX: Emitir progreso cada 200ms (más frecuente)
                val currentTime = System.currentTimeMillis()
                val timeDiff = currentTime - lastEmitTime
                if (timeDiff >= 200) {
                    currentSpeed = if (timeDiff > 0) {
                        (bytesDownloadedSinceLastEmit * 1000) / timeDiff
                    } else {
                        0L
                    }

                    emit(
                        DownloadProgress(
                            bytesDownloaded = totalBytesDownloaded,
                            totalBytes = totalBytes,
                            speed = currentSpeed
                        )
                    )

                    lastEmitTime = currentTime
                    bytesDownloadedSinceLastEmit = 0L
                }
            }

            // Emitir progreso final
            emit(
                DownloadProgress(
                    bytesDownloaded = totalBytesDownloaded,
                    totalBytes = totalBytes,
                    speed = 0L
                )
            )

            fileWriter.close()

        } catch (e: Exception) {
            fileWriter.close()
            throw e
        }
    }

    private fun extractFileNameFromHeaders(headers: Headers, url: String): String {
        val contentDisposition = headers[HttpHeaders.ContentDisposition]
        if (contentDisposition != null) {
            val fileNameMatch = Regex("filename=\"?([^\"]+)\"?").find(contentDisposition)
            if (fileNameMatch != null) {
                return fileNameMatch.groupValues[1]
            }
        }
        return url.substringAfterLast('/').substringBefore('?').ifEmpty { "download" }
    }

    companion object {
        // ✅ FIX: Buffer de 1MB para archivos grandes
        private const val LARGE_BUFFER_SIZE = 1024 * 1024 // 1MB
    }
}

interface FileWriter {
    fun openForWrite(path: String, append: Boolean)
    fun write(buffer: ByteArray, offset: Int, length: Int)
    fun close()
    fun delete(path: String): Boolean
}