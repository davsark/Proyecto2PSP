package com.dam2.flashdownloader.ui

import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.sp
import com.dam2.flashdownloader.domain.model.Category
import com.dam2.flashdownloader.presentation.viewmodel.DownloadStatusFilter
import com.dam2.flashdownloader.presentation.viewmodel.DownloadViewModel
import com.dam2.flashdownloader.presentation.viewmodel.UiEvent
import com.dam2.flashdownloader.ui.components.DownloadListItem
import com.dam2.flashdownloader.ui.components.StatisticsCard
import com.dam2.flashdownloader.ui.dialogs.AddDownloadBottomSheet
import com.dam2.flashdownloader.ui.dialogs.SettingsBottomSheet
import com.dam2.flashdownloader.ui.theme.FlashDownloaderTheme
import com.dam2.flashdownloader.ui.utils.rememberDragDropState
import com.dam2.flashdownloader.ui.utils.dragGestureHandler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.painterResource
import flashdownloader.composeapp.generated.resources.Res
import flashdownloader.composeapp.generated.resources.logo
import com.dam2.flashdownloader.domain.model.DownloadStatus

/**
 * Filtro de estado para el segundo spinner
 */
enum class StatusFilter(val displayName: String) {
    ALL("Todas"),
    ACTIVE("Activas"),
    COMPLETED("Completadas"),
    FAILED("Fallidas"),
    QUEUED("En cola"),
    PAUSED("Pausadas")
}

/**
 * Aplicación principal de Android
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

    // Drag Drop State
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val dragDropState = rememberDragDropState(listState) { from, to ->
        viewModel.moveDownload(from, to)
    }

    FlashDownloaderTheme(darkTheme = isDarkTheme) {
        var selectedStatus by remember { mutableStateOf(StatusFilter.ALL) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Logo de la aplicación
                            Image(
                                painter = painterResource(Res.drawable.logo),
                                contentDescription = "Flash Downloader Logo",
                                modifier = Modifier.size(28.dp)
                            )
                            Text("Flash Downloader")
                        }
                    },
                    actions = {
                        // Icono de tema oscuro (visible)
                        IconButton(onClick = { viewModel.toggleTheme() }) {
                            Icon(
                                if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                "Cambiar tema"
                            )
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
                            Divider()
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

                // Fila de spinners (categorías y estados)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Spinner de categorías
                    var categoryExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = uiState.selectedCategoryFilter?.displayName ?: "Todas",
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = { Icon(Icons.Default.FilterList, null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            singleLine = true
                        )

                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📋 Todas") },
                                onClick = {
                                    viewModel.filterByCategory(null)
                                    categoryExpanded = false
                                }
                            )
                            Category.entries.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text("${category.iconName} ${category.displayName}") },
                                    onClick = {
                                        viewModel.filterByCategory(category)
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Spinner de estados
                    var statusExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = statusExpanded,
                        onExpandedChange = { statusExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedStatus.displayName,
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = { Icon(Icons.Default.FilterAlt, null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            singleLine = true
                        )

                        ExposedDropdownMenu(
                            expanded = statusExpanded,
                            onDismissRequest = { statusExpanded = false }
                        ) {
                            StatusFilter.entries.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status.displayName) },
                                    onClick = {
                                        selectedStatus = status
                                        statusExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Filtrar descargas según los dos spinners
                val filteredDownloads = remember(downloads, selectedStatus) {
                    downloads.filter { download ->
                        when (selectedStatus) {
                            StatusFilter.ALL -> true
                            StatusFilter.ACTIVE -> download.status is DownloadStatus.Downloading
                            StatusFilter.QUEUED -> download.status is DownloadStatus.Queued
                            StatusFilter.PAUSED -> download.status is DownloadStatus.Paused
                            StatusFilter.COMPLETED -> download.status is DownloadStatus.Completed
                            StatusFilter.FAILED -> download.status is DownloadStatus.Failed
                        }
                    }
                }

                // Card de estadísticas
                StatisticsCard(statistics = statistics)

                // Lista de descargas
                if (filteredDownloads.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize()
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
                            items = filteredDownloads,
                            key = { it.id }
                        ) { download ->
                            val index = filteredDownloads.indexOf(download)
                            DownloadListItem(
                                download = download,
                                onPause = { viewModel.pauseDownload(download.id) },
                                onResume = { viewModel.resumeDownload(download.id) },
                                onCancel = { viewModel.cancelDownload(download.id) },
                                onRemove = { viewModel.removeDownload(download.id) },
                                onRetry = { viewModel.retryDownload(download.id) },
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

            // Detalles de descarga
            if (uiState.showDetailsDialog && uiState.selectedDownloadId != null) {
                val selectedDownload = downloads.find { it.id == uiState.selectedDownloadId }
                if (selectedDownload != null) {
                    val detailsSheetState = rememberModalBottomSheetState()
                    com.dam2.flashdownloader.ui.dialogs.DownloadDetailsBottomSheet(
                        download = selectedDownload,
                        sheetState = detailsSheetState,
                        onDismiss = viewModel::dismissDownloadDetails
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
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
            text = "Toca el botón + para comenzar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
