package com.dam2.flashdownloader.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dam2.flashdownloader.domain.model.DownloadItem
import com.dam2.flashdownloader.utils.formatBytes
import com.dam2.flashdownloader.utils.formatSpeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailsBottomSheet(
    download: DownloadItem,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = download.fileName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = download.status::class.simpleName ?: "Desconocido",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

            // Detalles
            DetailItem(Icons.Default.Link, "URL", download.url)
            DetailItem(
                Icons.Default.Storage, 
                "Tamaño", 
                "${download.downloadedBytes.formatBytes()} / ${download.totalSize.formatBytes()}"
            )
            DetailItem(
                Icons.Default.Speed,
                "Velocidad Actual",
                download.currentSpeed.formatSpeed()
            )
            val elapsedSeconds = when (val s = download.status) {
                is com.dam2.flashdownloader.domain.model.DownloadStatus.Downloading -> s.totalElapsedSeconds
                is com.dam2.flashdownloader.domain.model.DownloadStatus.Paused -> s.elapsedSeconds
                is com.dam2.flashdownloader.domain.model.DownloadStatus.Queued -> s.elapsedSeconds ?: 0L
                else -> 0L
            }

            DetailItem(
                Icons.Default.Timer,
                "Tiempo Transcurrido",
                "${elapsedSeconds} s"
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
