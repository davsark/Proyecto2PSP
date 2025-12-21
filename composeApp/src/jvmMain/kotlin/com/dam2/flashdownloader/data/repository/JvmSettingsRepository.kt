package com.dam2.flashdownloader.data.repository

import com.dam2.flashdownloader.domain.repository.SettingsRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * Implementación de SettingsRepository para JVM usando Properties
 */
class JvmSettingsRepository(private val storagePath: String) : SettingsRepository {
    
    private val properties = Properties()
    private val settingsFile = File(storagePath, SETTINGS_FILE_NAME)
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        if (settingsFile.exists()) {
            try {
                FileInputStream(settingsFile).use { input ->
                    properties.load(input)
                }
            } catch (e: Exception) {
                println("Error loading settings: ${e.message}")
            }
        }
    }
    
    private fun saveSettings() {
        try {
            settingsFile.parentFile?.mkdirs()
            FileOutputStream(settingsFile).use { output ->
                properties.store(output, "Flash Downloader Settings")
            }
        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
        }
    }
    
    override fun getMaxConcurrentDownloads(): Int {
        return properties.getProperty(
            KEY_MAX_CONCURRENT,
            SettingsRepository.DEFAULT_MAX_CONCURRENT.toString()
        ).toIntOrNull() ?: SettingsRepository.DEFAULT_MAX_CONCURRENT
    }
    
    override fun setMaxConcurrentDownloads(value: Int) {
        properties.setProperty(KEY_MAX_CONCURRENT, value.toString())
        saveSettings()
    }
    
    override fun getGlobalSpeedLimit(): Long? {
        val value = properties.getProperty(KEY_SPEED_LIMIT, "-1")
        val longValue = value.toLongOrNull() ?: -1L
        return if (longValue == -1L) null else longValue
    }
    
    override fun setGlobalSpeedLimit(value: Long?) {
        properties.setProperty(KEY_SPEED_LIMIT, (value ?: -1L).toString())
        saveSettings()
    }
    
    override fun getIsDarkTheme(): Boolean {
        return properties.getProperty(
            KEY_DARK_THEME,
            SettingsRepository.DEFAULT_DARK_THEME.toString()
        ).toBoolean()
    }
    
    override fun setIsDarkTheme(value: Boolean) {
        properties.setProperty(KEY_DARK_THEME, value.toString())
        saveSettings()
    }
    
    companion object {
        private const val SETTINGS_FILE_NAME = "settings.properties"
        private const val KEY_MAX_CONCURRENT = "max_concurrent_downloads"
        private const val KEY_SPEED_LIMIT = "global_speed_limit"
        private const val KEY_DARK_THEME = "is_dark_theme"
    }
}
