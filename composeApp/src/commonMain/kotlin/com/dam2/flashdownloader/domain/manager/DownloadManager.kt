package com.dam2.flashdownloader.domain.manager

import com.dam2.flashdownloader.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manager central para gestión de descargas
 * Controla la concurrencia, cola de espera, prioridades y persistencia
 */
interface DownloadManager {

    /**
     * StateFlow con la lista completa de descargas
     * La UI se suscribe a este flow para mostrar cambios en tiempo real
     */
    val downloads: StateFlow<List<DownloadItem>>

    /**
     * StateFlow con las estadísticas globales
     */
    val statistics: StateFlow<DownloadStatistics>

    /**
     * Límite actual de descargas simultáneas
     */
    val maxConcurrentDownloads: StateFlow<Int>

    /**
     * Límite de velocidad global (bytes/segundo, null = sin límite)
     */
    val globalSpeedLimit: StateFlow<Long?>

    /**
     * Añade una nueva descarga a la cola
     * @param url URL del archivo a descargar
     * @param fileName Nombre del archivo (opcional, se detecta automáticamente)
     * @param category Categoría (opcional, se detecta desde la extensión)
     * @param priority Prioridad de la descarga
     * @param hash Hash SHA-256 esperado para verificación de integridad (opcional)
     * @return ID de la descarga creada
     */
    suspend fun addDownload(
        url: String,
        fileName: String? = null,
        category: Category? = null,
        priority: Priority = Priority.MEDIUM,
        speedLimit: Long? = null,
        hash: String? = null
    ): Result<String>

    /**
     * Añade múltiples descargas desde URLs
     */
    suspend fun addMultipleDownloads(
        urls: List<String>,
        priority: Priority = Priority.MEDIUM
    ): Result<List<String>>

    /**
     * Inicia una descarga específica
     */
    suspend fun startDownload(id: String): Result<Unit>

    /**
     * Pausa una descarga específica
     */
    suspend fun pauseDownload(id: String): Result<Unit>

    /**
     * Reanuda una descarga pausada
     */
    suspend fun resumeDownload(id: String): Result<Unit>

    /**
     * Cancela una descarga
     */
    suspend fun cancelDownload(id: String): Result<Unit>

    /**
     * Elimina una descarga de la lista
     */
    suspend fun removeDownload(id: String): Result<Unit>

    /**
     * Pausa todas las descargas activas
     */
    suspend fun pauseAll(): Result<Unit>

    /**
     * Reanuda todas las descargas pausadas
     */
    suspend fun resumeAll(): Result<Unit>

    /**
     * Cancela todas las descargas activas
     */
    suspend fun cancelAll(): Result<Unit>

    /**
     * Cambia la prioridad de una descarga
     * Las descargas en cola se reordenarán automáticamente
     */
    suspend fun changePriority(id: String, newPriority: Priority): Result<Unit>

    /**
     * Mueve una descarga una posición arriba en la cola
     */
    suspend fun moveUp(id: String): Result<Unit>

    /**
     * Mueve una descarga una posición abajo en la cola
     */
    suspend fun moveDown(id: String): Result<Unit>

    /**
     * Cambia el límite de descargas simultáneas
     * @param limit Nuevo límite (1-10)
     */
    suspend fun setMaxConcurrentDownloads(limit: Int): Result<Unit>

    /**
     * Establece el límite de velocidad global
     * @param bytesPerSecond Límite en bytes/segundo (null = sin límite)
     */
    suspend fun setGlobalSpeedLimit(bytesPerSecond: Long?): Result<Unit>

    /**
     * Establece el límite de velocidad para una descarga específica
     */
    suspend fun setDownloadSpeedLimit(id: String, bytesPerSecond: Long?): Result<Unit>

    /**
     * Limpia todas las descargas completadas
     */
    suspend fun clearCompleted(): Result<Unit>

    /**
     * Mueve una descarga a una posición específica en la lista
     */
    suspend fun moveDownloadToPosition(id: String, newIndex: Int): Result<Unit>

    /**
     * Reintenta una descarga fallida
     */
    suspend fun retryDownload(id: String): Result<Unit>

    /**
     * Verifica la integridad de un archivo descargado mediante hash
     */
    suspend fun verifyIntegrity(id: String, expectedHash: String): Result<Boolean>

    /**
     * Carga las descargas guardadas desde el repositorio
     */
    suspend fun loadSavedDownloads(): Result<Unit>

    /**
     * Detiene el manager y guarda el estado actual
     */
    suspend fun shutdown(): Result<Unit>
}
