package com.dam2.flashdownloader.utils

/**
 * Manager multiplataforma para acceder al portapapeles
 */
expect class ClipboardManager {
    /**
     * Obtiene el texto actual del portapapeles
     */
    suspend fun getText(): String?

    /**
     * Establece texto en el portapapeles
     */
    suspend fun setText(text: String)

    /**
     * Detecta si el contenido del portapapeles es una URL
     */
    suspend fun hasUrl(): Boolean
}
