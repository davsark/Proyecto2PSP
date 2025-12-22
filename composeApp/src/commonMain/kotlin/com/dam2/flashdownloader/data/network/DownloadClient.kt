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
        var bytesSinceLastFlush = 0L
        var lastFlushTime = System.currentTimeMillis()

        // ✅ Token Bucket para límite de velocidad
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
            
            // ✅ Buffer de 64KB (estándar industria)
            val buffer = ByteArray(BUFFER_SIZE)

            fileWriter.openForWrite(outputPath, startByte > 0)

            while (!channel.isClosedForRead && coroutineContext.isActive) {
                val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                if (bytesRead <= 0) break

                // ✅ Aplicar límite de velocidad
                if (speedLimitBytesPerSecond != null && speedLimitBytesPerSecond > 0) {
                    val currentTime = System.currentTimeMillis()
                    val timeDelta = (currentTime - lastTokenRefill) / 1000.0

                    tokenBucket += timeDelta * speedLimitBytesPerSecond
                    if (tokenBucket > speedLimitBytesPerSecond.toDouble()) {
                        tokenBucket = speedLimitBytesPerSecond.toDouble()
                    }
                    lastTokenRefill = currentTime

                    tokenBucket -= bytesRead

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
                bytesSinceLastFlush += bytesRead

                // ✅ Flush periódico cada 10MB o 30 segundos
                val currentTime = System.currentTimeMillis()
                if (bytesSinceLastFlush >= FLUSH_INTERVAL_BYTES || 
                    (currentTime - lastFlushTime) >= FLUSH_INTERVAL_MS) {
                    fileWriter.flush()
                    bytesSinceLastFlush = 0L
                    lastFlushTime = currentTime
                }

                // ✅ Emitir progreso cada 500ms O cada 1MB
                val timeDiff = currentTime - lastEmitTime
                if (timeDiff >= 500 || bytesDownloadedSinceLastEmit >= 1024 * 1024) {
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

            // Flush final
            fileWriter.flush()

            // Emitir progreso final
            emit(
                DownloadProgress(
                    bytesDownloaded = totalBytesDownloaded,
                    totalBytes = totalBytes,
                    speed = 0L
                )
            )

            fileWriter.close()

        } catch (e: java.net.SocketTimeoutException) {
            fileWriter.close()
            throw Exception("Socket timeout - conexión muy lenta o perdida", e)
        } catch (e: java.net.UnknownHostException) {
            fileWriter.close()
            throw Exception("No se pudo resolver el host: ${e.message}", e)
        } catch (e: java.io.IOException) {
            fileWriter.close()
            throw Exception("Error de red: ${e.message}", e)
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
        // ✅ Buffer de 64KB (estándar industria, tamaño ventana TCP)
        private const val BUFFER_SIZE = 64 * 1024 // 64KB
        
        // ✅ Flush cada 10MB para asegurar datos en disco
        private const val FLUSH_INTERVAL_BYTES = 10 * 1024 * 1024 // 10MB
        
        // ✅ Flush cada 30 segundos como máximo
        private const val FLUSH_INTERVAL_MS = 30_000L // 30 segundos
    }
}

interface FileWriter {
    fun openForWrite(path: String, append: Boolean)
    fun write(buffer: ByteArray, offset: Int, length: Int)
    fun flush()  // ✅ Flush datos al disco
    fun close()
    fun delete(path: String): Boolean
}