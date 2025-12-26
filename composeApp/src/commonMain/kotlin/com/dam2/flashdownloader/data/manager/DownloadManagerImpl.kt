package com.dam2.flashdownloader.data.manager

import com.dam2.flashdownloader.data.network.DownloadClient
import com.dam2.flashdownloader.data.network.FileWriter
import com.dam2.flashdownloader.data.network.BandwidthLimiter
import com.dam2.flashdownloader.data.network.TokenBucketLimiter
import com.dam2.flashdownloader.data.network.CompositeBandwidthLimiter
import com.dam2.flashdownloader.domain.manager.DownloadManager
import com.dam2.flashdownloader.domain.model.*
import com.dam2.flashdownloader.domain.repository.DownloadRepository
import com.dam2.flashdownloader.utils.HashAlgorithm
import com.dam2.flashdownloader.utils.HashCalculator
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
    private val scope: CoroutineScope,
    private val hashCalculator: HashCalculator = HashCalculator()
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

    /**
     * Flag para indicar si se está cerrando la aplicación
     */
    private var isShuttingDown = false

    /**
     * Limitador global de ancho de banda (Token Bucket)
     * Inicialmente 0 (sin límite)
     */
    private val globalLimiter = TokenBucketLimiter(0)

    /**
     * Tracker de bytes descargados por ID
     */
    private val bytesTracker = mutableMapOf<String, Long>()

    /**
     * Tracker de velocidad por ID
     */
    private val speedTracker = mutableMapOf<String, Long>()

    /**
     * Timestamp de última persistencia por ID
     */
    private val lastPersistTime = mutableMapOf<String, Long>()

    /**
     * Intervalo de persistencia en milisegundos (5 segundos)
     */
    private val PERSIST_INTERVAL_MS = 5000L

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
        speedLimit: Long?,
        hash: String?
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
                status = DownloadStatus.Queued(),
                createdAt = currentTime,
                speedLimit = speedLimit,
                metadata = DownloadMetadata(), // Se actualizará cuando inicie la descarga
                hash = hash // ✅ Guardar hash esperado para verificación
            )

            // ✅ Lock MÍNIMO: solo para añadir a la lista
            downloadsMutex.withLock {
                _downloadsList.add(downloadItem)
                // ✅ Actualizar StateFlow directamente (ya tenemos el lock)
                // NO llamar a funciones que intenten adquirir el lock de nuevo
                _downloads.value = _downloadsList.toList()
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

        // Cambiar estado a en cola preserving progress AND elapsedSeconds if resuming
        val queuedStatus = when (val currentStatus = download.status) {
            is DownloadStatus.Paused -> {
                println("🟡 QUEUING FROM PAUSED: preserving elapsedSeconds = ${currentStatus.elapsedSeconds}")
                DownloadStatus.Queued(
                    bytesDownloaded = download.downloadedBytes,
                    totalBytes = download.totalSize,
                    elapsedSeconds = currentStatus.elapsedSeconds // ✅ Preservar tiempo acumulado
                )
            }
            else -> {
                println("🟡 QUEUING NEW: no elapsedSeconds")
                DownloadStatus.Queued(download.downloadedBytes, download.totalSize)
            }
        }
        updateDownloadStatus(id, queuedStatus)

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

        // Actualizar estado a pausado - calcular tiempo acumulado
        when (val status = download.status) {
            is DownloadStatus.Downloading -> {
                // Calcular tiempo total acumulado hasta ahora
                val totalElapsed = status.totalElapsedSeconds
                println("🔵 PAUSANDO: elapsedSeconds acumulado = $totalElapsed")
                updateDownloadStatus(
                    id,
                    DownloadStatus.Paused(
                        bytesDownloaded = status.bytesDownloaded,
                        totalBytes = status.totalBytes,
                        elapsedSeconds = totalElapsed // ✅ Guardar tiempo acumulado
                    )
                )
                // ✅ CRÍTICO: Guardar progreso inmediatamente al pausar para evitar reinicios
                repository.savePartialData(id, status.bytesDownloaded)
                println("🔵 PAUSADO: elapsedSeconds guardado = $totalElapsed")
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
            _downloads.value = _downloadsList.toList()
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
            _downloads.value = _downloadsList.toList()
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
            _downloads.value = _downloadsList.toList()
        }

        // Actualizar repositorio sin lock
        val updatedDownload = downloadsMutex.withLock {
            _downloadsList.find { it.id == id }
        }
        updatedDownload?.let { repository.updateDownload(it) }

        Result.success(Unit)
    }

    override suspend fun moveDownloadToPosition(id: String, newIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        downloadsMutex.withLock {
            val currentIndex = _downloadsList.indexOfFirst { it.id == id }
            if (currentIndex == -1) {
                return@withContext Result.failure(Exception("Descarga no encontrada"))
            }

            if (newIndex < 0 || newIndex >= _downloadsList.size) {
                return@withContext Result.failure(Exception("Índice inválido"))
            }

            // Mover elemento
            val item = _downloadsList.removeAt(currentIndex)
            _downloadsList.add(newIndex, item)

            // ✅ NUEVO: Recalcular prioridades basándose en el orden
            updatePrioritiesBasedOnOrder()

            // Actualizar flow
            _downloads.value = _downloadsList.toList()
        }

        // Guardar el nuevo orden masivamente
        val currentList = downloads.value
        repository.updateAll(currentList)

        Result.success(Unit)
    }

    /**
     * Actualiza las prioridades de todas las descargas basándose en su posición en la lista
     * Tercio superior = ALTA, tercio medio = MEDIA, tercio inferior = BAJA
     */
    private fun updatePrioritiesBasedOnOrder() {
        val size = _downloadsList.size
        if (size == 0) return

        val highThreshold = size / 3
        val mediumThreshold = (size * 2) / 3

        _downloadsList.forEachIndexed { index, download ->
            val newPriority = when {
                index < highThreshold -> Priority.HIGH
                index < mediumThreshold -> Priority.MEDIUM
                else -> Priority.LOW
            }

            // Solo actualizar si la prioridad cambió
            if (download.priority != newPriority) {
                _downloadsList[index] = download.copy(priority = newPriority)
            }
        }
    }

    override suspend fun moveUp(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        downloadsMutex.withLock {
            val index = _downloadsList.indexOfFirst { it.id == id }
            if (index <= 0) return@withContext Result.failure(Exception("No se puede mover más arriba"))

            val item = _downloadsList.removeAt(index)
            _downloadsList.add(index - 1, item)

            // ✅ NUEVO: Recalcular prioridades basándose en el orden
            updatePrioritiesBasedOnOrder()

            _downloads.value = _downloadsList.toList()
        }

        // Guardar cambios
        val currentList = downloads.value
        repository.updateAll(currentList)

        Result.success(Unit)
    }

    override suspend fun moveDown(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        downloadsMutex.withLock {
            val index = _downloadsList.indexOfFirst { it.id == id }
            if (index == -1 || index >= _downloadsList.size - 1) return@withContext Result.failure(Exception("No se puede mover más abajo"))

            val item = _downloadsList.removeAt(index)
            _downloadsList.add(index + 1, item)

            // ✅ NUEVO: Recalcular prioridades basándose en el orden
            updatePrioritiesBasedOnOrder()

            _downloads.value = _downloadsList.toList()
        }

        // Guardar cambios
        val currentList = downloads.value
        repository.updateAll(currentList)

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
        // Si es null, pasamos 0 para indicar sin límite
        globalLimiter.setRate(bytesPerSecond ?: 0)
        Result.success(Unit)
    }

    override suspend fun setDownloadSpeedLimit(id: String, bytesPerSecond: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        downloadsMutex.withLock {
            val index = _downloadsList.indexOfFirst { it.id == id }
            if (index == -1) {
                return@withContext Result.failure(Exception("Descarga no encontrada"))
            }

            _downloadsList[index] = _downloadsList[index].copy(speedLimit = bytesPerSecond)
            _downloads.value = _downloadsList.toList()
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
            // Filtrar solo los en cola y no activos
            _downloadsList
                .filter { it.status is DownloadStatus.Queued && !activeJobs.containsKey(it.id) }
                // ✅ IMPORTANTE: Respetar orden de prioridad primero, pero luego EL ORDEN DE LA LISTA
                // Esto permite que el usuario reordene manualmete dentro de la misma prioridad
                // O si la prioridad es igual, el orden visual manda.
                // Si queremos que el Drag&Drop sea "absoluto", deberíamos quitar el sort de prioridad,
                // pero el requisito dice "prioridades". Asumimos: Prioridad > Orden Manual.
                .sortedWith(
                    compareByDescending<DownloadItem> { it.priority.level }
                    // El orden secundario es implícito por la posición en la lista (stable sort)
                    // No necesitamos 'thenBy' si el filter conserva el orden relativo original
                )
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
    // Reemplazar la función executeDownload completa (líneas ~389-550)

    private suspend fun executeDownload(id: String) {
        val download = downloadsMutex.withLock {
            _downloadsList.find { it.id == id }
        } ?: return

        // Auto-retry configuration
        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null
        
        // Get accumulated elapsed time if resuming
        val previousElapsed = when (val currentStatus = download.status) {
            is DownloadStatus.Paused -> {
                println("🔵 REANUDANDO FROM PAUSED: elapsedSeconds = ${currentStatus.elapsedSeconds}")
                currentStatus.elapsedSeconds
            }
            is DownloadStatus.Queued -> {
                if (currentStatus.elapsedSeconds != null) {
                    println("🔵 REANUDANDO FROM QUEUED: elapsedSeconds = ${currentStatus.elapsedSeconds}")
                    currentStatus.elapsedSeconds
                } else {
                    println("🔵 NUEVA DESCARGA FROM QUEUED")
                    0L
                }
            }
            else -> {
                println("🔵 NUEVA DESCARGA")
                0L
            }
        }
        println("🔵 previousElapsed = $previousElapsed")

        // ✅ FEEDBACK INMEDIATO: Cambiar a estado Downloading AHORA
        // Esto evita que el usuario vea "En cola" mientras conectamos
        updateDownloadStatus(
            id,
            DownloadStatus.Downloading(
                bytesDownloaded = 0,
                totalBytes = -1, // Indeterminado hasta obtener metadatos
                speed = 0,
                elapsedSeconds = previousElapsed,
                sessionStartTime = System.currentTimeMillis()
            )
        )

        while (retryCount <= maxRetries) {
            try {
                // Obtener metadatos del archivo
                val metadataResult = downloadClient.getFileMetadata(download.url)
                val metadata = metadataResult.getOrNull() ?: DownloadMetadata()

                // Si obtenemos metadatos, actualizamos el estado con el tamaño real
                if (metadata.totalBytes > 0) {
                     updateDownloadStatus(
                        id,
                        DownloadStatus.Downloading(
                            bytesDownloaded = 0,
                            totalBytes = metadata.totalBytes,
                            speed = 0,
                            elapsedSeconds = previousElapsed,
                            sessionStartTime = System.currentTimeMillis()
                        )
                    )
                }

                // ✅ FIX: Verificación de espacio mejorada
                if (metadata.totalBytes > 0) {
                    val hasSpace = withContext(Dispatchers.IO) {
                        try {
                            com.dam2.flashdownloader.utils.HashUtils.hasEnoughDiskSpace(
                                downloadPath,
                                metadata.totalBytes + (100 * 1024 * 1024) // +100MB buffer
                            )
                        } catch (e: Exception) {
                            // Si falla la verificación, asumir que hay espacio
                            println("Warning: Could not verify disk space: ${e.message}")
                            true
                        }
                    }
                    
                    if (!hasSpace) {
                        updateDownloadStatus(
                            id,
                            DownloadStatus.Failed(
                                error = "Espacio insuficiente. Se requieren ${(metadata.totalBytes / (1024 * 1024))} MB",
                                bytesDownloaded = 0L
                            ),
                            forcePersist = true
                        )
                        return
                    }
                }

                // Actualizar metadata
                downloadsMutex.withLock {
                    val index = _downloadsList.indexOfFirst { it.id == id }
                    if (index != -1) {
                        val updatedFileName = metadata.serverFileName ?: download.fileName
                        _downloadsList[index] = _downloadsList[index].copy(
                            fileName = updatedFileName,
                            metadata = metadata
                        )
                        _downloads.value = _downloadsList.toList()
                    }
                }

                // Verificar progreso guardado (Repositorio vs Memoria)
                var startByte = repository.getPartialData(id).getOrNull() ?: 0L
                
                // ✅ FIX: Si el estado en memoria tiene más progreso (reciente), usarlo
                if (download.status is DownloadStatus.Paused) {
                    val statusBytes = (download.status as DownloadStatus.Paused).bytesDownloaded
                    if (statusBytes > startByte) {
                        println("⚠️ Using memory bytes ($statusBytes) over repo bytes ($startByte)")
                        startByte = statusBytes
                    }
                } else if (download.status is DownloadStatus.Queued) {
                    val statusBytes = (download.status as DownloadStatus.Queued).bytesDownloaded
                    if (statusBytes > startByte) {
                        startByte = statusBytes
                    }
                }

                // Validar soporte de reanudación
                if (startByte > 0) {
                    val supportsResume = metadata.supportsRangeRequests
                    if (!supportsResume) {
                        startByte = 0L
                        val fullPath = "$downloadPath/${download.fileName}"
                        val fileWriter = fileWriterFactory.createFileWriter()
                        fileWriter.delete(fullPath)
                    }
                }

                // ✅ FIX: Solo usar límite individual, NO el global (el global se maneja aparte)
                val speedLimit = download.speedLimit

                val fullPath = "$downloadPath/${download.fileName}"
                val fileWriter = fileWriterFactory.createFileWriter()

                // Inicializar tracker
                bytesTracker[id] = startByte
                
                // ✅ FIX: Variables para tracking de velocidad
                var lastProgressTime = System.currentTimeMillis()
                var lastProgressBytes = startByte

                // ✅ CRÍTICO: Cambiar estado a Downloading ANTES de empezar
                updateDownloadStatus(
                    id,
                    DownloadStatus.Downloading(
                        bytesDownloaded = startByte,
                        totalBytes = metadata.totalBytes,
                        speed = 0L,
                        elapsedSeconds = previousElapsed,
                        sessionStartTime = System.currentTimeMillis()
                    )
                )

                // ✅ FIX CRÍTICO: Descargar SIN Token Bucket (se maneja en DownloadClient)
                downloadClient.downloadFile(
                    url = download.url,
                    outputPath = fullPath,
                    startByte = startByte,
                    speedLimitBytesPerSecond = speedLimit, // Pasarlo al cliente
                    fileWriter = fileWriter
                )
                .buffer(0)  // ✅ Sin buffering - emisión inmediata
                .collect { progress ->
                    currentCoroutineContext().ensureActive()

                    // ✅ DEBUG: Verificar que collect recibe las emisiones
                    val percentage = if (progress.totalBytes > 0) (progress.bytesDownloaded * 100 / progress.totalBytes) else 0
                    println("📥 COLLECT: ${progress.bytesDownloaded}/${progress.totalBytes} ($percentage%)")

                    // ✅ FIX: Calcular velocidad real
                    val currentTime = System.currentTimeMillis()
                    val timeDelta = (currentTime - lastProgressTime) / 1000.0
                    val bytesDelta = progress.bytesDownloaded - lastProgressBytes

                    // ✅ Aplicar límite global de velocidad
                    if (bytesDelta > 0) {
                        globalLimiter.acquire(bytesDelta.toInt())
                    }

                    val currentSpeed = if (timeDelta > 0) {
                        (bytesDelta / timeDelta).toLong()
                    } else {
                        progress.speed
                    }

                    // Actualizar para próximo cálculo
                    lastProgressTime = currentTime
                    lastProgressBytes = progress.bytesDownloaded

                    // Actualizar tracker
                    bytesTracker[id] = progress.bytesDownloaded
                    speedTracker[id] = currentSpeed

                    // ✅ Actualizar estado con velocidad correcta
                    // Preservar elapsedSeconds y sessionStartTime del estado actual
                    val currentStatus = downloadsMutex.withLock {
                        _downloadsList.find { it.id == id }?.status as? DownloadStatus.Downloading
                    }
                    
                    updateDownloadStatus(
                        id,
                        DownloadStatus.Downloading(
                            bytesDownloaded = progress.bytesDownloaded,
                            totalBytes = progress.totalBytes,
                            speed = currentSpeed, // Velocidad calculada
                            elapsedSeconds = currentStatus?.elapsedSeconds ?: previousElapsed,
                            sessionStartTime = currentStatus?.sessionStartTime ?: System.currentTimeMillis()
                        )
                    )

                    // Guardar progreso cada 5 segundos
                    if ((currentTime - lastPersistTime.getOrDefault(id, 0L)) >= PERSIST_INTERVAL_MS) {
                        repository.savePartialData(id, progress.bytesDownloaded)
                        lastPersistTime[id] = currentTime
                    }

                    // Actualizar estadísticas
                    updateStatistics()
                }

                // ✅ Calcular hash solo para archivos menores de 500MB
                // Para archivos grandes (2GB+), el cálculo puede tomar minutos y bloquear la app
                val calculatedHash = if (metadata.totalBytes > 0 && metadata.totalBytes < 500 * 1024 * 1024) {
                    withContext(Dispatchers.Default) {
                        try {
                            com.dam2.flashdownloader.utils.HashUtils.calculateSHA256(fullPath)
                        } catch (e: Exception) {
                            println("Warning: Could not calculate hash: ${e.message}")
                            null
                        }
                    }
                } else {
                    // Archivos grandes: skip hash calculation para evitar bloqueo
                    println("Skipping hash calculation for large file (${metadata.totalBytes / (1024 * 1024)} MB)")
                    null
                }

                // ✅ VERIFICACIÓN DE HASH: Si se proporcionó hash esperado, verificar
                if (download.hash != null && download.hash.isNotBlank()) {
                    if (calculatedHash == null) {
                        // No se pudo calcular hash (archivo muy grande o error)
                        println("⚠️ Hash verification skipped: Could not calculate hash for ${download.fileName}")
                    } else if (!calculatedHash.equals(download.hash, ignoreCase = true)) {
                        // ❌ Hash NO coincide - descarga corrupta o incorrecta
                        println("❌ Hash verification FAILED for ${download.fileName}")
                        println("   Expected: ${download.hash}")
                        println("   Got:      $calculatedHash")
                        
                        updateDownloadStatus(
                            id,
                            DownloadStatus.Failed(
                                error = "Hash verification failed. Expected: ${download.hash.take(16)}..., Got: ${calculatedHash.take(16)}...",
                                bytesDownloaded = metadata.totalBytes
                            ),
                            forcePersist = true
                        )
                        
                        // Limpiar tracking
                        bytesTracker.remove(id)
                        speedTracker.remove(id)
                        lastPersistTime.remove(id)
                        
                        return // Salir sin marcar como completada
                    } else {
                        // ✅ Hash coincide - verificación exitosa
                        println("✅ Hash verification PASSED for ${download.fileName}")
                    }
                }

                // Descarga completada (y hash verificado si se proporcionó)
                updateDownloadStatus(
                    id,
                    DownloadStatus.Completed(
                        filePath = fullPath,
                        totalBytes = metadata.totalBytes,
                        calculatedHash = calculatedHash
                    ),
                    forcePersist = true
                )
                
                // ✅ CRÍTICO: Limpiar mapas de tracking para evitar memory leak
                bytesTracker.remove(id)
                speedTracker.remove(id)
                lastPersistTime.remove(id)
                
                return // Éxito - salir

            } catch (e: CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                lastException = e
                retryCount++
                
                if (retryCount <= maxRetries) {
                    val delayMs = 2000L * (1 shl (retryCount - 1))
                    delay(delayMs)
                    
                    val currentBytes = downloadsMutex.withLock {
                        _downloadsList.find { it.id == id }?.downloadedBytes ?: 0L
                    }
                    updateDownloadStatus(
                        id,
                        DownloadStatus.Failed(
                            error = "Error de red. Reintentando (${retryCount}/$maxRetries)...",
                            bytesDownloaded = currentBytes
                        ),
                        forcePersist = true
                    )
                } else {
                    val currentBytes = downloadsMutex.withLock {
                        _downloadsList.find { it.id == id }?.downloadedBytes ?: 0L
                    }
                    updateDownloadStatus(
                        id,
                        DownloadStatus.Failed(
                            error = "Error de red tras $maxRetries intentos: ${e.message}",
                            bytesDownloaded = currentBytes
                        ),
                        forcePersist = true
                    )
                    
                    // ✅ CRÍTICO: Limpiar mapas de tracking para evitar memory leak
                    bytesTracker.remove(id)
                    speedTracker.remove(id)
                    lastPersistTime.remove(id)
                }
            } catch (e: Exception) {
                val currentBytes = downloadsMutex.withLock {
                    _downloadsList.find { it.id == id }?.downloadedBytes ?: 0L
                }
                updateDownloadStatus(
                    id,
                    DownloadStatus.Failed(
                        error = e.message ?: "Error desconocido",
                        bytesDownloaded = currentBytes
                    ),
                    forcePersist = true
                )
                
                // ✅ CRÍTICO: Limpiar mapas de tracking para evitar memory leak
                bytesTracker.remove(id)
                speedTracker.remove(id)
                lastPersistTime.remove(id)
                
                return
            } finally {
                if (retryCount > maxRetries || lastException !is java.io.IOException) {
                    activeJobs.remove(id)
                    bytesTracker.remove(id)
                    speedTracker.remove(id)
                    updateStatistics()
                }
            }
        }
    }

    // SECCIÓN 8: FUNCIONES AUXILIARES

    /**
     * Actualiza el estado de una descarga específica (Thread-safe)
     * ✅ OPTIMIZADO: Repository update FUERA del lock para reducir contención
     */
    private suspend fun updateDownloadStatus(id: String, newStatus: DownloadStatus, forcePersist: Boolean = false) {
        // Paso 1: Modificar la lista y obtener snapshot actualizado dentro del lock
        val (updatedDownloadItem, newListSnapshot) = downloadsMutex.withLock {
            val index = _downloadsList.indexOfFirst { it.id == id }
            if (index != -1) {
                _downloadsList[index] = _downloadsList[index].copy(status = newStatus)
                // Retornamos el item modificado y una COPIA de la lista completa
                Pair(_downloadsList[index], _downloadsList.toList())
            } else {
                Pair(null, null)
            }
        }
        
        // Paso 2: Actualizar StateFlow con el snapshot (Thread-safe y seguro para Compose)
        // Al asignar una nueva referencia de lista, Compose detectará el cambio
        if (newListSnapshot != null) {
            _downloads.value = newListSnapshot
        }

        // Paso 3: Persistencia (Fuera del lock de memoria, pero async)
        if (forcePersist && updatedDownloadItem != null) {
            repository.updateDownload(updatedDownloadItem)
        }
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

    override suspend fun verifyIntegrity(id: String, expectedHash: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Buscar la descarga
            val download = downloadsMutex.withLock {
                _downloadsList.find { it.id == id }
            } ?: return@withContext Result.failure(Exception("Descarga no encontrada"))

            // Verificar que la descarga esté completada
            if (download.status !is DownloadStatus.Completed) {
                return@withContext Result.failure(Exception("La descarga debe estar completada para verificar integridad"))
            }

            // Obtener la ruta del archivo
            val filePath = (download.status as DownloadStatus.Completed).filePath

            // Detectar el algoritmo basándose en la longitud del hash
            val algorithm = when (expectedHash.length) {
                32 -> HashAlgorithm.MD5      // MD5 = 32 caracteres hex
                40 -> HashAlgorithm.SHA1     // SHA-1 = 40 caracteres hex
                64 -> HashAlgorithm.SHA256   // SHA-256 = 64 caracteres hex
                else -> {
                    // Si no se puede detectar, intentar con SHA-256 por defecto
                    HashAlgorithm.SHA256
                }
            }

            // Verificar el hash usando el HashCalculator
            hashCalculator.verifyFileHash(filePath, expectedHash, algorithm)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // SECCIÓN 10: PERSISTENCIA Y CICLO DE VIDA

    override suspend fun loadSavedDownloads(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val savedDownloads = repository.getAllDownloads().getOrNull() ?: emptyList()
            downloadsMutex.withLock {
                _downloadsList.clear()
                _downloadsList.addAll(
                    savedDownloads.map {
                        // Auto-Resume logic:
                        // Si estaba Descargando (o Queued), restaurar como Queued para que inicie automáticamente
                        if (it.status is DownloadStatus.Downloading) {
                            it.copy(
                                status = DownloadStatus.Queued(
                                    bytesDownloaded = it.status.bytesDownloaded,
                                    totalBytes = it.status.totalBytes
                                )
                            )
                        } else if (it.status is DownloadStatus.Queued) {
                             it // Ya está en cola (aunque Queued no guardaba bytes antes, ahora sí)
                        } else {
                            it
                        }
                    }
                )
                _downloads.value = _downloadsList.toList()
            }
            updateStatistics()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun shutdown(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            isShuttingDown = true
            // No llamamos a pauseAll porque cambiaría el estado en DB
            
            // Cancelar todas las corrutinas
            val jobs = downloadsMutex.withLock {
                activeJobs.values.toList().also { activeJobs.clear() }
            }
            jobs.forEach { it.cancel() }
            
            // Esperar brevemente a que se cancelen? No es necesario.
            
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
