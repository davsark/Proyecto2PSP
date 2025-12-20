package com.dam2.flashdownloader.data.network

import com.dam2.flashdownloader.data.manager.FileWriterFactory

/**
 * Factory para crear FileWriters en Android
 */
class AndroidFileWriterFactory : FileWriterFactory {
    override fun createFileWriter(): FileWriter {
        return AndroidFileWriter()
    }
}
