package com.dam2.flashdownloader.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Bottom Sheet de configuración para Android
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    sheetState: SheetState,
    maxConcurrentDownloads: Int,
    onMaxConcurrentDownloadsChange: (Int) -> Unit,
    globalSpeedLimit: Long?,
    onGlobalSpeedLimitChange: (Long?) -> Unit,
    autoDetectClipboard: Boolean,
    onAutoDetectClipboardChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var concurrentDownloads by remember { mutableStateOf(maxConcurrentDownloads.toString()) }
    var speedLimitEnabled by remember { mutableStateOf(globalSpeedLimit != null) }
    var speedLimit by remember {
        mutableStateOf((globalSpeedLimit?.div(1024) ?: 0).toString())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Título
            Text(
                text = "Configuración",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Descargas simultáneas
            OutlinedTextField(
                value = concurrentDownloads,
                onValueChange = { value ->
                    concurrentDownloads = value
                    value.toIntOrNull()?.let { num ->
                        if (num in 1..10) {
                            onMaxConcurrentDownloadsChange(num)
                        }
                    }
                },
                label = { Text("Descargas simultáneas (1-10)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Máximo de descargas activas al mismo tiempo") }
            )

            Divider()

            // Límite de velocidad
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Límite de velocidad global",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Velocidad máxima total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = speedLimitEnabled,
                    onCheckedChange = {
                        speedLimitEnabled = it
                        if (!it) {
                            onGlobalSpeedLimitChange(null)
                        } else {
                            speedLimit.toLongOrNull()?.let { limit ->
                                onGlobalSpeedLimitChange(limit * 1024)
                            }
                        }
                    }
                )
            }

            if (speedLimitEnabled) {
                OutlinedTextField(
                    value = speedLimit,
                    onValueChange = { value ->
                        speedLimit = value
                        value.toLongOrNull()?.let { limit ->
                            if (limit > 0) {
                                onGlobalSpeedLimitChange(limit * 1024)
                            }
                        }
                    },
                    label = { Text("Velocidad máxima (KB/s)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Divider()

            // Auto-detectar portapapeles
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Detectar URLs en portapapeles",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Sugerir descargas automáticamente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoDetectClipboard,
                    onCheckedChange = onAutoDetectClipboardChange
                )
            }

            Divider()

            // Botón cerrar
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar")
            }
        }
    }
}
