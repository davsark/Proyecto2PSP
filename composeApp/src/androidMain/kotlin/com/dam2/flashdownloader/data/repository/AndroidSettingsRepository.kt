package com.dam2.flashdownloader.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.dam2.flashdownloader.domain.repository.SettingsRepository

/**
 * Implementación de SettingsRepository para Android usando SharedPreferences
 */
class AndroidSettingsRepository(context: Context) : SettingsRepository {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    override fun getMaxConcurrentDownloads(): Int {
        return prefs.getInt(
            KEY_MAX_CONCURRENT,
            SettingsRepository.DEFAULT_MAX_CONCURRENT
        )
    }
    
    override fun setMaxConcurrentDownloads(value: Int) {
        prefs.edit().putInt(KEY_MAX_CONCURRENT, value).apply()
    }
    
    override fun getGlobalSpeedLimit(): Long? {
        val value = prefs.getLong(KEY_SPEED_LIMIT, -1L)
        return if (value == -1L) null else value
    }
    
    override fun setGlobalSpeedLimit(value: Long?) {
        prefs.edit().putLong(KEY_SPEED_LIMIT, value ?: -1L).apply()
    }
    
    override fun getIsDarkTheme(): Boolean {
        return prefs.getBoolean(
            KEY_DARK_THEME,
            SettingsRepository.DEFAULT_DARK_THEME
        )
    }
    
    override fun setIsDarkTheme(value: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()
    }
    
    companion object {
        private const val PREFS_NAME = "flash_downloader_settings"
        private const val KEY_MAX_CONCURRENT = "max_concurrent_downloads"
        private const val KEY_SPEED_LIMIT = "global_speed_limit"
        private const val KEY_DARK_THEME = "is_dark_theme"
    }
}
