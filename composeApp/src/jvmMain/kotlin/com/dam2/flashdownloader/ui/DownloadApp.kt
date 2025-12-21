package com.dam2.flashdownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dam2.flashdownloader.domain.model.DownloadStatus
import com.dam2.flashdownloader.presentation.viewmodel.DownloadViewModel
import com.dam2.flashdownloader.presentation.viewmodel.UiEvent
import com.dam2.flashdownloader.ui.components.DownloadListItem
import com.dam2.flashdownloader.ui.components.StatisticsPanel
import com.dam2.flashdownloader.ui.components.TopBar
import com.dam2.flashdownloader.ui.dialogs.AddDownloadDialog
import com.dam2.flashdownloader.ui.dialogs.SettingsDialog
import com.dam2.flashdownloader.ui.theme.FlashDownloaderTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Aplicación principal de Desktop con NavigationRail
 */
@Composable
fun DownloadApp(
    viewModel: DownloadViewModel = koinInject()
) {
    val downloads by viewModel.filteredDownloads.collectAsState()
    val statistics by viewModel.statistics.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val maxConcurrentDownloads by viewModel.maxConcurrentDownloads.collectAsState()
    val globalSpeedLimit by viewModel.globalSpeedLimit.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Estado de navegación (0 = Todos, 1 = Activos, 2 = Completados)
    var selectedNavIndex by remember { mutableStateOf(0) }

    // Observar eventos de UI
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is UiEvent.Success -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is UiEvent.Error -> {
                    snackbarHostState.showSnackbar(
                        message = "❌ ${event.message}",
                        duration = SnackbarDuration.Long
                    )
                }
                is UiEvent.Info -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    // Filtrar descargas según navegación
    val filteredDownloads = remember(downloads, selectedNavIndex) {
        when (selectedNavIndex) {
            0 -> downloads // Todos
            1 -> downloads.filter { // Activos (Descargando)
                it.status is DownloadStatus.Downloading
            }
            2 -> downloads.filter { // En Cola
                it.status is DownloadStatus.Queued
            }
            3 -> downloads.filter { // Pausadas
                it.status is DownloadStatus.Paused
            }
            4 -> downloads.filter { // Completados
                it.status is DownloadStatus.Completed
            }
            5 -> downloads.filter { // Errores
                it.status is DownloadStatus.Failed
            }
            else -> downloads
        }
    }

    FlashDownloaderTheme(darkTheme = isDarkTheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // NavigationRail (barra lateral izquierda)
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    header = {
                        FloatingActionButton(
                            onClick = { viewModel.showAddDownloadDialog() },
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, "Añadir descarga")
                        }
                    }
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Todos
                    NavigationRailItem(
                        selected = selectedNavIndex == 0,
                        onClick = { selectedNavIndex = 0 },
                        icon = { Icon(Icons.Default.List, null) },
                        label = { Text("Todos") },
                        alwaysShowLabel = true
                    )
                    
                    // Activos (Descargando)
                    NavigationRailItem(
                        selected = selectedNavIndex == 1,
                        onClick = { selectedNavIndex = 1 },
                        icon = { 
                            BadgedBox(
                                badge = {
                                    val activeCount = downloads.count { it.status is DownloadStatus.Downloading }
                                    if (activeCount > 0) {
                                        Badge { Text(activeCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Download, null)
                            }
                        },
                        label = { Text("Activos") },
                        alwaysShowLabel = true
                    )
                    
                    // En Cola
                    NavigationRailItem(
                        selected = selectedNavIndex == 2,
                        onClick = { selectedNavIndex = 2 },
                        icon = { 
                            BadgedBox(
                                badge = {
                                    val queuedCount = downloads.count { it.status is DownloadStatus.Queued }
                                    if (queuedCount > 0) {
                                        Badge { Text(queuedCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.HourglassEmpty, null)
                            }
                        },
                        label = { Text("En Cola") },
                        alwaysShowLabel = true
                    )
                    
                    // Pausadas
                    NavigationRailItem(
                        selected = selectedNavIndex == 3,
                        onClick = { selectedNavIndex = 3 },
                        icon = { 
                            BadgedBox(
                                badge = {
                                    val pausedCount = downloads.count { it.status is DownloadStatus.Paused }
                                    if (pausedCount > 0) {
                                        Badge { Text(pausedCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Pause, null)
                            }
                        },
                        label = { Text("Pausadas") },
                        alwaysShowLabel = true
                    )
                    
                    // Completados
                    NavigationRailItem(
                        selected = selectedNavIndex == 4,
                        onClick = { selectedNavIndex = 4 },
                        icon = { 
                            BadgedBox(
                                badge = {
                                    if (statistics.completedDownloads > 0) {
                                        Badge { Text(statistics.completedDownloads.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.CheckCircle, null)
                            }
                        },
                        label = { Text("Completados") },
                        alwaysShowLabel = true
                    )
                    
                    // Errores
                    NavigationRailItem(
                        selected = selectedNavIndex == 5,
                        onClick = { selectedNavIndex = 5 },
                        icon = { 
                            BadgedBox(
                                badge = {
                                    val failedCount = downloads.count { it.status is DownloadStatus.Failed }
                                    if (failedCount > 0) {
                                        Badge { Text(failedCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Error, null)
                            }
                        },
                        label = { Text("Errores") },
                        alwaysShowLabel = true
                    )
                }

                // Contenido principal
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Barra superior
                    TopBar(
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        selectedCategoryFilter = uiState.selectedCategoryFilter,
                        onCategoryFilterChange = viewModel::filterByCategory,
                        onAddDownload = viewModel::showAddDownloadDialog,
                        onPauseAll = { scope.launch { viewModel.pauseAll() } },
                        onResumeAll = { scope.launch { viewModel.resumeAll() } },
                        onClearCompleted = { scope.launch { viewModel.clearCompleted() } },
                        onSettings = viewModel::showSettingsDialog,
                        onToggleTheme = viewModel::toggleTheme,
                        isDarkTheme = isDarkTheme
                    )

                    // Lista de descargas
                    if (filteredDownloads.isEmpty()) {
                        EmptyState(
                            modifier = Modifier.fillMaxSize(),
                            onAddDownload = viewModel::showAddDownloadDialog,
                            navIndex = selectedNavIndex
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = filteredDownloads,
                                key = { it.id }
                            ) { download ->
                                DownloadListItem(
                                    download = download,
                                    onPause = { viewModel.pauseDownload(download.id) },
                                    onResume = { viewModel.resumeDownload(download.id) },
                                    onCancel = { viewModel.cancelDownload(download.id) },
                                    onRemove = { deleteFile -> 
                                        viewModel.removeDownload(download.id, deleteFile) 
                                    },
                                    onRetry = { viewModel.retryDownload(download.id) },
                                    onMoveUp = { viewModel.moveDownloadUp(download.id) },
                                    onMoveDown = { viewModel.moveDownloadDown(download.id) },
                                    onClick = { viewModel.showDownloadDetails(download.id) }
                                )
                            }
                        }
                    }
                }

                // Panel de estadísticas (lado derecho)
                StatisticsPanel(statistics = statistics)
            }

            // Diálogos
            if (uiState.showAddDownloadDialog) {
                AddDownloadDialog(
                    url = uiState.addDownloadUrl,
                    onUrlChange = viewModel::updateAddDownloadUrl,
                    fileName = uiState.addDownloadFileName,
                    onFileNameChange = viewModel::updateAddDownloadFileName,
                    category = uiState.addDownloadCategory,
                    onCategoryChange = viewModel::updateAddDownloadCategory,
                    priority = uiState.addDownloadPriority,
                    onPriorityChange = viewModel::updateAddDownloadPriority,
                    onConfirm = {
                        viewModel.addDownload(
                            url = uiState.addDownloadUrl,
                            fileName = uiState.addDownloadFileName.ifBlank { null },
                            category = uiState.addDownloadCategory,
                            priority = uiState.addDownloadPriority
                        )
                    },
                    onDismiss = viewModel::dismissAddDownloadDialog,
                    onPasteFromClipboard = { scope.launch { viewModel.addFromClipboard() } }
                )
            }

            if (uiState.showSettingsDialog) {
                SettingsDialog(
                    maxConcurrentDownloads = maxConcurrentDownloads,
                    onMaxConcurrentDownloadsChange = { viewModel.setMaxConcurrentDownloads(it) },
                    globalSpeedLimit = globalSpeedLimit,
                    onGlobalSpeedLimitChange = { viewModel.setGlobalSpeedLimit(it) },
                    autoDetectClipboard = uiState.autoDetectClipboard,
                    onAutoDetectClipboardChange = { viewModel.toggleAutoDetectClipboard() },
                    onDismiss = viewModel::dismissSettingsDialog
                )
            }

            // Sugerencia de portapapeles
            if (uiState.showClipboardSuggestion && uiState.suggestedUrl != null) {
                ClipboardSuggestionSnackbar(
                    url = uiState.suggestedUrl!!,
                    onAccept = viewModel::acceptClipboardSuggestion,
                    onDismiss = viewModel::dismissClipboardSuggestion
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    onAddDownload: () -> Unit,
    navIndex: Int,
    modifier: Modifier = Modifier
) {
    val (emoji, title, subtitle) = when (navIndex) {
        1 -> Triple("⬇️", "No hay descargas activas", "Las descargas en progreso aparecerán aquí")
        2 -> Triple("⏳", "No hay descargas en cola", "Las descargas en espera aparecerán aquí")
        3 -> Triple("⏸️", "No hay descargas pausadas", "Las descargas pausadas aparecerán aquí")
        4 -> Triple("✅", "No hay descargas completadas", "Las descargas completadas aparecerán aquí")
        5 -> Triple("❌", "No hay descargas con errores", "Las descargas fallidas aparecerán aquí")
        else -> Triple("📥", "No hay descargas", "Añade tu primera descarga para comenzar")
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (navIndex == 0) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAddDownload) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Añadir descarga")
            }
        }
    }
}

@Composable
private fun ClipboardSuggestionSnackbar(
    url: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Snackbar(
        modifier = Modifier.padding(16.dp),
        action = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Ignorar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onAccept) {
                    Text("Añadir")
                }
            }
        }
    ) {
        Text("URL detectada en portapapeles: ${url.take(50)}...")
    }
}
