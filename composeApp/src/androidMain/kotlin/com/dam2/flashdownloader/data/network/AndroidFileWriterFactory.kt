package com.dam2.flashdownloader.data.network

import android.content.Context
import com.dam2.flashdownloader.data.manager.FileWriterFactory

/**
 * Factory para crear FileWriters en Android
 */
class AndroidFileWriterFactory(private val context: Context) : FileWriterFactory {
    override fun createFileWriter(): FileWriter {
        return AndroidFileWriter(context)
    }
}
