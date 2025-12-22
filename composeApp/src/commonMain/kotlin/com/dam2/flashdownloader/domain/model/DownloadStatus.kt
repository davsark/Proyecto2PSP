package com.dam2.flashdownloader.domain.model

/**
 * Estados posibles de una descarga durante su ciclo de vida
 */
sealed class DownloadStatus {
    /**
     * La descarga está en cola esperando su turno
     */
    /**
     * La descarga está en cola esperando su turno
     * @param bytesDownloaded Bytes descargados hasta el momento (para resumir)
     * @param totalBytes Total de bytes (si se conoce)
     */
    data class Queued(
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = -1L
    ) : DownloadStatus() {
        val progress: Float
            get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()) else 0f
    }

    /**
     * La descarga está actualmente en progreso
     * @param bytesDownloaded Bytes descargados hasta el momento
     * @param totalBytes Total de bytes a descargar (puede ser -1 si es desconocido)
     * @param speed Velocidad actual en bytes/segundo
     */
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speed: Long = 0L
    ) : DownloadStatus() {
        val progress: Float
            get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()) else 0f

        val progressPercentage: Int
            get() = (progress * 100).toInt()

        /**
         * Calcula el tiempo restante estimado en segundos
         */
        val estimatedTimeRemaining: Long
            get() {
                if (speed <= 0 || totalBytes <= 0) return -1L
                val remainingBytes = totalBytes - bytesDownloaded
                return remainingBytes / speed
            }
    }

    /**
     * La descarga ha sido pausada manualmente
     * @param bytesDownloaded Bytes descargados antes de pausar
     * @param totalBytes Total de bytes a descargar
     */
    data class Paused(
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadStatus() {
        val progress: Float
            get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()) else 0f

        val progressPercentage: Int
            get() = (progress * 100).toInt()
    }

    /**
     * La descarga ha sido completada exitosamente
     * @param filePath Ruta donde se guardó el archivo
     * @param totalBytes Tamaño total del archivo
     * @param calculatedHash Hash SHA-256 del archivo (opcional)
     */
    data class Completed(
        val filePath: String,
        val totalBytes: Long,
        val calculatedHash: String? = null
    ) : DownloadStatus()

    /**
     * La descarga ha fallado
     * @param error Mensaje de error descriptivo
     * @param bytesDownloaded Bytes descargados antes del error
     */
    data class Failed(
        val error: String,
        val bytesDownloaded: Long = 0L
    ) : DownloadStatus()

    /**
     * La descarga ha sido cancelada por el usuario
     */
    data object Cancelled : DownloadStatus()

    /**
     * Verifica si la descarga está en un estado activo (descargando o en cola)
     */
    val isActive: Boolean
        get() = this is Queued || this is Downloading

    /**
     * Verifica si la descarga puede ser reanudada
     */
    val canResume: Boolean
        get() = this is Paused || this is Failed

    /**
     * Verifica si la descarga está terminada (completada, cancelada o fallida permanentemente)
     */
    val isTerminal: Boolean
        get() = this is Completed || this is Cancelled
}
