package com.dam2.flashdownloader.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Diálogo de configuración
 */
@Composable
fun SettingsDialog(
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(500.dp)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Título
                Text(
                    text = "Configuración",
                    style = MaterialTheme.typography.headlineSmall
                )

                Divider()

                // Descargas simultáneas
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Descargas simultáneas",
                        style = MaterialTheme.typography.titleMedium
                    )
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
                        label = { Text("Número de descargas (1-10)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Máximo de descargas activas al mismo tiempo") }
                    )
                }

                // Límite de velocidad global
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Límite de velocidad global",
                            style = MaterialTheme.typography.titleMedium
                        )
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
                            singleLine = true,
                            enabled = speedLimitEnabled,
                            supportingText = { Text("Velocidad máxima total de todas las descargas") }
                        )
                    }
                }

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
                            text = "Monitorea el portapapeles y sugiere descargas automáticamente",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}
