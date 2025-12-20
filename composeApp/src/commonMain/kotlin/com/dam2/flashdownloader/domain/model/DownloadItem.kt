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

    /**
     * Formatea el tamaño en una cadena legible
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Formatea la velocidad en una cadena legible
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        }
    }

    /**
     * Formatea el tiempo restante en una cadena legible
     */
    fun formatTimeRemaining(seconds: Long): String {
        if (seconds < 0) return "Calculando..."
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
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
) {
    fun formatTotalSize(): String {
        return when {
            totalBytesDownloaded < 1024 -> "$totalBytesDownloaded B"
            totalBytesDownloaded < 1024 * 1024 -> "${totalBytesDownloaded / 1024} KB"
            totalBytesDownloaded < 1024 * 1024 * 1024 -> String.format("%.2f MB", totalBytesDownloaded / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", totalBytesDownloaded / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        }
    }
}
