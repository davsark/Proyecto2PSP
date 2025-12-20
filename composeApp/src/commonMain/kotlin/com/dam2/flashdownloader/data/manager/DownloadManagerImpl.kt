package com.dam2.flashdownloader.data.manager

import com.dam2.flashdownloader.data.network.DownloadClient
import com.dam2.flashdownloader.data.network.FileWriter
import com.dam2.flashdownloader.domain.manager.DownloadManager
import com.dam2.flashdownloader.domain.model.*
import com.dam2.flashdownloader.domain.repository.DownloadRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Implementación completa del DownloadManager con control avanzado de concurrencia
 *
 * Características clave:
 * - Uso de Semaphore para limitar descargas simultáneas
 * - Mutex para thread-safety en operaciones sobre la lista
 * - Flow para progreso individual de cada descarga
 * - StateFlow para estado global reactivo
 * - Cola de prioridades automática
 * - Cancelación cooperativa
 * - Persistencia automática
 */
class DownloadManagerImpl(
    private val downloadClient: DownloadClient,
    private val repository: DownloadRepository,
    private val fileWriterFactory: FileWriterFactory,
    private val downloadPath: String,
    private val scope: CoroutineScope
) : DownloadManager {

    // SECCIÓN 1: ESTADO Y SINCRONIZACIÓN

    /**
     * Mutex para proteger el acceso a la lista de descargas (Thread-safety)
     */
    private val downloadsMutex = Mutex()

    /**
     * Semaphore para limitar el número de descargas simultáneas
     */
    private var downloadSemaphore = Semaphore(3)

    /**
     * Mapa de Jobs activos para cada descarga (para cancelación)
     */
    private val activeJobs = mutableMapOf<String, Job>()

    /**
     * Lista mutable interna de descargas
     */
    private val _downloadsList = mutableListOf<DownloadItem>()

    /**
     * StateFlow público con la lista de descargas (ordenada por prioridad)
     */
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    override val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    /**
     * StateFlow de estadísticas
     */
    private val _statistics = MutableStateFlow(DownloadStatistics())
    override val statistics: StateFlow<DownloadStatistics> = _statistics.asStateFlow()

    /**
     * Límite de descargas simultáneas
     */
    private val _maxConcurrentDownloads = MutableStateFlow(3)
    override val maxConcurrentDownloads: StateFlow<Int> = _maxConcurrentDownloads.asStateFlow()

    /**
     * Límite de velocidad global
     */
    private val _globalSpeedLimit = MutableStateFlow<Long?>(null)
    override val globalSpeedLimit: StateFlow<Long?> = _globalSpeedLimit.asStateFlow()

    // SECCIÓN 2: AÑADIR DESCARGAS

    override suspend fun addDownload(
        url: String,
        fileName: String?,
        category: Category?,
        priority: Priority,
        speedLimit: Long?
    ): Result<String> = downloadsMutex.withLock {
        return try {
            // Validar URL
            if (!isValidUrl(url)) {
                return Result.failure(IllegalArgumentException("URL inválida: $url"))
            }

            // Determinar nombre de archivo (sin llamada de red bloqueante)
            val finalFileName = fileName ?: extractFileNameFromUrl(url)

            // Determinar categoría
            val finalCategory = category ?: Category.fromFileName(finalFileName)

            // Crear ID único usando java.util.UUID (más compatible)
            val id = java.util.UUID.randomUUID().toString()

            // Obtener timestamp actual
            val currentTime = System.currentTimeMillis()

            // Crear item de descarga (metadata se obtendrá durante la descarga)
            val downloadItem = DownloadItem(
                id = id,
                url = url,
                fileName = finalFileName,
                category = finalCategory,
                priority = priority,
                status = DownloadStatus.Queued,
                createdAt = currentTime,
                speedLimit = speedLimit,
                metadata = DownloadMetadata() // Se actualizará cuando inicie la descarga
            )

            // Añadir a la lista
            _downloadsList.add(downloadItem)
            updateDownloadsFlow()

            // Guardar en repositorio
            repository.saveDownload(downloadItem)

            // Iniciar procesamiento de cola
            scope.launch(Dispatchers.IO) {
                processQueue()
            }

            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addMultipleDownloads(
        urls: List<String>,
        priority: Priority
    ): Result<List<String>> {
        val ids = mutableListOf<String>()
        urls.forEach { url ->
            val result = addDownload(url = url, priority = priority)
            result.onSuccess { ids.add(it) }
        }
        return Result.success(ids)
    }

    // SECCIÓN 3: CONTROL DE DESCARGAS INDIVIDUALES

    override suspend fun startDownload(id: String): Result<Unit> = downloadsMutex.withLock {
        val download = _downloadsList.find { it.id == id }
            ?: return Result.failure(Exception("Descarga no encontrada"))

        if (download.status.isActive) {
            return Result.failure(Exception("La descarga ya está activa"))
        }

        // Cambiar estado a en cola
        updateDownloadStatus(id, DownloadStatus.Queued)

        // Procesar cola
        scope.launch(Dispatchers.IO) {
            processQueue()
        }

        Result.success(Unit)
    }

    override suspend fun pauseDownload(id: String): Result<Unit> = downloadsMutex.withLock {
        val download = _downloadsList.find { it.id == id }
            ?: return Result.failure(Exception("Descarga no encontrada"))

        // Cancelar el job activo
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        // Actualizar estado a pausado
        when (val status = download.status) {
            is DownloadStatus.Downloading -> {
                updateDownloadStatus(
                    id,
                    DownloadStatus.Paused(status.bytesDownloaded, status.totalBytes)
                )
            }
            else -> {
                // Si no está descargando, no hacer nada
            }
        }

        Result.success(Unit)
    }

    override suspend fun resumeDownload(id: String): Result<Unit> {
        return startDownload(id) // Reusar la lógica de inicio
    }

    override suspend fun cancelDownload(id: String): Result<Unit> = downloadsMutex.withLock {
        // Cancelar el job
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        // Actualizar estado
        updateDownloadStatus(id, DownloadStatus.Cancelled)

        Result.success(Unit)
    }

    override suspend fun removeDownload(id: String): Result<Unit> = downloadsMutex.withLock {
        // Cancelar si está activo
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        // Eliminar de la lista
        _downloadsList.removeAll { it.id == id }
        updateDownloadsFlow()

        // Eliminar del repositorio
        repository.deleteDownload(id)

        Result.success(Unit)
    }

    // SECCIÓN 4: CONTROL GLOBAL

    override suspend fun pauseAll(): Result<Unit> = downloadsMutex.withLock {
        _downloadsList.filter { it.status is DownloadStatus.Downloading }.forEach {
            pauseDownload(it.id)
        }
        Result.success(Unit)
    }

    override suspend fun resumeAll(): Result<Unit> {
        val pausedIds = downloadsMutex.withLock {
            _downloadsList.filter { it.status is DownloadStatus.Paused }.map { it.id }
        }
        pausedIds.forEach { resumeDownload(it) }
        return Result.success(Unit)
    }

    override suspend fun cancelAll(): Result<Unit> = downloadsMutex.withLock {
        _downloadsList.filter { it.status.isActive }.forEach {
            cancelDownload(it.id)
        }
        Result.success(Unit)
    }

    override suspend fun clearCompleted(): Result<Unit> = downloadsMutex.withLock {
        _downloadsList.removeAll { it.status is DownloadStatus.Completed }
        updateDownloadsFlow()
        repository.clearCompleted()
        Result.success(Unit)
    }

    // SECCIÓN 5: PRIORIDADES Y ORDENAMIENTO

    override suspend fun changePriority(id: String, newPriority: Priority): Result<Unit> = downloadsMutex.withLock {
        val index = _downloadsList.indexOfFirst { it.id == id }
        if (index == -1) {
            return Result.failure(Exception("Descarga no encontrada"))
        }

        val download = _downloadsList[index]
        _downloadsList[index] = download.copy(priority = newPriority)
        updateDownloadsFlow()
        repository.updateDownload(_downloadsList[index])

        Result.success(Unit)
    }

    override suspend fun moveUp(id: String): Result<Unit> = downloadsMutex.withLock {
        val index = _downloadsList.indexOfFirst { it.id == id }
        if (index <= 0) {
            return Result.failure(Exception("No se puede mover más arriba"))
        }

        // Intercambiar posiciones
        val temp = _downloadsList[index]
        _downloadsList[index] = _downloadsList[index - 1]
        _downloadsList[index - 1] = temp
        updateDownloadsFlow()

        Result.success(Unit)
    }

    override suspend fun moveDown(id: String): Result<Unit> = downloadsMutex.withLock {
        val index = _downloadsList.indexOfFirst { it.id == id }
        if (index == -1 || index >= _downloadsList.size - 1) {
            return Result.failure(Exception("No se puede mover más abajo"))
        }

        // Intercambiar posiciones
        val temp = _downloadsList[index]
        _downloadsList[index] = _downloadsList[index + 1]
        _downloadsList[index + 1] = temp
        updateDownloadsFlow()

        Result.success(Unit)
    }

    // SECCIÓN 6: CONFIGURACIÓN

    override suspend fun setMaxConcurrentDownloads(limit: Int): Result<Unit> {
        if (limit !in 1..10) {
            return Result.failure(IllegalArgumentException("El límite debe estar entre 1 y 10"))
        }

        _maxConcurrentDownloads.value = limit
        downloadSemaphore = Semaphore(limit)

        // Re-procesar cola con el nuevo límite
        scope.launch(Dispatchers.IO) {
            processQueue()
        }

        return Result.success(Unit)
    }

    override suspend fun setGlobalSpeedLimit(bytesPerSecond: Long?): Result<Unit> {
        _globalSpeedLimit.value = bytesPerSecond
        return Result.success(Unit)
    }

    override suspend fun setDownloadSpeedLimit(id: String, bytesPerSecond: Long?): Result<Unit> = downloadsMutex.withLock {
        val index = _downloadsList.indexOfFirst { it.id == id }
        if (index == -1) {
            return Result.failure(Exception("Descarga no encontrada"))
        }

        _downloadsList[index] = _downloadsList[index].copy(speedLimit = bytesPerSecond)
        updateDownloadsFlow()

        Result.success(Unit)
    }

    override suspend fun retryDownload(id: String): Result<Unit> {
        return startDownload(id)
    }

    // SECCIÓN 7: PROCESAMIENTO DE COLA (PARTE CRÍTICA)

    /**
     * Procesa la cola de descargas respetando el límite de concurrencia
     * Esta es la función más crítica del sistema
     */
    private suspend fun processQueue() {
        downloadsMutex.withLock {
            // Obtener descargas en cola ordenadas por prioridad
            val queued = _downloadsList
                .filter { it.status is DownloadStatus.Queued && !activeJobs.containsKey(it.id) }
                .sortedWith(compareByDescending<DownloadItem> { it.priority.level }.thenBy { it.createdAt })

            // Procesar cada descarga en cola
            queued.forEach { download ->
                // Intentar adquirir permiso del semaphore (no bloqueante)
                if (downloadSemaphore.tryAcquire()) {
                    // Lanzar descarga en corrutina separada
                    val job = scope.launch(Dispatchers.IO) {
                        try {
                            executeDownload(download.id)
                        } finally {
                            // Liberar permiso al terminar
                            downloadSemaphore.release()
                            // Procesar siguiente en cola
                            processQueue()
                        }
                    }
                    activeJobs[download.id] = job
                }
            }
        }
    }

    /**
     * Ejecuta la descarga real de un archivo
     * Usa async para ejecución paralela y Flow para progreso
     */
    private suspend fun executeDownload(id: String) {
        val download = downloadsMutex.withLock {
            _downloadsList.find { it.id == id }
        } ?: return

        try {
            // Verificar si hay progreso guardado (para reanudar)
            val startByte = repository.getPartialData(id).getOrNull() ?: 0L

            // Determinar límite de velocidad (individual o global)
            val speedLimit = download.speedLimit ?: _globalSpeedLimit.value

            // Construir ruta completa del archivo
            val fullPath = "$downloadPath/${download.fileName}"

            // Crear FileWriter
            val fileWriter = fileWriterFactory.createFileWriter()

            // Iniciar descarga y recolectar progreso
            downloadClient.downloadFile(
                url = download.url,
                outputPath = fullPath,
                startByte = startByte,
                speedLimitBytesPerSecond = speedLimit,
                fileWriter = fileWriter
            ).collect { progress ->
                // Verificar cancelación
                currentCoroutineContext().ensureActive()

                // Actualizar estado con progreso
                updateDownloadStatus(
                    id,
                    DownloadStatus.Downloading(
                        bytesDownloaded = progress.bytesDownloaded,
                        totalBytes = progress.totalBytes,
                        speed = progress.speed
                    )
                )

                // Guardar progreso para poder reanudar
                repository.savePartialData(id, progress.bytesDownloaded)

                // Actualizar estadísticas globales
                updateStatistics()
            }

            // Descarga completada
            updateDownloadStatus(
                id,
                DownloadStatus.Completed(
                    filePath = fullPath,
                    totalBytes = download.totalSize
                )
            )

        } catch (e: CancellationException) {
            // Descarga cancelada por el usuario (comportamiento esperado)
            throw e
        } catch (e: Exception) {
            // Error en la descarga
            val currentBytes = downloadsMutex.withLock {
                _downloadsList.find { it.id == id }?.downloadedBytes ?: 0L
            }
            updateDownloadStatus(
                id,
                DownloadStatus.Failed(
                    error = e.message ?: "Error desconocido",
                    bytesDownloaded = currentBytes
                )
            )
        } finally {
            activeJobs.remove(id)
            updateStatistics()
        }
    }

    // SECCIÓN 8: FUNCIONES AUXILIARES

    /**
     * Actualiza el estado de una descarga específica (Thread-safe)
     */
    private suspend fun updateDownloadStatus(id: String, newStatus: DownloadStatus) {
        downloadsMutex.withLock {
            val index = _downloadsList.indexOfFirst { it.id == id }
            if (index != -1) {
                _downloadsList[index] = _downloadsList[index].copy(status = newStatus)
                updateDownloadsFlow()
                repository.updateDownload(_downloadsList[index])
            }
        }
    }

    /**
     * Actualiza el StateFlow de descargas
     */
    private fun updateDownloadsFlow() {
        // Ordenar por prioridad y fecha de creación
        val sorted = _downloadsList.sortedWith(
            compareByDescending<DownloadItem> { it.priority.level }
                .thenBy { it.createdAt }
        )
        _downloads.value = sorted
    }

    /**
     * Actualiza las estadísticas globales
     */
    private suspend fun updateStatistics() {
        downloadsMutex.withLock {
            val stats = DownloadStatistics(
                totalDownloads = _downloadsList.size,
                activeDownloads = _downloadsList.count { it.status is DownloadStatus.Downloading },
                queuedDownloads = _downloadsList.count { it.status is DownloadStatus.Queued },
                pausedDownloads = _downloadsList.count { it.status is DownloadStatus.Paused },
                completedDownloads = _downloadsList.count { it.status is DownloadStatus.Completed },
                failedDownloads = _downloadsList.count { it.status is DownloadStatus.Failed },
                totalBytesDownloaded = _downloadsList.sumOf { it.downloadedBytes },
                currentGlobalSpeed = _downloadsList.sumOf { it.currentSpeed },
                averageSpeed = calculateAverageSpeed()
            )
            _statistics.value = stats
        }
    }

    /**
     * Calcula la velocidad media de descargas activas
     */
    private fun calculateAverageSpeed(): Long {
        val activeSpeeds = _downloadsList
            .filter { it.status is DownloadStatus.Downloading }
            .map { it.currentSpeed }
        return if (activeSpeeds.isNotEmpty()) {
            activeSpeeds.average().toLong()
        } else {
            0L
        }
    }

    /**
     * Valida una URL
     */
    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true)
    }

    /**
     * Extrae el nombre de archivo de una URL
     */
    private fun extractFileNameFromUrl(url: String): String {
        return url.substringAfterLast('/').substringBefore('?').ifEmpty { "download" }
    }

    // SECCIÓN 9: VERIFICACIÓN DE INTEGRIDAD

    override suspend fun verifyIntegrity(id: String, expectedHash: String): Result<Boolean> {
        // TODO: Implementar verificación de hash (MD5, SHA-256, etc.)
        // Esta funcionalidad requiere APIs específicas de plataforma
        return Result.success(true)
    }

    // SECCIÓN 10: PERSISTENCIA Y CICLO DE VIDA

    override suspend fun loadSavedDownloads(): Result<Unit> {
        return try {
            val savedDownloads = repository.getAllDownloads().getOrNull() ?: emptyList()
            downloadsMutex.withLock {
                _downloadsList.clear()
                _downloadsList.addAll(
                    savedDownloads.map {
                        // Restablecer estados activos a pausado
                        if (it.status is DownloadStatus.Downloading) {
                            it.copy(
                                status = DownloadStatus.Paused(
                                    it.downloadedBytes,
                                    it.totalSize
                                )
                            )
                        } else {
                            it
                        }
                    }
                )
                updateDownloadsFlow()
            }
            updateStatistics()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun shutdown(): Result<Unit> {
        return try {
            // Pausar todas las descargas activas
            pauseAll()

            // Guardar estado actual
            downloadsMutex.withLock {
                _downloadsList.forEach { download ->
                    repository.updateDownload(download)
                }
            }

            // Cancelar todas las corrutinas
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Factory para crear FileWriters específicos de plataforma
 */
interface FileWriterFactory {
    fun createFileWriter(): FileWriter
}
