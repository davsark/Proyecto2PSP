package com.dam2.flashdownloader.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dam2.flashdownloader.domain.model.Category
import com.dam2.flashdownloader.domain.model.Priority

/**
 * Bottom Sheet para añadir descarga en Android
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadBottomSheet(
    sheetState: SheetState,
    url: String,
    onUrlChange: (String) -> Unit,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    category: Category?,
    onCategoryChange: (Category?) -> Unit,
    priority: Priority,
    onPriorityChange: (Priority) -> Unit,
    hash: String,
    onHashChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onPasteFromClipboard: () -> Unit
) {
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
                text = "Nueva descarga",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // URL
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("URL *") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = onPasteFromClipboard) {
                        Icon(Icons.Default.ContentPaste, "Pegar")
                    }
                },
                singleLine = true
            )

            // Nombre de archivo (opcional)
            OutlinedTextField(
                value = fileName,
                onValueChange = onFileNameChange,
                label = { Text("Nombre (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Categoría
            var categoryExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = category?.displayName ?: "Auto-detectar",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoría") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Auto-detectar") },
                        onClick = {
                            onCategoryChange(null)
                            categoryExpanded = false
                        }
                    )
                    Category.entries.forEach { cat ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(cat.iconName)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(cat.displayName)
                                }
                            },
                            onClick = {
                                onCategoryChange(cat)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            // Prioridad
            var priorityExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = priorityExpanded,
                onExpandedChange = { priorityExpanded = it }
            ) {
                OutlinedTextField(
                    value = priority.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Prioridad") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = priorityExpanded,
                    onDismissRequest = { priorityExpanded = false }
                ) {
                    Priority.entries.forEach { prio ->
                        DropdownMenuItem(
                            text = { Text(prio.displayName) },
                            onClick = {
                                onPriorityChange(prio)
                                priorityExpanded = false
                            }
                        )
                    }
                }
            }

            // Hash SHA-256 (opcional)
            OutlinedTextField(
                value = hash,
                onValueChange = onHashChange,
                label = { Text("Hash SHA-256 (opcional)") },
                placeholder = { Text("Para verificar integridad") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text(
                        text = "Opcional: Hash para verificar que el archivo descargado es correcto",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )

            // Botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = url.isNotBlank()
                ) {
                    Text("Añadir")
                }
            }
        }
    }
}
