package com.dam2.flashdownloader.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dam2.flashdownloader.domain.model.Category
import com.dam2.flashdownloader.domain.model.Priority

/**
 * Diálogo para añadir una nueva descarga
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadDialog(
    url: String,
    onUrlChange: (String) -> Unit,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    category: Category?,
    onCategoryChange: (Category?) -> Unit,
    priority: Priority,
    onPriorityChange: (Priority) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onPasteFromClipboard: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(600.dp)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Título
                Text(
                    text = "Añadir nueva descarga",
                    style = MaterialTheme.typography.headlineSmall
                )

                Divider()

                // URL
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("URL *") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = onPasteFromClipboard) {
                            Icon(Icons.Default.ContentPaste, "Pegar desde portapapeles")
                        }
                    },
                    supportingText = { Text("URL completa del archivo a descargar") },
                    singleLine = true
                )

                // Nombre de archivo (opcional)
                OutlinedTextField(
                    value = fileName,
                    onValueChange = onFileNameChange,
                    label = { Text("Nombre de archivo (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Se detectará automáticamente si no se especifica") },
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
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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

                Divider()

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = url.isNotBlank()
                    ) {
                        Text("Añadir")
                    }
                }
            }
        }
    }
}
