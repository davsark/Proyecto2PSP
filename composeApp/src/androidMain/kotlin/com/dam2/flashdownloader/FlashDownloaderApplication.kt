package com.dam2.flashdownloader

import android.app.Application
import com.dam2.flashdownloader.di.commonModule
import com.dam2.flashdownloader.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class para Android - Inicialización de Koin
 */
class FlashDownloaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Inicializar Koin
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@FlashDownloaderApplication)
            modules(commonModule(), platformModule())
        }
    }
}
