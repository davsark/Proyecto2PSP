package com.dam2.flashdownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    FlashDownloaderTheme(darkTheme = isDarkTheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
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
                        selectedStatusFilter = uiState.selectedStatusFilter,
                        onStatusFilterChange = viewModel::filterByStatus,
                        onAddDownload = viewModel::showAddDownloadDialog,
                        onPauseAll = { scope.launch { viewModel.pauseAll() } },
                        onResumeAll = { scope.launch { viewModel.resumeAll() } },
                        onClearCompleted = { scope.launch { viewModel.clearCompleted() } },
                        onSettings = viewModel::showSettingsDialog,
                        onToggleTheme = viewModel::toggleTheme,
                        isDarkTheme = isDarkTheme
                    )

                    // Lista de descargas
                    if (downloads.isEmpty()) {
                        // Estado vacío
                        EmptyState(
                            modifier = Modifier.fillMaxSize(),
                            onAddDownload = viewModel::showAddDownloadDialog
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = downloads,
                                key = { it.id }
                            ) { download ->
                                DownloadListItem(
                                    download = download,
                                    onPause = { viewModel.pauseDownload(download.id) },
                                    onResume = { viewModel.resumeDownload(download.id) },
                                    onCancel = { viewModel.cancelDownload(download.id) },
                                    onRemove = { viewModel.removeDownload(download.id) },
                                    onRetry = { viewModel.retryDownload(download.id) },
                                    onMoveUp = { viewModel.moveDownloadUp(download.id) },
                                    onMoveDown = { viewModel.moveDownloadDown(download.id) },
                                    onClick = { viewModel.showDownloadDetails(download.id) }
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
