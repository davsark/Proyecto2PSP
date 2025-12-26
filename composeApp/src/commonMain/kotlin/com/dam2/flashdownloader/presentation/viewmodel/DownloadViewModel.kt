package com.dam2.flashdownloader.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dam2.flashdownloader.domain.manager.DownloadManager
import com.dam2.flashdownloader.domain.model.*
import com.dam2.flashdownloader.utils.ClipboardManager
import com.dam2.flashdownloader.utils.extractUrls
import com.dam2.flashdownloader.utils.isValidUrl
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel principal compartido entre Android y Desktop
 * Gestiona el estado de la UI y coordina con el DownloadManager
 */
class DownloadViewModel(
    private val downloadManager: DownloadManager,
    private val clipboardManager: ClipboardManager
) : ViewModel() {

    // SECCIÓN 1: ESTADO EXPUESTO A LA UI

    /**
     * Lista de descargas ordenadas por prioridad
     */
    val downloads: StateFlow<List<DownloadItem>> = downloadManager.downloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,  // ✅ CRÍTICO: Siempre activo para actualizaciones en tiempo real
            initialValue = emptyList()
        )

    /**
     * Estadísticas globales
     */
    val statistics: StateFlow<DownloadStatistics> = downloadManager.statistics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadStatistics()
        )

    /**
     * Límite de descargas simultáneas
     */
    val maxConcurrentDownloads: StateFlow<Int> = downloadManager.maxConcurrentDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 3
        )

    /**
     * Límite de velocidad global
     */
    val globalSpeedLimit: StateFlow<Long?> = downloadManager.globalSpeedLimit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Estado de la UI (diálogos, mensajes, etc.)
     */
    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    /**
     * Eventos de UI (mensajes únicos, snackbars, etc.)
     */
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    /**
     * Tema de la aplicación (Claro/Oscuro)
     */
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // SECCIÓN 2: INICIALIZACIÓN

    init {
        loadSavedDownloads()
        observeClipboard()
    }

    /**
     * Carga las descargas guardadas al iniciar
     */
    private fun loadSavedDownloads() {
        viewModelScope.launch {
            downloadManager.loadSavedDownloads()
                .onFailure { error ->
                    emitEvent(UiEvent.Error("Error al cargar descargas: ${error.message}"))
                }
        }
    }

    /**
     * Observa el portapapeles para detectar URLs automáticamente
     */
    private fun observeClipboard() {
        viewModelScope.launch {
            // Revisar portapapeles cada 2 segundos si está habilitado
            while (true) {
                if (_uiState.value.autoDetectClipboard) {
                    try {
                        if (clipboardManager.hasUrl()) {
                            val text = clipboardManager.getText()
                            text?.let {
                                val urls = it.extractUrls()
                                if (urls.isNotEmpty() && urls.first() != _uiState.value.lastDetectedUrl) {
                                    _uiState.update { state ->
                                        state.copy(
                                            showClipboardSuggestion = true,
                                            suggestedUrl = urls.first(),
                                            lastDetectedUrl = urls.first()
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignorar errores del portapapeles
                    }
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    // SECCIÓN 3: ACCIONES DE DESCARGA

    /**
     * Añade una nueva descarga desde URL
     */
    fun addDownload(
        url: String,
        fileName: String? = null,
        category: Category? = null,
        priority: Priority = Priority.MEDIUM,
        speedLimit: Long? = null,
        hash: String? = null
    ) {
        viewModelScope.launch {
            if (!url.isValidUrl()) {
                emitEvent(UiEvent.Error("URL inválida"))
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }

            downloadManager.addDownload(url, fileName, category, priority, speedLimit, hash)
                .onSuccess { id ->
                    emitEvent(UiEvent.Success("Descarga añadida correctamente"))
                    dismissAddDownloadDialog()
                }
                .onFailure { error ->
                    emitEvent(UiEvent.Error("Error al añadir descarga: ${error.message}"))
                }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Añade múltiples descargas desde texto (detecta URLs)
     */
    fun addMultipleDownloads(text: String, priority: Priority = Priority.MEDIUM) {
        viewModelScope.launch {
            val urls = text.extractUrls()
            if (urls.isEmpty()) {
                emitEvent(UiEvent.Error("No se encontraron URLs válidas"))
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }

            downloadManager.addMultipleDownloads(urls, priority)
                .onSuccess { ids ->
                    emitEvent(UiEvent.Success("${ids.size} descargas añadidas"))
                    dismissAddDownloadDialog()
                }
                .onFailure { error ->
                    emitEvent(UiEvent.Error("Error: ${error.message}"))
                }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Añade descarga desde el portapapeles
     */
    fun addFromClipboard() {
        viewModelScope.launch {
            val text = clipboardManager.getText()
            if (text.isNullOrBlank()) {
                emitEvent(UiEvent.Error("El portapapeles está vacío"))
                return@launch
            }

            addMultipleDownloads(text)
        }
    }

    /**
     * Pausa una descarga
     */
    fun pauseDownload(id: String) {
        viewModelScope.launch {
            downloadManager.pauseDownload(id)
                .onFailure { error ->
                    emitEvent(UiEvent.Error("Error al pausar: ${error.message}"))
                }
        }
    }

    /**
     * Reanuda una descarga
     */
    fun resumeDownload(id: String) {
        viewModelScope.launch {
            downloadManager.resumeDownload(id)
                .onFailure { error ->
                    emitEvent(UiEvent.Error("Error al reanudar: ${error.message}"))
                }
        }
    }

    /**
     * Cancela una descarga
     */
    fun cancelDownload(id: String) {
        viewModelScope.launch {
            downloadManager.cancelDownload(id)
                .onSuccess {
                    emitEvent(UiEvent.Info("Descarga cancelada"))
                }
                .onFailure { error ->
                    emitEvent(UiEvent.Error("Error al cancelar: ${error.message}"))
                }
        }
    }

    /**
     * Elimina una descarga
     */
    fun removeDownload(id: String) {
        viewModelScope.launch {
            downloadManager.removeDownload(id)
                .onSuccess {
                    emitEvent(UiEvent.Info("Descarga eliminada"))
                }
                .onFailure { error ->
                    emitEvent(UiEvent.Error("Error al eliminar: ${error.message}"))
                }
        }
    }

    /**
     * Abre la carpeta donde está el archivo descargado
     */
    fun openFileLocation(id: String) {
        viewModelScope.launch {
            val download = filteredDownloads.value.find { it.id == id }
            if (download == null) {
                emitEvent(UiEvent.Error("Descarga no encontrada"))
                return@launch
            }

            val filePath = when (val status = download.status) {
                is com.dam2.flashdownloader.domain.model.DownloadStatus.Completed -> status.filePath
                else -> {
                    emitEvent(UiEvent.Error("La descarga debe estar completada para abrir su ubicación"))
                    return@launch
                }
            }

            val success = com.dam2.flashdownloader.utils.FileOpener.openFileLocation(filePath)
            if (success) {
                emitEvent(UiEvent.Success("Carpeta abierta"))
            } else {
                emitEvent(UiEvent.Error("No se pudo abrir la carpeta del archivo"))
            }
        }
    }

    /**
     * Reintenta una descarga fallida
     */
    fun retryDownload(id: String) {
        viewModelScope.launch {
            downloadManager.retryDownload(id)
                .onFailure { error ->
                    emitEvent(UiEvent.Error("Error al reintentar: ${error.message}"))
                }
        }
    }

    // SECCIÓN 4: CONTROL GLOBAL

    /**
     * Pausa todas las descargas
     */
    fun pauseAll() {
        viewModelScope.launch {
            downloadManager.pauseAll()
                .onSuccess {
                    emitEvent(UiEvent.Info("Todas las descargas pausadas"))
                }
        }
    }

    /**
     * Reanuda todas las descargas
     */
    fun resumeAll() {
        viewModelScope.launch {
            downloadManager.resumeAll()
                .onSuccess {
                    emitEvent(UiEvent.Info("Descargas reanudadas"))
                }
        }
    }

    /**
     * Cancela todas las descargas
     */
    fun cancelAll() {
        viewModelScope.launch {
            downloadManager.cancelAll()
                .onSuccess {
                    emitEvent(UiEvent.Info("Todas las descargas canceladas"))
                }
        }
    }

    /**
     * Limpia todas las descargas completadas
     */
    fun clearCompleted() {
        viewModelScope.launch {
            downloadManager.clearCompleted()
                .onSuccess {
                    emitEvent(UiEvent.Success("Descargas completadas eliminadas"))
                }
        }
    }

    // SECCIÓN 5: PRIORIDADES Y ORDEN

    /**
     * Cambia la prioridad de una descarga
     */
    fun changePriority(id: String, newPriority: Priority) {
        viewModelScope.launch {
            downloadManager.changePriority(id, newPriority)
                .onFailure { error ->
                    emitEvent(UiEvent.Error("Error: ${error.message}"))
                }
        }
    }

    /**
     * Mueve una descarga hacia arriba en la lista
     */
    fun moveDownloadUp(id: String) {
        viewModelScope.launch {
            downloadManager.moveUp(id)
                .onFailure { error ->
                    emitEvent(UiEvent.Error(error.message ?: "Error al mover"))
                }
        }
    }

    /**
     * Mueve una descarga hacia abajo en la lista
     */
    fun moveDownloadDown(id: String) {
        viewModelScope.launch {
            downloadManager.moveDown(id)
                .onFailure { error ->
                    emitEvent(UiEvent.Error(error.message ?: "Error al mover"))
                }
        }
    }

    fun moveDownload(fromIndex: Int, toIndex: Int) {
        val currentFiltered = filteredDownloads.value
        // Validar índices en la lista filtrada (lo que ve el usuario)
        if (fromIndex in currentFiltered.indices && toIndex in currentFiltered.indices) {
            val fromItem = currentFiltered[fromIndex]
            val toItem = currentFiltered[toIndex]
            
            // Obtener lista completa actual para calcular índices reales
            val fullList = downloads.value
            val realToIndex = fullList.indexOfFirst { it.id == toItem.id }
            
            if (realToIndex != -1) {
                viewModelScope.launch {
                    downloadManager.moveDownloadToPosition(fromItem.id, realToIndex)
                }
            }
        }
    }

    // SECCIÓN 6: CONFIGURACIÓN

    /**
     * Cambia el límite de descargas simultáneas
     */
    fun setMaxConcurrentDownloads(limit: Int) {
        viewModelScope.launch {
            downloadManager.setMaxConcurrentDownloads(limit)
                .onSuccess {
                    emitEvent(UiEvent.Success("Límite actualizado a $limit"))
                }
                .onFailure { error ->
                    emitEvent(UiEvent.Error("Error: ${error.message}"))
                }
        }
    }

    /**
     * Establece el límite de velocidad global
     */
    fun setGlobalSpeedLimit(bytesPerSecond: Long?) {
        viewModelScope.launch {
            downloadManager.setGlobalSpeedLimit(bytesPerSecond)
                .onSuccess {
                    val message = if (bytesPerSecond == null) {
                        "Límite de velocidad eliminado"
                    } else {
                        "Límite de velocidad: ${bytesPerSecond / 1024} KB/s"
                    }
                    emitEvent(UiEvent.Success(message))
                }
        }
    }

    /**
     * Establece el límite de velocidad para una descarga específica
     */
    fun setDownloadSpeedLimit(id: String, bytesPerSecond: Long?) {
        viewModelScope.launch {
            downloadManager.setDownloadSpeedLimit(id, bytesPerSecond)
        }
    }

    /**
     * Cambia el tema de la aplicación
     */
    fun toggleTheme() {
        _isDarkTheme.update { !it }
    }

    /**
     * Habilita/deshabilita la detección automática del portapapeles
     */
    fun toggleAutoDetectClipboard() {
        _uiState.update { it.copy(autoDetectClipboard = !it.autoDetectClipboard) }
    }

    // SECCIÓN 7: GESTIÓN DE DIÁLOGOS Y UI

    /**
     * Muestra el diálogo para añadir descarga
     */
    fun showAddDownloadDialog() {
        _uiState.update { it.copy(showAddDownloadDialog = true) }
    }

    /**
     * Cierra el diálogo de añadir descarga
     */
    fun dismissAddDownloadDialog() {
        _uiState.update {
            it.copy(
                showAddDownloadDialog = false,
                addDownloadUrl = "",
                addDownloadFileName = "",
                addDownloadCategory = null,
                addDownloadPriority = Priority.MEDIUM,
                addDownloadHash = "" // ✅ Limpiar hash también
            )
        }
    }

    /**
     * Actualiza la URL del diálogo de añadir descarga
     */
    fun updateAddDownloadUrl(url: String) {
        _uiState.update { it.copy(addDownloadUrl = url) }
    }

    /**
     * Actualiza el nombre de archivo del diálogo
     */
    fun updateAddDownloadFileName(fileName: String) {
        _uiState.update { it.copy(addDownloadFileName = fileName) }
    }

    /**
     * Actualiza la categoría del diálogo
     */
    fun updateAddDownloadCategory(category: Category?) {
        _uiState.update { it.copy(addDownloadCategory = category) }
    }

    /**
     * Actualiza la prioridad del diálogo
     */
    fun updateAddDownloadPriority(priority: Priority) {
        _uiState.update { it.copy(addDownloadPriority = priority) }
    }

    /**
     * Actualiza el hash del diálogo
     */
    fun updateAddDownloadHash(hash: String) {
        _uiState.update { it.copy(addDownloadHash = hash) }
    }

    /**
     * Muestra el diálogo de configuración
     */
    fun showSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    /**
     * Cierra el diálogo de configuración
     */
    fun dismissSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    /**
     * Muestra los detalles de una descarga
     */
    fun showDownloadDetails(downloadId: String) {
        _uiState.update {
            it.copy(
                showDetailsDialog = true,
                selectedDownloadId = downloadId
            )
        }
    }

    /**
     * Cierra el diálogo de detalles
     */
    fun dismissDownloadDetails() {
        _uiState.update {
            it.copy(
                showDetailsDialog = false,
                selectedDownloadId = null
            )
        }
    }

    /**
     * Descarta la sugerencia del portapapeles
     */
    fun dismissClipboardSuggestion() {
        _uiState.update { it.copy(showClipboardSuggestion = false) }
    }

    /**
     * Acepta la sugerencia del portapapeles
     */
    fun acceptClipboardSuggestion() {
        val url = _uiState.value.suggestedUrl
        if (url != null) {
            addDownload(url)
            dismissClipboardSuggestion()
        }
    }

    // SECCIÓN 8: FILTROS Y BÚSQUEDA

    /**
     * Filtra las descargas por categoría
     */
    fun filterByCategory(category: Category?) {
        _uiState.update { it.copy(selectedCategoryFilter = category) }
    }

    /**
     * Filtra las descargas por estado
     */
    fun filterByStatus(status: DownloadStatusFilter?) {
        _uiState.update { it.copy(selectedStatusFilter = status) }
    }

    /**
     * Actualiza el texto de búsqueda
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Obtiene las descargas filtradas según los criterios actuales
     */
    val filteredDownloads: StateFlow<List<DownloadItem>> = combine(
        downloads,
        uiState
    ) { downloads, state ->
        var filtered = downloads

        // Filtrar por categoría
        state.selectedCategoryFilter?.let { category ->
            filtered = filtered.filter { it.category == category }
        }

        // Filtrar por estado
        state.selectedStatusFilter?.let { statusFilter ->
            filtered = when (statusFilter) {
                DownloadStatusFilter.ACTIVE -> filtered.filter { it.status is DownloadStatus.Downloading }
                DownloadStatusFilter.QUEUED -> filtered.filter { it.status is DownloadStatus.Queued }
                DownloadStatusFilter.PAUSED -> filtered.filter { it.status is DownloadStatus.Paused }
                DownloadStatusFilter.COMPLETED -> filtered.filter { it.status is DownloadStatus.Completed }
                DownloadStatusFilter.FAILED -> filtered.filter { it.status is DownloadStatus.Failed }
            }
        }

        // Filtrar por búsqueda
        if (state.searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.fileName.contains(state.searchQuery, ignoreCase = true) ||
                        it.url.contains(state.searchQuery, ignoreCase = true)
            }
        }

        filtered
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // SECCIÓN 9: UTILIDADES

    /**
     * Verifica la integridad de una descarga completada mediante hash
     * @param id ID de la descarga
     * @param expectedHash Hash esperado (MD5, SHA-1 o SHA-256 detectado automáticamente por longitud)
     */
    fun verifyDownloadIntegrity(id: String, expectedHash: String) {
        viewModelScope.launch {
            // Validar que el hash no esté vacío
            if (expectedHash.isBlank()) {
                emitEvent(UiEvent.Error("El hash no puede estar vacío"))
                return@launch
            }

            // Validar formato hexadecimal
            if (!expectedHash.matches(Regex("^[0-9a-fA-F]+$"))) {
                emitEvent(UiEvent.Error("El hash debe estar en formato hexadecimal"))
                return@launch
            }

            // Validar longitud (MD5=32, SHA-1=40, SHA-256=64)
            if (expectedHash.length !in listOf(32, 40, 64)) {
                emitEvent(UiEvent.Error("Hash inválido. Longitud esperada: 32 (MD5), 40 (SHA-1) o 64 (SHA-256)"))
                return@launch
            }

            emitEvent(UiEvent.Info("Verificando integridad..."))

            downloadManager.verifyIntegrity(id, expectedHash)
                .onSuccess { isValid ->
                    if (isValid) {
                        emitEvent(UiEvent.Success("✓ Verificación exitosa: El archivo es íntegro"))
                    } else {
                        emitEvent(UiEvent.Error("✗ Verificación fallida: El hash no coincide (archivo corrupto o modificado)"))
                    }
                }
                .onFailure { error ->
                    emitEvent(UiEvent.Error("Error al verificar: ${error.message}"))
                }
        }
    }

    /**
     * Emite un evento de UI
     */
    private suspend fun emitEvent(event: UiEvent) {
        _uiEvents.emit(event)
    }

    /**
     * Limpieza al destruir el ViewModel
     */
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            downloadManager.shutdown()
        }
    }
}

/**
 * Estado de la UI
 */
data class DownloadUiState(
    val isLoading: Boolean = false,
    val showAddDownloadDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showDetailsDialog: Boolean = false,
    val selectedDownloadId: String? = null,
    val addDownloadUrl: String = "",
    val addDownloadFileName: String = "",
    val addDownloadCategory: Category? = null,
    val addDownloadPriority: Priority = Priority.MEDIUM,
    val addDownloadHash: String = "", // ✅ Hash SHA-256 opcional para verificación
    val selectedCategoryFilter: Category? = null,
    val selectedStatusFilter: DownloadStatusFilter? = null,
    val searchQuery: String = "",
    val autoDetectClipboard: Boolean = true,
    val showClipboardSuggestion: Boolean = false,
    val suggestedUrl: String? = null,
    val lastDetectedUrl: String? = null
)

/**
 * Filtros de estado
 */
enum class DownloadStatusFilter {
    ACTIVE,
    QUEUED,
    PAUSED,
    COMPLETED,
    FAILED
}

/**
 * Eventos únicos de UI (Snackbars, toasts, etc.)
 */
sealed class UiEvent {
    data class Success(val message: String) : UiEvent()
    data class Error(val message: String) : UiEvent()
    data class Info(val message: String) : UiEvent()
}
