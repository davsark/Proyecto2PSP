package com.dam2.flashdownloader.domain.repository

import com.dam2.flashdownloader.domain.model.DownloadItem
import com.dam2.flashdownloader.domain.model.DownloadHistoryEntry
import com.dam2.flashdownloader.domain.model.DownloadHistoryFilter

/**
 * Repositorio para la persistencia de descargas
 * Permite guardar, cargar y restaurar el estado de las descargas entre sesiones
 */
interface DownloadRepository {
    /**
     * Guarda una descarga en almacenamiento persistente
     */
    suspend fun saveDownload(download: DownloadItem): Result<Unit>

    /**
     * Actualiza una descarga existente
     */
    suspend fun updateDownload(download: DownloadItem): Result<Unit>

    /**
     * Actualiza todas las descargas (útil para reordenamiento masivo)
     */
    suspend fun updateAll(downloads: List<DownloadItem>): Result<Unit>

    /**
     * Elimina una descarga del almacenamiento
     */
    suspend fun deleteDownload(id: String): Result<Unit>

    /**
     * Obtiene una descarga por su ID
     */
    suspend fun getDownload(id: String): Result<DownloadItem?>

    /**
     * Obtiene todas las descargas guardadas
     */
    suspend fun getAllDownloads(): Result<List<DownloadItem>>

    /**
     * Limpia todas las descargas completadas
     */
    suspend fun clearCompleted(): Result<Unit>

    /**
     * Guarda el progreso parcial de una descarga (para reanudar después)
     */
    suspend fun savePartialData(id: String, bytesDownloaded: Long): Result<Unit>

    /**
     * Obtiene el progreso guardado de una descarga
     */
    suspend fun getPartialData(id: String): Result<Long>

    /**
     * Obtiene el historial de descargas completadas con filtros opcionales
     */
    suspend fun getHistory(filter: DownloadHistoryFilter = DownloadHistoryFilter()): Result<List<DownloadHistoryEntry>>

    /**
     * Limpia todo el historial de descargas completadas
     */
    suspend fun clearHistory(): Result<Unit>

    /**
     * Elimina una entrada específica del historial
     */
    suspend fun removeHistoryEntry(id: String): Result<Unit>
}
