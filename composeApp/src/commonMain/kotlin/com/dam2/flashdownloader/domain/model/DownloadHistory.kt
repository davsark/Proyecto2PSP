package com.dam2.flashdownloader.domain.model

/**
 * Entrada en el historial de descargas completadas
 * Representa un snapshot de una descarga completada con información relevante para el historial
 */
data class DownloadHistoryEntry(
    val id: String,
    val fileName: String,
    val url: String,
    val category: Category,
    val totalSize: Long,
    val completedAt: Long, // Timestamp en milisegundos
    val downloadDuration: Long, // segundos
    val averageSpeed: Long, // bytes/s
    val filePath: String
)

/**
 * Filtros para consultar el historial de descargas
 */
data class DownloadHistoryFilter(
    val category: Category? = null,
    val dateRange: DateRange? = null,
    val searchQuery: String? = null
)

/**
 * Rango de fechas para filtrar el historial
 */
data class DateRange(
    val start: Long, // Timestamp en milisegundos
    val end: Long // Timestamp en milisegundos
)
