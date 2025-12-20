package com.dam2.flashdownloader.domain.model

/**
 * Niveles de prioridad para ordenar la cola de descargas
 */
enum class Priority(val level: Int, val displayName: String) {
    HIGH(3, "Alta"),
    MEDIUM(2, "Media"),
    LOW(1, "Baja");

    companion object {
        fun fromLevel(level: Int): Priority {
            return values().firstOrNull { it.level == level } ?: MEDIUM
        }
    }
}
