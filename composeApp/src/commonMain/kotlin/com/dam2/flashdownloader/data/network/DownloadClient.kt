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
 * Soporta descargas parciales (range requests) para reanudar descargas
 */
class DownloadClient(private val httpClient: HttpClient) {

    /**
     * Resultado del progreso de descarga
     */
    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speed: Long
    )

    /**
     * Obtiene metadata del archivo sin descargarlo (HEAD request)
     */
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

    /**
     * Descarga un archivo emitiendo progreso mediante Flow
     * @param url URL del archivo a descargar
     * @param outputPath Ruta donde guardar el archivo
     * @param startByte Byte desde donde iniciar (para reanudar descargas)
     * @param speedLimitBytesPerSecond Límite de velocidad en bytes/segundo (null = sin límite)
     * @param fileWriter Función para escribir los datos descargados
     */
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
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            // Inicializar el archivo para escritura
            fileWriter.openForWrite(outputPath, startByte > 0)

            while (!channel.isClosedForRead && coroutineContext.isActive) {
                // ✅ Medir tiempo ANTES de leer para control preciso de velocidad
                val readStartTime = System.currentTimeMillis()

                val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                if (bytesRead <= 0) break

                // Escribir datos
                fileWriter.write(buffer, 0, bytesRead)
                totalBytesDownloaded += bytesRead
                bytesDownloadedSinceLastEmit += bytesRead

                // ✅ Aplicar límite de velocidad ANTES de emitir progreso
                if (speedLimitBytesPerSecond != null && speedLimitBytesPerSecond > 0) {
                    val readEndTime = System.currentTimeMillis()
                    val actualReadTimeMs = readEndTime - readStartTime
                    applySpeedLimit(bytesRead.toLong(), speedLimitBytesPerSecond, actualReadTimeMs)
                }

                // Emitir progreso cada 500ms para no saturar la UI
                val currentTime = System.currentTimeMillis()
                val timeDiff = currentTime - lastEmitTime
                if (timeDiff >= 500) {
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

        } finally {
            // ✅ SIEMPRE cerrar el archivo, incluso si hay errores
            try {
                fileWriter.close()
            } catch (e: Exception) {
                // Ignorar errores al cerrar
            }
        }
    }

    /**
     * Aplica un límite de velocidad mediante delay calculado
     * @param bytesRead Bytes leídos en esta iteración
     * @param limitBytesPerSecond Límite de velocidad en bytes/segundo
     * @param actualTimeMs Tiempo real que tomó leer y escribir los bytes
     */
    private suspend fun applySpeedLimit(bytesRead: Long, limitBytesPerSecond: Long, actualTimeMs: Long) {
        // Calcular cuánto tiempo DEBERÍA haber tomado según el límite
        val idealTimeMs = (bytesRead * 1000) / limitBytesPerSecond

        // Calcular cuánto tiempo adicional necesitamos esperar
        val delayNeeded = idealTimeMs - actualTimeMs

        // Solo hacer delay si necesitamos ralentizar (si delayNeeded es positivo)
        if (delayNeeded > 0) {
            delay(delayNeeded)
        }
        // Si delayNeeded <= 0, significa que ya estamos por debajo del límite, no hacer nada
    }

    /**
     * Extrae el nombre del archivo desde los headers de respuesta o la URL
     */
    private fun extractFileNameFromHeaders(headers: Headers, url: String): String {
        // Intentar obtener desde Content-Disposition
        val contentDisposition = headers[HttpHeaders.ContentDisposition]
        if (contentDisposition != null) {
            val fileNameMatch = Regex("filename=\"?([^\"]+)\"?").find(contentDisposition)
            if (fileNameMatch != null) {
                return fileNameMatch.groupValues[1]
            }
        }

        // Si no, extraer de la URL
        return url.substringAfterLast('/').substringBefore('?').ifEmpty { "download" }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8192
    }
}

/**
 * Interfaz para escritura de archivos específica de plataforma
 */
interface FileWriter {
    /**
     * Abre el archivo para escritura
     * @param path Ruta del archivo
     * @param append Si es true, abre en modo append (para reanudar descargas)
     */
    fun openForWrite(path: String, append: Boolean)

    /**
     * Escribe bytes al archivo
     */
    fun write(buffer: ByteArray, offset: Int, length: Int)

    /**
     * Cierra el archivo
     */
    fun close()
}
