package com.dam2.flashdownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dam2.flashdownloader.domain.model.Category
import com.dam2.flashdownloader.domain.model.DownloadStatus
import com.dam2.flashdownloader.presentation.viewmodel.DownloadStatusFilter
import com.dam2.flashdownloader.presentation.viewmodel.DownloadViewModel
import com.dam2.flashdownloader.presentation.viewmodel.UiEvent
import com.dam2.flashdownloader.ui.components.DownloadListItem
import com.dam2.flashdownloader.ui.components.StatisticsCard
import com.dam2.flashdownloader.ui.dialogs.AddDownloadBottomSheet
import com.dam2.flashdownloader.ui.dialogs.SettingsBottomSheet
import com.dam2.flashdownloader.ui.theme.FlashDownloaderTheme
import com.dam2.flashdownloader.ui.utils.draggableItem
import com.dam2.flashdownloader.ui.utils.rememberDragDropState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Aplicación principal de Android con sistema de pestañas
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val addDownloadSheetState = rememberModalBottomSheetState()
    val settingsSheetState = rememberModalBottomSheetState()

    // Estado de las pestañas
    var selectedTabIndex by remember { mutableStateOf(0) }

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

    // Filtrar descargas según la pestaña seleccionada
    val filteredDownloads = remember(downloads, selectedTabIndex) {
        when (selectedTabIndex) {
            0 -> downloads // Todos
            1 -> downloads.filter { // Activos (Descargando + En cola)
                it.status is DownloadStatus.Downloading || it.status is DownloadStatus.Queued
            }
            2 -> downloads.filter { // Completados
                it.status is DownloadStatus.Completed
            }
            else -> downloads
        }
    }

    FlashDownloaderTheme(darkTheme = isDarkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Flash Downloader") },
                    actions = {
                        // Filtros
                        var showFilterMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(Icons.Default.FilterList, "Filtros")
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            Text(
                                "Categorías",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                            DropdownMenuItem(
                                text = { Text("Todas") },
                                onClick = {
                                    viewModel.filterByCategory(null)
                                    showFilterMenu = false
                                }
                            )
                            Category.entries.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text("${category.iconName} ${category.displayName}") },
                                    onClick = {
                                        viewModel.filterByCategory(category)
                                        showFilterMenu = false
                                    }
                                )
                            }

                            HorizontalDivider()

                            Text(
                                "Estados",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                            DropdownMenuItem(
                                text = { Text("Todos") },
                                onClick = {
                                    viewModel.filterByStatus(null)
                                    showFilterMenu = false
                                }
                            )
                            DownloadStatusFilter.entries.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        viewModel.filterByStatus(status)
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }

                        // Menú de opciones
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Más opciones")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Pausar todas") },
                                leadingIcon = { Icon(Icons.Default.PauseCircle, null) },
                                onClick = {
                                    scope.launch { viewModel.pauseAll() }
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reanudar todas") },
                                leadingIcon = { Icon(Icons.Default.PlayCircle, null) },
                                onClick = {
                                    scope.launch { viewModel.resumeAll() }
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Limpiar completadas") },
                                leadingIcon = { Icon(Icons.Default.CleaningServices, null) },
                                onClick = {
                                    scope.launch { viewModel.clearCompleted() }
                                    showMenu = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (isDarkTheme) "Tema claro" else "Tema oscuro") },
                                leadingIcon = {
                                    Icon(
                                        if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        null
                                    )
                                },
                                onClick = {
                                    viewModel.toggleTheme()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Configuración") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = {
                                    viewModel.showSettingsDialog()
                                    showMenu = false
                                }
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showAddDownloadDialog() },
                    icon = { Icon(Icons.Default.Add, "Añadir descarga") },
                    text = { Text("Nueva descarga") }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Barra de búsqueda
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("Buscar descargas...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )

                // Card de estadísticas
                StatisticsCard(statistics = statistics)

                // Sistema de pestañas
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Todos") },
                        icon = { Icon(Icons.Default.List, null) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Activos") },
                        icon = { 
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Text(statistics.activeDownloads.toString())
                            }
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text("Completados") },
                        icon = { 
                            Badge(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(statistics.completedDownloads.toString())
                            }
                        }
                    )
                }

                // Lista de descargas filtrada por pestaña
                if (filteredDownloads.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        onAddDownload = { viewModel.showAddDownloadDialog() },
                        tabIndex = selectedTabIndex
                    )
                } else {
                    val listState = rememberLazyListState()
                    val dragDropState = rememberDragDropState(
                        lazyListState = listState,
                        onMove = { fromIndex, toIndex ->
                            viewModel.moveDownload(fromIndex, toIndex)
                        }
                    )
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = filteredDownloads,
                            key = { it.id }
                        ) { download ->
                            val index = filteredDownloads.indexOf(download)
                            DownloadListItem(
                                download = download,
                                onPause = { viewModel.pauseDownload(download.id) },
                                onResume = { viewModel.resumeDownload(download.id) },
                                onCancel = { viewModel.cancelDownload(download.id) },
                                onRemove = { deleteFile -> 
                                    viewModel.removeDownload(download.id, deleteFile) 
                                },
                                onRetry = { viewModel.retryDownload(download.id) },
                                onClick = { viewModel.showDownloadDetails(download.id) },
                                modifier = Modifier.draggableItem(dragDropState, index)
                            )
                        }
                    }
                }
            }

            // Bottom Sheets
            if (uiState.showAddDownloadDialog) {
                AddDownloadBottomSheet(
                    sheetState = addDownloadSheetState,
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
                SettingsBottomSheet(
                    sheetState = settingsSheetState,
                    maxConcurrentDownloads = maxConcurrentDownloads,
                    onMaxConcurrentDownloadsChange = { viewModel.setMaxConcurrentDownloads(it) },
                    globalSpeedLimit = globalSpeedLimit,
                    onGlobalSpeedLimitChange = { viewModel.setGlobalSpeedLimit(it) },
                    autoDetectClipboard = uiState.autoDetectClipboard,
                    onAutoDetectClipboardChange = { viewModel.toggleAutoDetectClipboard() },
                    onDismiss = viewModel::dismissSettingsDialog
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    onAddDownload: () -> Unit,
    tabIndex: Int,
    modifier: Modifier = Modifier
) {
    val (emoji, title, subtitle) = when (tabIndex) {
        1 -> Triple("⏸️", "No hay descargas activas", "Las descargas activas aparecerán aquí")
        2 -> Triple("✅", "No hay descargas completadas", "Las descargas completadas aparecerán aquí")
        else -> Triple("📥", "No hay descargas", "Toca el botón + para comenzar")
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
        if (tabIndex == 0) {
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(onClick = onAddDownload) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Añadir descarga")
            }
        }
    }
}
