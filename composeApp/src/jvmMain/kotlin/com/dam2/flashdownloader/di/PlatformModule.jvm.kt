package com.dam2.flashdownloader.di

import com.dam2.flashdownloader.data.manager.FileWriterFactory
import com.dam2.flashdownloader.data.network.JvmFileWriterFactory
import com.dam2.flashdownloader.data.repository.PersistentStorage
import com.dam2.flashdownloader.data.storage.JvmPersistentStorage
import com.dam2.flashdownloader.utils.ClipboardManager
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * Módulo de dependencias específicas para JVM (Desktop)
 */
actual fun platformModule(): Module = module {
    // Directorio de almacenamiento
    single {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".flashdownloader")
        appDir.absolutePath
    }

    // Directorio de descargas
    single(qualifier = org.koin.core.qualifier.named("downloadPath")) {
        val userHome = System.getProperty("user.home")
        val downloadsDir = File(userHome, "Downloads/FlashDownloader")
        downloadsDir.mkdirs()
        downloadsDir.absolutePath
    }

    // PersistentStorage
    single<PersistentStorage> {
        JvmPersistentStorage(get())
    }

    // FileWriterFactory
    single<FileWriterFactory> {
        JvmFileWriterFactory()
    }

    // ClipboardManager
    single {
        ClipboardManager()
    }
}
