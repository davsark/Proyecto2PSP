package com.dam2.flashdownloader.domain.repository

/**
 * Repository para persistir la configuración de la aplicación
 */
interface SettingsRepository {
    /**
     * Número máximo de descargas concurrentes
     */
    fun getMaxConcurrentDownloads(): Int
    fun setMaxConcurrentDownloads(value: Int)
    
    /**
     * Límite de velocidad global en bytes/segundo (null = sin límite)
     */
    fun getGlobalSpeedLimit(): Long?
    fun setGlobalSpeedLimit(value: Long?)
    
    /**
     * Tema oscuro activado
     */
    fun getIsDarkTheme(): Boolean
    fun setIsDarkTheme(value: Boolean)
    
    companion object {
        const val DEFAULT_MAX_CONCURRENT = 3
        const val DEFAULT_DARK_THEME = false
    }
}
