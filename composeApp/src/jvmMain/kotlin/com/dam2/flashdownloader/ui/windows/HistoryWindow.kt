package com.dam2.flashdownloader.ui.windows

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import com.dam2.flashdownloader.domain.model.Category
import com.dam2.flashdownloader.domain.model.DownloadHistoryEntry
import com.dam2.flashdownloader.ui.utils.formatBytes
import java.text.SimpleDateFormat
import java.util.*

/**
 * Ventana de historial de descargas completadas
 */
@Composable
fun HistoryWindow(
    onCloseRequest: () -> Unit,
    historyEntries: List<DownloadHistoryEntry>,
    onClearHistory: () -> Unit,
    onRemoveEntry: (String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    Window(
        onCloseRequest = onCloseRequest,
        title = "Historial de Descargas",
        state = WindowState(width = 900.dp, height = 600.dp)
    ) {
        HistoryContent(
            entries = historyEntries,
            onClearHistory = onClearHistory,
            onRemoveEntry = onRemoveEntry,
            onOpenFile = onOpenFile
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(
    entries: List<DownloadHistoryEntry>,
    onClearHistory: () -> Unit,
    onRemoveEntry: (String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }

    val filteredEntries = remember(entries, searchQuery, selectedCategory) {
        entries.filter { entry ->
            val matchesSearch = searchQuery.isEmpty() ||
                    entry.fileName.contains(searchQuery, ignoreCase = true) ||
                    entry.url.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == null || entry.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Descargas") },
                actions = {
                    IconButton(onClick = onClearHistory) {
                        Icon(Icons.Default.DeleteSweep, "Limpiar historial")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Filtros
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Buscar...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )

                // Dropdown de categorías
                var expandedCategory by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.displayName ?: "Todas",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                        modifier = Modifier.menuAnchor().width(180.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Todas") },
                            onClick = { selectedCategory = null; expandedCategory = false }
                        )
                        Category.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text("${category.iconName} ${category.displayName}") },
                                onClick = { selectedCategory = category; expandedCategory = false }
                            )
                        }
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (entries.isEmpty()) "No hay descargas en el historial" else "No se encontraron resultados",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(filteredEntries.size) { index ->
                        HistoryItem(
                            entry = filteredEntries[index],
                            onRemove = { onRemoveEntry(filteredEntries[index].id) },
                            onOpenFile = { onOpenFile(filteredEntries[index].filePath) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: DownloadHistoryEntry,
    onRemove: () -> Unit,
    onOpenFile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${entry.totalSize.formatBytes()} • ${entry.category.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Completado: ${formatTimestamp(entry.completedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.downloadDuration > 0) {
                    Text(
                        text = "Duración: ${formatDuration(entry.downloadDuration)} • Velocidad promedio: ${entry.averageSpeed.formatBytes()}/s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onOpenFile) {
                    Icon(Icons.Default.Folder, "Abrir archivo")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, "Eliminar del historial")
                }
            }
        }
    }
}

/**
 * Formatea un timestamp en milisegundos a formato legible
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Formatea una duración en segundos a formato legible
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> String.format("%dh %dm %ds", hours, minutes, secs)
        minutes > 0 -> String.format("%dm %ds", minutes, secs)
        else -> String.format("%ds", secs)
    }
}
