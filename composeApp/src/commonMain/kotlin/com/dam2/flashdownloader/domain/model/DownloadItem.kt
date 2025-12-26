package com.dam2.flashdownloader.domain.model

/**
 * Modelo de dominio que representa una descarga individual
 */
data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val category: Category,
    val priority: Priority,
    val status: DownloadStatus,
    val createdAt: Long, // Timestamp en milisegundos - debe ser pasado por el llamador
    val speedLimit: Long? = null, // bytes/segundo, null = sin límite
    val localPath: String? = null,
    val hash: String? = null, // Para verificación de integridad
    val metadata: DownloadMetadata = DownloadMetadata()
) {
    /**
     * Obtiene el tamaño total si está disponible
     */
    val totalSize: Long
        get() = when (status) {
            is DownloadStatus.Downloading -> status.totalBytes
            is DownloadStatus.Paused -> status.totalBytes
            is DownloadStatus.Completed -> status.totalBytes
            is DownloadStatus.Queued -> status.totalBytes
            else -> metadata.totalBytes
        }

    /**
     * Obtiene los bytes descargados actuales
     */
    val downloadedBytes: Long
        get() = when (status) {
            is DownloadStatus.Downloading -> status.bytesDownloaded
            is DownloadStatus.Paused -> status.bytesDownloaded
            is DownloadStatus.Failed -> status.bytesDownloaded
            is DownloadStatus.Completed -> status.totalBytes
            is DownloadStatus.Queued -> status.bytesDownloaded
            else -> 0L
        }

    /**
     * Obtiene la velocidad actual de descarga
     */
    val currentSpeed: Long
        get() = when (status) {
            is DownloadStatus.Downloading -> status.speed
            else -> 0L
        }
}

/**
 * Metadata adicional de la descarga
 */
data class DownloadMetadata(
    val totalBytes: Long = -1L,
    val mimeType: String? = null,
    val supportsRangeRequests: Boolean = false,
    val serverFileName: String? = null,
    val lastModified: String? = null
)

/**
 * Estadísticas globales de todas las descargas
 * Modelo de dominio puro sin lógica de presentación
 */
data class DownloadStatistics(
    val totalDownloads: Int = 0,
    val activeDownloads: Int = 0,
    val queuedDownloads: Int = 0,
    val pausedDownloads: Int = 0,
    val completedDownloads: Int = 0,
    val failedDownloads: Int = 0,
    val totalBytesDownloaded: Long = 0L,
    val currentGlobalSpeed: Long = 0L,
    val averageSpeed: Long = 0L
)
