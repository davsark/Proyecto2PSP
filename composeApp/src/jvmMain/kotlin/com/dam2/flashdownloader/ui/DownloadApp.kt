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
import com.dam2.flashdownloader.presentation.viewmodel.DownloadViewModel
import com.dam2.flashdownloader.presentation.viewmodel.DownloadStatusFilter
import com.dam2.flashdownloader.presentation.viewmodel.UiEvent
import com.dam2.flashdownloader.ui.components.DownloadListItem
import com.dam2.flashdownloader.ui.components.StatisticsPanel
import com.dam2.flashdownloader.ui.components.TopBar
import com.dam2.flashdownloader.ui.dialogs.AddDownloadDialog
import com.dam2.flashdownloader.ui.dialogs.SettingsDialog
import com.dam2.flashdownloader.ui.theme.FlashDownloaderTheme
import com.dam2.flashdownloader.ui.utils.rememberDragDropState
import com.dam2.flashdownloader.ui.utils.dragGestureHandler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.dam2.flashdownloader.domain.model.Category

/**
 * Aplicación principal de Desktop
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

    // Estado de navegación
    var selectedNavItem by remember { mutableStateOf(0) }
    
    // Estado de Drag & Drop
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val dragDropState = rememberDragDropState(listState) { from, to ->
        viewModel.moveDownload(from, to)
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
                // 🔹 NAVIGATION RAIL IZQUIERDA
                NavigationRail(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    NavigationRailItem(
                        icon = { Icon(Icons.Default.CloudDownload, "Todas") },
                        label = { Text("Todas") },
                        selected = selectedNavItem == 0,
                        onClick = {
                            selectedNavItem = 0
                            viewModel.filterByStatus(null)
                            viewModel.filterByCategory(null)
                        }
                    )

                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Download, "Descargando") },
                        label = { Text("Activas") },
                        selected = selectedNavItem == 1,
                        onClick = {
                            selectedNavItem = 1
                            viewModel.filterByStatus(DownloadStatusFilter.ACTIVE)
                        }
                    )

                    NavigationRailItem(
                        icon = { Icon(Icons.Default.CheckCircle, "Completadas") },
                        label = { Text("Completas") },
                        selected = selectedNavItem == 2,
                        onClick = {
                            selectedNavItem = 2
                            viewModel.filterByStatus(DownloadStatusFilter.COMPLETED)
                        }
                    )

                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Error, "Fallidas") },
                        label = { Text("Fallidas") },
                        selected = selectedNavItem == 3,
                        onClick = {
                            selectedNavItem = 3
                            viewModel.filterByStatus(DownloadStatusFilter.FAILED)
                        }
                    )
                    
                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Schedule, "En cola") },
                        label = { Text("En cola") },
                        selected = selectedNavItem == 4,
                        onClick = {
                            selectedNavItem = 4
                            viewModel.filterByStatus(DownloadStatusFilter.QUEUED)
                        }
                    )
                    
                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Pause, "Pausadas") },
                        label = { Text("Pausadas") },
                        selected = selectedNavItem == 5,
                        onClick = {
                            selectedNavItem = 5
                            viewModel.filterByStatus(DownloadStatusFilter.PAUSED)
                        }
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
                        onAddDownload = viewModel::showAddDownloadDialog,
                        onPauseAll = { scope.launch { viewModel.pauseAll() } },
                        onResumeAll = { scope.launch { viewModel.resumeAll() } },
                        onClearCompleted = { scope.launch { viewModel.clearCompleted() } },
                        onSettings = viewModel::showSettingsDialog,
                        onToggleTheme = viewModel::toggleTheme,
                        isDarkTheme = isDarkTheme
                    )
                    
                    // Barra de navegación de categorías
                    ScrollableTabRow(
                        selectedTabIndex = if (uiState.selectedCategoryFilter == null) 0 else Category.entries.indexOf(uiState.selectedCategoryFilter) + 1,
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 0.dp
                    ) {
                        // Tab "Todas"
                        Tab(
                            selected = uiState.selectedCategoryFilter == null,
                            onClick = { viewModel.filterByCategory(null) },
                            text = { Text("📋 Todas") }
                        )
                        
                        // Tabs de categorías
                        Category.entries.forEach { category ->
                            Tab(
                                selected = uiState.selectedCategoryFilter == category,
                                onClick = { viewModel.filterByCategory(category) },
                                text = { Text("${category.iconName} ${category.displayName}") }
                            )
                        }
                    }

                    // Lista de descargas
                    if (downloads.isEmpty()) {
                        // Estado vacío
                        EmptyState(
                            modifier = Modifier.fillMaxSize(),
                            onAddDownload = viewModel::showAddDownloadDialog
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .dragGestureHandler(dragDropState),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = downloads,
                                key = { it.id }
                            ) { download ->
                                val index = downloads.indexOf(download)
                                DownloadListItem(
                                    download = download,
                                    onPause = { viewModel.pauseDownload(download.id) },
                                    onResume = { viewModel.resumeDownload(download.id) },
                                    onCancel = { viewModel.cancelDownload(download.id) },
                                    onRemove = { viewModel.removeDownload(download.id) },
                                    onRetry = { viewModel.retryDownload(download.id) },
                                    onMoveUp = { viewModel.moveDownloadUp(download.id) },
                                    onMoveDown = { viewModel.moveDownloadDown(download.id) },
                                    onOpenFolder = { viewModel.openFileLocation(download.id) },
                                    onClick = { viewModel.showDownloadDetails(download.id) },
                                    dragHandleModifier = if (uiState.searchQuery.isEmpty()) Modifier else Modifier,
                                    dragDropState = if (uiState.searchQuery.isEmpty()) dragDropState else null,
                                    index = index
                                )
                            }
                        }
                    }
                }

                // Panel de estadísticas
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
                    hash = uiState.addDownloadHash,
                    onHashChange = viewModel::updateAddDownloadHash,
                    onConfirm = {
                        viewModel.addDownload(
                            url = uiState.addDownloadUrl,
                            fileName = uiState.addDownloadFileName.ifBlank { null },
                            category = uiState.addDownloadCategory,
                            priority = uiState.addDownloadPriority,
                            hash = uiState.addDownloadHash.ifBlank { null }
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📥",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No hay descargas",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Añade tu primera descarga para comenzar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddDownload) {
            Text("Añadir descarga")
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
