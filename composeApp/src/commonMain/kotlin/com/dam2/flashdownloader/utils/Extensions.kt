package com.dam2.flashdownloader.utils

import com.dam2.flashdownloader.domain.model.DownloadItem

/**
 * Extensiones útiles para formateo y utilidades
 */

/**
 * Formatea bytes a una representación legible
 */
fun Long.formatBytes(): String {
    return when {
        this < 0 -> "Desconocido"
        this < 1024 -> "$this B"
        this < 1024 * 1024 -> String.format("%.2f KB", this / 1024.0)
        this < 1024 * 1024 * 1024 -> String.format("%.2f MB", this / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", this / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * Formatea bytes/segundo a una velocidad legible
 */
fun Long.formatSpeed(): String {
    return when {
        this < 1024 -> "$this B/s"
        this < 1024 * 1024 -> String.format("%.2f KB/s", this / 1024.0)
        else -> String.format("%.2f MB/s", this / (1024.0 * 1024.0))
    }
}

/**
 * Formatea segundos a tiempo legible
 */
fun Long.formatTime(): String {
    if (this < 0) return "Calculando..."
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/**
 * Extrae URLs de un texto
 */
fun String.extractUrls(): List<String> {
    val urlPattern = Regex("https?://[^\\s]+")
    return urlPattern.findAll(this).map { it.value }.toList()
}

/**
 * Valida si una cadena es una URL válida
 */
fun String.isValidUrl(): Boolean {
    return this.startsWith("http://", ignoreCase = true) ||
            this.startsWith("https://", ignoreCase = true)
}

/**
 * Obtiene la extensión de un archivo
 */
fun String.getFileExtension(): String {
    return this.substringAfterLast('.', "")
}

/**
 * Trunca una cadena a una longitud máxima
 */
fun String.truncate(maxLength: Int): String {
    return if (this.length <= maxLength) this
    else this.substring(0, maxLength - 3) + "..."
}
