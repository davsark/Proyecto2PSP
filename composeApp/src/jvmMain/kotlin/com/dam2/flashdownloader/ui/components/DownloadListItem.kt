package com.dam2.flashdownloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.dam2.flashdownloader.ui.utils.dragContainer

/**
 * Item de la lista de descargas con toda la información y controles
 */
@Composable
fun DownloadListItem(
    download: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpenFolder: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    dragDropState: com.dam2.flashdownloader.ui.utils.DragDropState? = null,
    index: Int = -1
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (dragDropState != null && index >= 0) {
                    Modifier.dragContainer(dragDropState, index)
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Primera fila: Icono de categoría, nombre y estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag Handle (si se proporciona modificador)
                // Solo mostrar si es activo
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Reordenar",
                    modifier = dragHandleModifier
                        .size(24.dp)
                        .padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

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

                // Nombre de archivo y URL
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = download.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = download.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Badge de estado
                StatusBadge(status = download.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Barra de progreso
            when (val status = download.status) {
                is DownloadStatus.Downloading, is DownloadStatus.Paused -> {
                    LinearProgressIndicator(
                        progress = {
                            when (status) {
                                is DownloadStatus.Downloading -> status.progress
                                is DownloadStatus.Paused -> status.progress
                                else -> 0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = getStatusColor(status),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Información de progreso
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = when (status) {
                                is DownloadStatus.Downloading -> "${status.progressPercentage}%"
                                is DownloadStatus.Paused -> "${status.progressPercentage}% (Pausado)"
                                else -> "0%"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )

                        if (status is DownloadStatus.Downloading) {
                            Text(
                                text = "${download.downloadedBytes.formatBytes()} / ${download.totalSize.formatBytes()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (status is DownloadStatus.Downloading && status.speed > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Velocidad
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = status.speed.formatSpeed(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Tiempo transcurrido con etiqueta
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Transcurrido: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = status.totalElapsedSeconds.formatTime(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }

                            // Tiempo restante con etiqueta
                            if (status.estimatedTimeRemaining > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Restante: ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = status.estimatedTimeRemaining.formatTime(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                    
                    // Mostrar tiempo transcurrido también en Paused
                    if (status is DownloadStatus.Paused) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pausado - Transcurrido: ${status.elapsedSeconds.formatTime()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
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
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Completado - ${status.totalBytes.formatBytes()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DownloadColors.Completed
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
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Error: ${status.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DownloadColors.Failed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                is DownloadStatus.Queued -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
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
                else -> {}
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prioridad
                PriorityChip(priority = download.priority)

                Spacer(modifier = Modifier.weight(1f))

                // Mover arriba/abajo
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Mover arriba",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Mover abajo",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Controles según estado
                when (download.status) {
                    is DownloadStatus.Downloading -> {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pausar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is DownloadStatus.Paused, is DownloadStatus.Queued -> {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Reanudar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is DownloadStatus.Failed -> {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reintentar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is DownloadStatus.Completed -> {
                        IconButton(
                            onClick = onOpenFolder,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Abrir carpeta",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    else -> {}
                }

                // Cancelar o eliminar
                IconButton(
                    onClick = if (download.status.isActive) onCancel else onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (download.status.isActive) Icons.Default.Close else Icons.Default.Delete,
                        contentDescription = if (download.status.isActive) "Cancelar" else "Eliminar",
                        tint = DownloadColors.Failed
                    )
                }
            }
        }
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
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
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
        shape = RoundedCornerShape(12.dp),
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
                modifier = Modifier.size(14.dp),
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
