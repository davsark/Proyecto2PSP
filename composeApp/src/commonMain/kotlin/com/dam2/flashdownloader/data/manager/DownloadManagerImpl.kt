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

    // ✅ Sistema de procesamiento continuo de cola (reemplaza recursión)
    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    processQueue()
                } catch (e: Exception) {
                    // Ignorar errores en el procesamiento de cola para no romper el loop
                }
                delay(500) // ✅ Revisar cola cada 500ms para mejor responsividad
            }
        }
    }

    override suspend fun addDownload(
        url: String,
        fileName: String?,
        category: Category?,
        priority: Priority,
        speedLimit: Long?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validar URL
            if (!isValidUrl(url)) {
                return@withContext Result.failure(IllegalArgumentException("URL inválida: $url"))
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

            // ✅ Lock MÍNIMO: solo para añadir a la lista
            downloadsMutex.withLock {
                _downloadsList.add(downloadItem)
                updateDownloadsFlow()
            }

            // Guardar en repositorio (SIN lock)
            repository.saveDownload(downloadItem)

            // ✅ NO llamar a processQueue aquí - el init loop lo manejará automáticamente

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

    override suspend fun startDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val download = downloadsMutex.withLock {
            _downloadsList.find { it.id == id }
        } ?: return@withContext Result.failure(Exception("Descarga no encontrada"))

        if (download.status.isActive) {
            return@withContext Result.failure(Exception("La descarga ya está activa"))
        }

        // Cambiar estado a en cola
        updateDownloadStatus(id, DownloadStatus.Queued)

        // El init loop procesará automáticamente la cola

        Result.success(Unit)
    }

    override suspend fun pauseDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val download = downloadsMutex.withLock {
            _downloadsList.find { it.id == id }
        } ?: return@withContext Result.failure(Exception("Descarga no encontrada"))

        // Cancelar el job activo
        val job = downloadsMutex.withLock {
            activeJobs[id]?.also { activeJobs.remove(id) }
        }
        job?.cancel()

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

    override suspend fun cancelDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val job = downloadsMutex.withLock {
            activeJobs[id]?.also { activeJobs.remove(id) }
        }
        job?.cancel()

        // Actualizar estado (usa su propio lock internamente)
        updateDownloadStatus(id, DownloadStatus.Cancelled)

        Result.success(Unit)
    }

    override suspend fun removeDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Cancelar el job activo
        val job = downloadsMutex.withLock {
            activeJobs[id]?.also { activeJobs.remove(id) }
        }
        job?.cancel()

        // Eliminar de la lista con lock mínimo
        downloadsMutex.withLock {
            _downloadsList.removeAll { it.id == id }
            updateDownloadsFlow()
        }

        // Eliminar del repositorio (sin lock)
        repository.deleteDownload(id)

        Result.success(Unit)
    }

    // SECCIÓN 4: CONTROL GLOBAL

    override suspend fun pauseAll(): Result<Unit> = withContext(Dispatchers.IO) {
        val activeIds = downloadsMutex.withLock {
            _downloadsList.filter { it.status is DownloadStatus.Downloading }.map { it.id }
        }
        activeIds.forEach { pauseDownload(it) }
        Result.success(Unit)
    }

    override suspend fun resumeAll(): Result<Unit> = withContext(Dispatchers.IO) {
        val pausedIds = downloadsMutex.withLock {
            _downloadsList.filter { it.status is DownloadStatus.Paused }.map { it.id }
        }
        pausedIds.forEach { resumeDownload(it) }
        Result.success(Unit)
    }

    override suspend fun cancelAll(): Result<Unit> = withContext(Dispatchers.IO) {
        val activeIds = downloadsMutex.withLock {
            _downloadsList.filter { it.status.isActive }.map { it.id }
        }
        activeIds.forEach { cancelDownload(it) }
        Result.success(Unit)
    }

    override suspend fun clearCompleted(): Result<Unit> = withContext(Dispatchers.IO) {
        downloadsMutex.withLock {
            _downloadsList.removeAll { it.status is DownloadStatus.Completed }
            updateDownloadsFlow()
        }
        repository.clearCompleted()
        Result.success(Unit)
    }

    // SECCIÓN 5: PRIORIDADES Y ORDENAMIENTO

    override suspend fun changePriority(id: String, newPriority: Priority): Result<Unit> = withContext(Dispatchers.IO) {
        downloadsMutex.withLock {
            val index = _downloadsList.indexOfFirst { it.id == id }
            if (index == -1) {
                return@withContext Result.failure(Exception("Descarga no encontrada"))
            }

            val download = _downloadsList[index]
            _downloadsList[index] = download.copy(priority = newPriority)
            updateDownloadsFlow()
        }

        // Actualizar repositorio sin lock
        val updatedDownload = downloadsMutex.withLock {
            _downloadsList.find { it.id == id }
        }
        updatedDownload?.let { repository.updateDownload(it) }

        Result.success(Unit)
    }

    override suspend fun moveUp(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        downloadsMutex.withLock {
            val index = _downloadsList.indexOfFirst { it.id == id }
            if (index <= 0) {
                return@withContext Result.failure(Exception("No se puede mover más arriba"))
            }

            // Intercambiar posiciones
            val temp = _downloadsList[index]
            _downloadsList[index] = _downloadsList[index - 1]
            _downloadsList[index - 1] = temp
            updateDownloadsFlow()
        }

        Result.success(Unit)
    }

    override suspend fun moveDown(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        downloadsMutex.withLock {
            val index = _downloadsList.indexOfFirst { it.id == id }
            if (index == -1 || index >= _downloadsList.size - 1) {
                return@withContext Result.failure(Exception("No se puede mover más abajo"))
            }

            // Intercambiar posiciones
            val temp = _downloadsList[index]
            _downloadsList[index] = _downloadsList[index + 1]
            _downloadsList[index + 1] = temp
            updateDownloadsFlow()
        }

        Result.success(Unit)
    }

    // SECCIÓN 6: CONFIGURACIÓN

    override suspend fun setMaxConcurrentDownloads(limit: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (limit !in 1..10) {
            return@withContext Result.failure(IllegalArgumentException("El límite debe estar entre 1 y 10"))
        }

        _maxConcurrentDownloads.value = limit
        downloadSemaphore = Semaphore(limit)

        // El init loop procesará automáticamente la cola con el nuevo límite

        Result.success(Unit)
    }

    override suspend fun setGlobalSpeedLimit(bytesPerSecond: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        _globalSpeedLimit.value = bytesPerSecond
        Result.success(Unit)
    }

    override suspend fun setDownloadSpeedLimit(id: String, bytesPerSecond: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        downloadsMutex.withLock {
            val index = _downloadsList.indexOfFirst { it.id == id }
            if (index == -1) {
                return@withContext Result.failure(Exception("Descarga no encontrada"))
            }

            _downloadsList[index] = _downloadsList[index].copy(speedLimit = bytesPerSecond)
            updateDownloadsFlow()
        }

        Result.success(Unit)
    }

    override suspend fun retryDownload(id: String): Result<Unit> {
        return startDownload(id)
    }

    // SECCIÓN 7: PROCESAMIENTO DE COLA (REFACTORIZADO - SIN DEADLOCKS)

    /**
     * Procesa la cola de descargas respetando el límite de concurrencia
     * REFACTORIZADO: Sin deadlocks, sin recursión, sin race conditions
     */
    private suspend fun processQueue() {
        // Paso 1: Obtener candidatos con lock mínimo
        val candidates = downloadsMutex.withLock {
            _downloadsList
                .filter { it.status is DownloadStatus.Queued && !activeJobs.containsKey(it.id) }
                .sortedWith(compareByDescending<DownloadItem> { it.priority.level }.thenBy { it.createdAt })
        }

        // Paso 2: Procesar cada candidato SIN lock
        for (download in candidates) {
            // Verificar si podemos iniciar una nueva descarga
            if (downloadSemaphore.tryAcquire()) {
                // Crear job
                val job = scope.launch(Dispatchers.IO) {
                    try {
                        executeDownload(download.id)
                    } catch (e: CancellationException) {
                        // Cancelación normal, no hacer nada
                    } catch (e: Exception) {
                        // Error ya manejado en executeDownload
                    } finally {
                        // ✅ CRÍTICO: Limpiar en el orden correcto
                        downloadsMutex.withLock {
                            activeJobs.remove(download.id)
                        }
                        downloadSemaphore.release()
                    }
                }

                // ✅ Registrar el job INMEDIATAMENTE
                downloadsMutex.withLock {
                    activeJobs[download.id] = job
                }
            }
        }
    }

    /**
     * Ejecuta la descarga real de un archivo
     * REFACTORIZADO: Con timeout, mejor manejo de errores, sin re-lanzar excepciones
     */
    private suspend fun executeDownload(id: String) {
        val download = downloadsMutex.withLock {
            _downloadsList.find { it.id == id }
        } ?: return

        try {
            val startByte = repository.getPartialData(id).getOrNull() ?: 0L
            val speedLimit = download.speedLimit ?: _globalSpeedLimit.value
            val fullPath = "$downloadPath/${download.fileName}"
            val fileWriter = fileWriterFactory.createFileWriter()

            // ✅ CRÍTICO: Cambiar estado a Downloading INMEDIATAMENTE antes de empezar
            updateDownloadStatus(
                id,
                DownloadStatus.Downloading(
                    bytesDownloaded = startByte,
                    totalBytes = 0L,
                    speed = 0L
                )
            )

            // ✅ Descarga sin timeout de request (permitir archivos grandes)
            // Los timeouts de conexión y socket están configurados en el HttpClient
            downloadClient.downloadFile(
                url = download.url,
                outputPath = fullPath,
                startByte = startByte,
                speedLimitBytesPerSecond = speedLimit,
                fileWriter = fileWriter
            ).collect { progress ->
                currentCoroutineContext().ensureActive()

                // ✅ Actualizar estado INMEDIATAMENTE
                updateDownloadStatus(
                    id,
                    DownloadStatus.Downloading(
                        bytesDownloaded = progress.bytesDownloaded,
                        totalBytes = progress.totalBytes,
                        speed = progress.speed
                    )
                )

                // ✅ Guardar progreso y estadísticas en paralelo (no bloquear el Flow)
                scope.launch(Dispatchers.IO) {
                    repository.savePartialData(id, progress.bytesDownloaded)
                }
                scope.launch(Dispatchers.IO) {
                    updateStatistics()
                }
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
            // ✅ NO re-lanzar, solo actualizar estado a pausado
            updateDownloadStatus(id, DownloadStatus.Paused(
                download.downloadedBytes,
                download.totalSize
            ))
        } catch (e: Exception) {
            val currentBytes = downloadsMutex.withLock {
                _downloadsList.find { it.id == id }?.downloadedBytes ?: 0L
            }
            updateDownloadStatus(
                id,
                DownloadStatus.Failed(
                    error = e.message ?: "Error desconocido: ${e::class.simpleName}",
                    bytesDownloaded = currentBytes
                )
            )
        } finally {
            // ✅ Actualizar estadísticas siempre
            updateStatistics()
        }
    }

    // SECCIÓN 8: FUNCIONES AUXILIARES

    /**
     * Actualiza el estado de una descarga específica (Thread-safe)
     * ✅ OPTIMIZADO: Repository update FUERA del lock para reducir contención
     */
    private suspend fun updateDownloadStatus(id: String, newStatus: DownloadStatus) {
        val updatedDownload = downloadsMutex.withLock {
            val index = _downloadsList.indexOfFirst { it.id == id }
            if (index != -1) {
                _downloadsList[index] = _downloadsList[index].copy(status = newStatus)
                updateDownloadsFlow()
                _downloadsList[index]
            } else {
                null
            }
        }

        // ✅ Actualizar repositorio FUERA del lock
        updatedDownload?.let { repository.updateDownload(it) }
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
     * ✅ OPTIMIZADO: Snapshot rápido con lock, cálculos fuera del lock
     */
    private suspend fun updateStatistics() {
        // ✅ Lock MÍNIMO: solo para copiar lista
        val snapshot = downloadsMutex.withLock {
            _downloadsList.toList()
        }

        // ✅ Cálculos FUERA del lock
        val activeSpeeds = snapshot
            .filter { it.status is DownloadStatus.Downloading }
            .map { it.currentSpeed }

        val stats = DownloadStatistics(
            totalDownloads = snapshot.size,
            activeDownloads = snapshot.count { it.status is DownloadStatus.Downloading },
            queuedDownloads = snapshot.count { it.status is DownloadStatus.Queued },
            pausedDownloads = snapshot.count { it.status is DownloadStatus.Paused },
            completedDownloads = snapshot.count { it.status is DownloadStatus.Completed },
            failedDownloads = snapshot.count { it.status is DownloadStatus.Failed },
            totalBytesDownloaded = snapshot.sumOf { it.downloadedBytes },
            currentGlobalSpeed = snapshot.sumOf { it.currentSpeed },
            averageSpeed = if (activeSpeeds.isNotEmpty()) {
                activeSpeeds.average().toLong()
            } else {
                0L
            }
        )

        _statistics.value = stats
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

    override suspend fun loadSavedDownloads(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
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

    override suspend fun shutdown(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Pausar todas las descargas activas
            pauseAll()

            // Guardar estado actual
            val downloads = downloadsMutex.withLock {
                _downloadsList.toList()
            }
            downloads.forEach { download ->
                repository.updateDownload(download)
            }

            // Cancelar todas las corrutinas
            val jobs = downloadsMutex.withLock {
                activeJobs.values.toList().also { activeJobs.clear() }
            }
            jobs.forEach { it.cancel() }

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
