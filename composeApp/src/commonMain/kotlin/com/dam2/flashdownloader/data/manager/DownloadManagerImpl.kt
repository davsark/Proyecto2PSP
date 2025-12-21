package com.dam2.flashdownloader.data.manager

import com.dam2.flashdownloader.data.network.DownloadClient
import com.dam2.flashdownloader.data.network.FileWriter
import com.dam2.flashdownloader.domain.manager.DownloadManager
import com.dam2.flashdownloader.domain.model.*
import com.dam2.flashdownloader.domain.repository.DownloadRepository
import com.dam2.flashdownloader.domain.repository.SettingsRepository
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
 * - Token Bucket para límite de velocidad real
 * - Validación de reanudación con HEAD request
 * - Estadísticas en tiempo real
 */
class DownloadManagerImpl(
    private val downloadClient: DownloadClient,
    private val repository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
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

    // Estadísticas
    private val _statistics = MutableStateFlow(DownloadStatistics())
    override val statistics: StateFlow<DownloadStatistics> = _statistics.asStateFlow()

    // Job para actualizar estadísticas
    private var statisticsJob: Job? = null

    // Configuración
    private val _maxConcurrentDownloads = MutableStateFlow(3)
    override val maxConcurrentDownloads: StateFlow<Int> = _maxConcurrentDownloads.asStateFlow()

    private val _globalSpeedLimit = MutableStateFlow<Long?>(null)
    override val globalSpeedLimit: StateFlow<Long?> = _globalSpeedLimit.asStateFlow()

    // Tracker de bytes para cálculo de velocidad
    private val bytesTracker = mutableMapOf<String, Long>()
    private val speedTracker = mutableMapOf<String, Long>()
    private var lastSpeedUpdate = System.currentTimeMillis()

    // Optimización de persistencia - evitar DB spam
    private val lastPersistTime = mutableMapOf<String, Long>()
    private companion object {
        const val PERSIST_INTERVAL_MS = 5000L // 5 segundos
    }

    init {
        // Cargar configuración guardada
        _maxConcurrentDownloads.value = settingsRepository.getMaxConcurrentDownloads()
        _globalSpeedLimit.value = settingsRepository.getGlobalSpeedLimit()
        
        // Actualizar semaphore con el valor cargado
        downloadSemaphore = Semaphore(_maxConcurrentDownloads.value)
        
        // Iniciar actualizador de estadísticas
        startStatisticsUpdater()
    }

    // SECCIÓN 2: AÑADIR DESCARGAS

    /**
     * FIX 1: Eliminada la llamada bloqueante a getFileMetadata
     * Los metadatos se obtienen ahora en executeDownload
     */
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

            // Crear item de descarga con metadatos vacíos (se actualizarán durante la descarga)
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
        updateDownloadStatus(id, DownloadStatus.Cancelled, forcePersist = true)

        Result.success(Unit)
    }

    /**
     * FIX 3: Añadido parámetro deleteFile para borrado físico
     */
    override suspend fun removeDownload(id: String, deleteFile: Boolean): Result<Unit> = downloadsMutex.withLock {
        // Cancelar si está activo
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        // Obtener información del archivo antes de eliminar
        val download = _downloadsList.find { it.id == id }
        
        // Eliminar archivo físico si se solicita
        if (deleteFile && download != null) {
            val fullPath = "$downloadPath/${download.fileName}"
            val fileWriter = fileWriterFactory.createFileWriter()
            fileWriter.delete(fullPath)
        }

        // Eliminar de la lista
        _downloadsList.removeAll { it.id == id }
        updateDownloadsFlow()

        // Eliminar del repositorio
        repository.deleteDownload(id)

        // Limpiar tracker de bytes
        bytesTracker.remove(id)

        Result.success(Unit)
    }

    // SECCIÓN 4: CONTROL GLOBAL

    /**
     * FIX 6: Implementación de pauseAllDownloads
     */
    override suspend fun pauseAllDownloads(): Result<Unit> = downloadsMutex.withLock {
        val activeDownloads = _downloadsList.filter { it.status is DownloadStatus.Downloading }
        activeDownloads.forEach { download ->
            pauseDownload(download.id)
        }
        Result.success(Unit)
    }

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
    
    override suspend fun reorderDownload(fromIndex: Int, toIndex: Int): Result<Unit> = downloadsMutex.withLock {
        // Validar índices
        if (fromIndex < 0 || fromIndex >= _downloadsList.size) {
            return Result.failure(IllegalArgumentException("Índice de origen inválido: $fromIndex"))
        }
        if (toIndex < 0 || toIndex >= _downloadsList.size) {
            return Result.failure(IllegalArgumentException("Índice de destino inválido: $toIndex"))
        }
        if (fromIndex == toIndex) {
            return Result.success(Unit) // No hay cambio
        }
        
        // Mover el elemento
        val item = _downloadsList.removeAt(fromIndex)
        _downloadsList.add(toIndex, item)
        
        // Actualizar UI
        updateDownloadsFlow()
        
        // Persistir cambios
        _downloadsList.forEach { download ->
            repository.updateDownload(download)
        }
        
        Result.success(Unit)
    }

    // SECCIÓN 6: CONFIGURACIÓN (methods moved to SECCIÓN 11)

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

    // SECCIÓN 7: EJECUCIÓN DE DESCARGAS

    private suspend fun executeDownload(id: String) {
        val download = downloadsMutex.withLock {
            _downloadsList.find { it.id == id }
        } ?: return

        // Auto-retry configuration
        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null
        
        // Record start time for elapsed time tracking
        val downloadStartTime = System.currentTimeMillis()

        while (retryCount <= maxRetries) {
            try {
                // FIX 1: Obtener metadatos del archivo al inicio (no en addDownload)
                val metadataResult = downloadClient.getFileMetadata(download.url)
                val metadata = metadataResult.getOrNull() ?: DownloadMetadata()

                // ADVANCED: Pre-check disk space
                if (metadata.totalBytes > 0) {
                    val hasSpace = withContext(Dispatchers.IO) {
                        com.dam2.flashdownloader.utils.HashUtils.hasEnoughDiskSpace(
                            downloadPath,
                            metadata.totalBytes
                        )
                    }
                    
                    if (!hasSpace) {
                        updateDownloadStatus(
                            id,
                            DownloadStatus.Failed(
                                error = "Espacio insuficiente en disco. Se requieren ${metadata.totalBytes / (1024 * 1024)} MB",
                                bytesDownloaded = 0L
                            )
                        )
                        return
                    }
                }

                // Actualizar el item con los metadatos obtenidos
                downloadsMutex.withLock {
                    val index = _downloadsList.indexOfFirst { it.id == id }
                    if (index != -1) {
                        val updatedFileName = metadata.serverFileName ?: download.fileName
                        _downloadsList[index] = _downloadsList[index].copy(
                            fileName = updatedFileName,
                            metadata = metadata
                        )
                        updateDownloadsFlow()
                    }
                }

                // Verificar si hay progreso guardado (para reanudar)
                var startByte = repository.getPartialData(id).getOrNull() ?: 0L

                // FIX 4: Validar soporte de reanudación antes de reanudar
                if (startByte > 0) {
                    val supportsResume = metadata.supportsRangeRequests
                    if (!supportsResume) {
                        // El servidor no soporta reanudación, reiniciar desde 0
                        startByte = 0L
                        // Eliminar archivo parcial
                        val fullPath = "$downloadPath/${download.fileName}"
                        val fileWriter = fileWriterFactory.createFileWriter()
                        fileWriter.delete(fullPath)
                    }
                }

                // Determinar límite de velocidad (individual o global)
                val speedLimit = download.speedLimit ?: _globalSpeedLimit.value

                // Construir ruta completa del archivo
                val fullPath = "$downloadPath/${download.fileName}"

                // Crear FileWriter
                val fileWriter = fileWriterFactory.createFileWriter()

                // FIX 2: Variables para Token Bucket
                var tokenBucket = 0.0 // Tokens disponibles
                var lastTokenRefill = System.currentTimeMillis()

                // Inicializar tracker de bytes
                bytesTracker[id] = startByte

                // Iniciar descarga y recolectar progreso
                downloadClient.downloadFile(
                    url = download.url,
                    outputPath = fullPath,
                    startByte = startByte,
                    speedLimitBytesPerSecond = null, // Manejamos el límite aquí con Token Bucket
                    fileWriter = fileWriter
                ).collect { progress ->
                    // Verificar cancelación
                    currentCoroutineContext().ensureActive()

                    // FIX 2: Aplicar Token Bucket para límite de velocidad
                    if (speedLimit != null && speedLimit > 0) {
                        val currentTime = System.currentTimeMillis()
                        val timeDelta = (currentTime - lastTokenRefill) / 1000.0 // segundos

                        // Rellenar tokens basado en el tiempo transcurrido
                        tokenBucket += timeDelta * speedLimit
                        // Limitar el bucket al máximo de 1 segundo de datos
                        if (tokenBucket > speedLimit) {
                            tokenBucket = speedLimit.toDouble()
                        }
                        lastTokenRefill = currentTime

                        // Calcular bytes desde última actualización
                        val previousBytes = bytesTracker[id] ?: 0L
                        val bytesInThisChunk = progress.bytesDownloaded - previousBytes

                        // Consumir tokens
                        tokenBucket -= bytesInThisChunk

                        // Si no hay suficientes tokens, esperar
                        if (tokenBucket < 0) {
                            val deficit = -tokenBucket
                            val delayMs = ((deficit / speedLimit) * 1000).toLong()
                            delay(delayMs)
                            tokenBucket = 0.0
                        }
                    }

                    // Actualizar tracker
                    bytesTracker[id] = progress.bytesDownloaded

                    // Actualizar estado con progreso (preservando startTime)
                    updateDownloadStatus(
                        id,
                        DownloadStatus.Downloading(
                            bytesDownloaded = progress.bytesDownloaded,
                            totalBytes = progress.totalBytes,
                            speed = progress.speed,
                            startTime = downloadStartTime
                        )
                    )

                    // Guardar progreso para poder reanudar
                    repository.savePartialData(id, progress.bytesDownloaded)

                    // Actualizar estadísticas globales
                    updateStatistics()
                }

                // ADVANCED: Calculate SHA-256 hash after successful download
                val calculatedHash = withContext(Dispatchers.Default) {
                    com.dam2.flashdownloader.utils.HashUtils.calculateSHA256(fullPath)
                }

                // Descarga completada con hash
                updateDownloadStatus(
                    id,
                    DownloadStatus.Completed(
                        filePath = fullPath,
                        totalBytes = metadata.totalBytes,
                        calculatedHash = calculatedHash
                    ),
                    forcePersist = true  // ✅ Persistir inmediatamente
                )
                
                // Success - exit retry loop
                return

            } catch (e: CancellationException) {
                // Descarga cancelada por el usuario (comportamiento esperado)
                throw e
            } catch (e: java.io.IOException) {
                // Network error - retry
                lastException = e
                retryCount++
                
                if (retryCount <= maxRetries) {
                    // Wait before retry (exponential backoff: 2s, 4s, 8s)
                    val delayMs = 2000L * (1 shl (retryCount - 1))
                    delay(delayMs)
                    
                    // Update status to show retry attempt
                    val currentBytes = downloadsMutex.withLock {
                        _downloadsList.find { it.id == id }?.downloadedBytes ?: 0L
                    }
                    updateDownloadStatus(
                        id,
                        DownloadStatus.Failed(
                            error = "Error de red. Reintentando (${retryCount}/$maxRetries)...",
                            bytesDownloaded = currentBytes
                        ),
                        forcePersist = true  // ✅ Persistir estado de reintento
                    )
                } else {
                    // Max retries exceeded
                    val currentBytes = downloadsMutex.withLock {
                        _downloadsList.find { it.id == id }?.downloadedBytes ?: 0L
                    }
                    updateDownloadStatus(
                        id,
                        DownloadStatus.Failed(
                            error = "Error de red tras $maxRetries intentos: ${e.message}",
                            bytesDownloaded = currentBytes
                        ),
                        forcePersist = true  // ✅ Persistir fallo final
                    )
                }
            } catch (e: Exception) {
                // Other errors - don't retry
                val currentBytes = downloadsMutex.withLock {
                    _downloadsList.find { it.id == id }?.downloadedBytes ?: 0L
                }
                updateDownloadStatus(
                    id,
                    DownloadStatus.Failed(
                        error = e.message ?: "Error desconocido",
                        bytesDownloaded = currentBytes
                    ),
                    forcePersist = true  // ✅ Persistir error
                )
                return
            } finally {
                if (retryCount > maxRetries || lastException !is java.io.IOException) {
                    activeJobs.remove(id)
                    bytesTracker.remove(id)
                    updateStatistics()
                }
            }
        }
    }

    // SECCIÓN 8: FUNCIONES AUXILIARES

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
     * FIX 5: Implementación real del actualizador de estadísticas
     * Calcula la velocidad global sumando los bytes descargados en el último segundo
     */
    private fun startStatisticsUpdater() {
        statisticsJob = scope.launch(Dispatchers.IO) {
            val previousBytes = mutableMapOf<String, Long>()
            
            while (isActive) {
                delay(1000) // Actualizar cada segundo

                downloadsMutex.withLock {
                    var globalSpeed = 0L

                    // Calcular velocidad para cada descarga activa
                    _downloadsList.filter { it.status is DownloadStatus.Downloading }.forEach { download ->
                        val currentBytes = download.downloadedBytes
                        val prevBytes = previousBytes[download.id] ?: currentBytes
                        val bytesInLastSecond = currentBytes - prevBytes
                        globalSpeed += bytesInLastSecond
                        previousBytes[download.id] = currentBytes
                    }

                    // Limpiar entradas de descargas que ya no están activas
                    val activeIds = _downloadsList
                        .filter { it.status is DownloadStatus.Downloading }
                        .map { it.id }
                        .toSet()
                    previousBytes.keys.retainAll(activeIds)

                    // Actualizar estadísticas con la velocidad calculada
                    val stats = _statistics.value.copy(
                        currentGlobalSpeed = globalSpeed
                    )
                    _statistics.value = stats
                }
            }
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

    // SECCIÓN 9: ACTUALIZACIÓN DE ESTADO Y PERSISTENCIA

    /**
     * Actualiza el estado de una descarga con persistencia optimizada
     * 
     * @param id ID de la descarga
     * @param status Nuevo estado
     * @param forcePersist Si es true, persiste inmediatamente sin importar el tiempo
     */
    private suspend fun updateDownloadStatus(
        id: String,
        status: DownloadStatus,
        forcePersist: Boolean = false
    ) {
        downloadsMutex.withLock {
            val index = _downloadsList.indexOfFirst { it.id == id }
            if (index != -1) {
                // ✅ SIEMPRE actualiza en memoria (UI fluida)
                _downloadsList[index] = _downloadsList[index].copy(status = status)
                updateDownloadsFlow()
                
                // ✅ Persistencia inteligente
                val shouldPersist = forcePersist || 
                    status.isCriticalState() ||
                    shouldPersistByTime(id)
                
                if (shouldPersist) {
                    // Persistir en BD (en IO dispatcher)
                    withContext(Dispatchers.IO) {
                        try {
                            repository.updateDownload(_downloadsList[index])
                            lastPersistTime[id] = System.currentTimeMillis()
                        } catch (e: Exception) {
                            // Log error pero no bloquear UI
                            println("Error persisting download $id: ${e.message}")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Verifica si debe persistir basado en el tiempo transcurrido
     */
    private fun shouldPersistByTime(id: String): Boolean {
        val lastTime = lastPersistTime[id] ?: 0L
        return (System.currentTimeMillis() - lastTime) >= PERSIST_INTERVAL_MS
    }
    
    /**
     * Determina si un estado es crítico y debe persistirse inmediatamente
     */
    private fun DownloadStatus.isCriticalState(): Boolean = when (this) {
        is DownloadStatus.Completed,
        is DownloadStatus.Failed,
        is DownloadStatus.Cancelled,
        is DownloadStatus.Paused -> true
        else -> false
    }

    // SECCIÓN 11: VERIFICACIÓN DE INTEGRIDAD

    override suspend fun verifyIntegrity(id: String, expectedHash: String): Result<Boolean> {
        // La verificación de integridad se realiza automáticamente al completar la descarga
        // El hash SHA-256 se calcula y almacena en DownloadStatus.Completed.calculatedHash
        val download = downloadsMutex.withLock {
            _downloadsList.find { it.id == id }
        } ?: return Result.failure(Exception("Descarga no encontrada"))
        
        return when (val status = download.status) {
            is DownloadStatus.Completed -> {
                val calculatedHash = status.calculatedHash
                if (calculatedHash == null) {
                    Result.failure(Exception("Hash no disponible"))
                } else {
                    Result.success(calculatedHash.equals(expectedHash, ignoreCase = true))
                }
            }
            else -> Result.failure(Exception("La descarga no está completada"))
        }
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

    // SECCIÓN 11: CONFIGURACIÓN

    override suspend fun setMaxConcurrentDownloads(limit: Int): Result<Unit> {
        return try {
            if (limit < 1) {
                return Result.failure(IllegalArgumentException("El límite debe ser al menos 1"))
            }
            
            _maxConcurrentDownloads.value = limit
            downloadSemaphore = Semaphore(limit)
            
            // Guardar en repositorio
            settingsRepository.setMaxConcurrentDownloads(limit)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setGlobalSpeedLimit(bytesPerSecond: Long?): Result<Unit> {
        return try {
            _globalSpeedLimit.value = bytesPerSecond
            
            // Guardar en repositorio
            settingsRepository.setGlobalSpeedLimit(bytesPerSecond)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun shutdown(): Result<Unit> {

        return try {
            // Detener actualizador de estadísticas
            statisticsJob?.cancel()
            statisticsJob = null

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
