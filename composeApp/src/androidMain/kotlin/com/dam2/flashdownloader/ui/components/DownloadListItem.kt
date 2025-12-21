package com.dam2.flashdownloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dam2.flashdownloader.domain.model.*
import com.dam2.flashdownloader.ui.theme.CategoryColors
import com.dam2.flashdownloader.ui.theme.DownloadColors
import com.dam2.flashdownloader.utils.formatBytes
import com.dam2.flashdownloader.utils.formatSpeed
import com.dam2.flashdownloader.utils.formatTime

/**
 * Item de la lista de descargas optimizado con datos reales
 */
@Composable
fun DownloadListItem(
    download: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: (deleteFile: Boolean) -> Unit,
    onRetry: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Primera fila: Icono, nombre y acciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Drag Handle Icon
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Arrastrar para reordenar",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Icono de categoría
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(getCategoryColor(download.category).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = download.category.iconName,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Nombre y estado
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = download.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusBadge(status = download.status)
                }

                // Botones de acción rápida
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Botón principal según estado
                    when (download.status) {
                        is DownloadStatus.Downloading -> {
                            IconButton(
                                onClick = onPause,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Pause,
                                    "Pausar",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        is DownloadStatus.Paused, is DownloadStatus.Queued, is DownloadStatus.Failed -> {
                            IconButton(
                                onClick = if (download.status is DownloadStatus.Failed) onRetry else onResume,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Icon(
                                    if (download.status is DownloadStatus.Failed) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                    if (download.status is DownloadStatus.Failed) "Reintentar" else "Reanudar",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        is DownloadStatus.Completed -> {
                            // Botón de abrir archivo
                            IconButton(
                                onClick = { /* Funcionalidad de abrir archivo */ },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    "Abrir",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        else -> {}
                    }

                    // Botón de borrar (siempre visible)
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            "Eliminar",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progreso y detalles según estado
            when (val status = download.status) {
                is DownloadStatus.Downloading, is DownloadStatus.Paused -> {
                    // Barra de progreso
                    val progress = when (status) {
                        is DownloadStatus.Downloading -> status.progress
                        is DownloadStatus.Paused -> status.progress
                        else -> 0f
                    }

                    val totalBytes = when (status) {
                        is DownloadStatus.Downloading -> status.totalBytes
                        is DownloadStatus.Paused -> status.totalBytes
                        else -> -1L
                    }

                    // Si el total es desconocido, mostrar barra indeterminada
                    if (totalBytes <= 0) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = getStatusColor(status),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = getStatusColor(status),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Información detallada
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            // Porcentaje
                            Text(
                                text = when (status) {
                                    is DownloadStatus.Downloading -> "${status.progressPercentage}%"
                                    is DownloadStatus.Paused -> "${status.progressPercentage}% (Pausado)"
                                    else -> "0%"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (status is DownloadStatus.Paused) 
                                    DownloadColors.Paused 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                            
                            // Velocidad (solo si está descargando)
                            if (status is DownloadStatus.Downloading && status.speed > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Speed,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = status.speed.formatSpeed(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            // Tamaño descargado / total
                            Text(
                                text = if (totalBytes > 0) {
                                    "${download.downloadedBytes.formatBytes()} / ${totalBytes.formatBytes()}"
                                } else {
                                    download.downloadedBytes.formatBytes()
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // ETA (tiempo estimado restante) y Tiempo transcurrido
                            if (status is DownloadStatus.Downloading && status.speed > 0 && totalBytes > 0) {
                                val remainingBytes = totalBytes - status.bytesDownloaded
                                val etaSeconds = if (remainingBytes > 0 && status.speed > 0) {
                                    remainingBytes / status.speed
                                } else 0L
                                
                                val elapsedSeconds = status.elapsedTimeSeconds

                                if (etaSeconds > 0 || elapsedSeconds > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Tiempo transcurrido
                                        if (elapsedSeconds > 0) {
                                            Icon(
                                                Icons.Default.AccessTime,
                                                null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                            Text(
                                                text = elapsedSeconds.formatTime(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        
                                        // Separador
                                        if (elapsedSeconds > 0 && etaSeconds > 0) {
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        // ETA
                                        if (etaSeconds > 0) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                text = etaSeconds.formatTime(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is DownloadStatus.Completed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = DownloadColors.Completed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Completado - ${status.totalBytes.formatBytes()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DownloadColors.Completed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                is DownloadStatus.Failed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = DownloadColors.Failed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Error: ${status.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = DownloadColors.Failed,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (status.bytesDownloaded > 0) {
                                Text(
                                    text = "${status.bytesDownloaded.formatBytes()} descargados",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is DownloadStatus.Queued -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = DownloadColors.Queued
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "En cola...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DownloadColors.Queued
                        )
                    }
                }
                is DownloadStatus.Cancelled -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            tint = DownloadColors.Cancelled,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cancelado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DownloadColors.Cancelled
                        )
                    }
                }
            }

            // Chip de prioridad
            Spacer(modifier = Modifier.height(8.dp))
            PriorityChip(priority = download.priority)
        }
    }

    // Diálogo de confirmación de borrado
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("Eliminar descarga") },
            text = { Text("¿Deseas eliminar también el archivo del disco?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(true) // Borrar con archivo
                        showDeleteDialog = false
                    }
                ) {
                    Text("Sí, borrar archivo")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onRemove(false) // Borrar solo de la lista
                            showDeleteDialog = false
                        }
                    ) {
                        Text("Solo de la lista")
                    }
                    TextButton(
                        onClick = { showDeleteDialog = false }
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}

@Composable
private fun StatusBadge(status: DownloadStatus) {
    val (text, color) = when (status) {
        is DownloadStatus.Queued -> "En cola" to DownloadColors.Queued
        is DownloadStatus.Downloading -> "Descargando" to DownloadColors.Downloading
        is DownloadStatus.Paused -> "Pausado" to DownloadColors.Paused
        is DownloadStatus.Completed -> "Completado" to DownloadColors.Completed
        is DownloadStatus.Failed -> "Fallido" to DownloadColors.Failed
        is DownloadStatus.Cancelled -> "Cancelado" to DownloadColors.Cancelled
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Icono especial para estado pausado
            if (status is DownloadStatus.Paused) {
                Icon(
                    Icons.Default.Pause,
                    null,
                    modifier = Modifier.size(12.dp),
                    tint = color
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PriorityChip(priority: Priority) {
    val color = when (priority) {
        Priority.HIGH -> Color(0xFFE53935)
        Priority.MEDIUM -> Color(0xFFFB8C00)
        Priority.LOW -> Color(0xFF43A047)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (priority) {
                    Priority.HIGH -> Icons.Default.KeyboardDoubleArrowUp
                    Priority.MEDIUM -> Icons.Default.Remove
                    Priority.LOW -> Icons.Default.KeyboardDoubleArrowDown
                },
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = priority.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun getStatusColor(status: DownloadStatus): Color {
    return when (status) {
        is DownloadStatus.Downloading -> DownloadColors.Downloading
        is DownloadStatus.Paused -> DownloadColors.Paused
        is DownloadStatus.Completed -> DownloadColors.Completed
        is DownloadStatus.Failed -> DownloadColors.Failed
        is DownloadStatus.Queued -> DownloadColors.Queued
        is DownloadStatus.Cancelled -> DownloadColors.Cancelled
    }
}

private fun getCategoryColor(category: Category): Color {
    return when (category) {
        Category.DOCUMENTS -> CategoryColors.Documents
        Category.MULTIMEDIA -> CategoryColors.Multimedia
        Category.SOFTWARE -> CategoryColors.Software
        Category.COMPRESSED -> CategoryColors.Compressed
        Category.OTHERS -> CategoryColors.Others
    }
}
