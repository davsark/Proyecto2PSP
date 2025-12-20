package com.dam2.flashdownloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dam2.flashdownloader.domain.model.DownloadStatistics
import com.dam2.flashdownloader.utils.formatBytes
import com.dam2.flashdownloader.utils.formatSpeed

/**
 * Panel lateral con estadísticas globales de descargas
 */
@Composable
fun StatisticsPanel(
    statistics: DownloadStatistics,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Título
            Text(
                text = "Estadísticas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            // Total de descargas
            StatCard(
                icon = Icons.Default.CloudDownload,
                label = "Total de descargas",
                value = statistics.totalDownloads.toString(),
                iconTint = MaterialTheme.colorScheme.primary
            )

            // Descargas activas
            StatCard(
                icon = Icons.Default.Download,
                label = "Activas",
                value = statistics.activeDownloads.toString(),
                iconTint = MaterialTheme.colorScheme.tertiary
            )

            // En cola
            StatCard(
                icon = Icons.Default.Queue,
                label = "En cola",
                value = statistics.queuedDownloads.toString(),
                iconTint = MaterialTheme.colorScheme.secondary
            )

            // Pausadas
            StatCard(
                icon = Icons.Default.Pause,
                label = "Pausadas",
                value = statistics.pausedDownloads.toString(),
                iconTint = MaterialTheme.colorScheme.tertiary
            )

            // Completadas
            StatCard(
                icon = Icons.Default.CheckCircle,
                label = "Completadas",
                value = statistics.completedDownloads.toString(),
                iconTint = MaterialTheme.colorScheme.primary
            )

            // Fallidas
            if (statistics.failedDownloads > 0) {
                StatCard(
                    icon = Icons.Default.Error,
                    label = "Fallidas",
                    value = statistics.failedDownloads.toString(),
                    iconTint = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Total descargado
            StatCard(
                icon = Icons.Default.Storage,
                label = "Total descargado",
                value = statistics.totalBytesDownloaded.formatBytes(),
                iconTint = MaterialTheme.colorScheme.primary
            )

            // Velocidad actual
            if (statistics.currentGlobalSpeed > 0) {
                StatCard(
                    icon = Icons.Default.Speed,
                    label = "Velocidad actual",
                    value = statistics.currentGlobalSpeed.formatSpeed(),
                    iconTint = MaterialTheme.colorScheme.secondary
                )
            }

            // Velocidad media
            if (statistics.averageSpeed > 0) {
                StatCard(
                    icon = Icons.Default.BarChart,
                    label = "Velocidad media",
                    value = statistics.averageSpeed.formatSpeed(),
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer con información
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Flash Downloader",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "v1.0.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = iconTint
            )
        }
    }
}
