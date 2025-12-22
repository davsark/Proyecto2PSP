package com.dam2.flashdownloader.di

import android.content.Context
import android.os.Environment
import com.dam2.flashdownloader.data.manager.FileWriterFactory
import com.dam2.flashdownloader.data.network.AndroidFileWriterFactory
import com.dam2.flashdownloader.data.repository.PersistentStorage
import com.dam2.flashdownloader.data.storage.AndroidPersistentStorage
import com.dam2.flashdownloader.utils.ClipboardManager
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * Módulo de dependencias específicas para Android
 */
actual fun platformModule(): Module = module {
    // Context se debe proveer desde la Activity/Application
    // Este módulo asume que el context ya está registrado en Koin

    // Directorio de almacenamiento (usa filesDir de la app)
    single {
        val context: Context = get()
        context.filesDir.absolutePath
    }

    // Directorio de descargas (usa Downloads público)
    single(qualifier = org.koin.core.qualifier.named("downloadPath")) {
        val context: Context = get()
        // Usar el directorio de descargas público o el privado de la app
        val downloadsDir = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "FlashDownloader"
            )
        } else {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "FlashDownloader")
        }
        downloadsDir.mkdirs()
        downloadsDir.absolutePath
    }

    // PersistentStorage
    single<PersistentStorage> {
        AndroidPersistentStorage(get())
    }

    // FileWriterFactory
    single<FileWriterFactory> {
        AndroidFileWriterFactory(get())
    }

    // ClipboardManager
    single {
        ClipboardManager(get())
    }
}
