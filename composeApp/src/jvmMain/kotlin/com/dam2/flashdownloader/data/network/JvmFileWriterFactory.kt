package com.dam2.flashdownloader.data.network

import com.dam2.flashdownloader.data.manager.FileWriterFactory

/**
 * Factory para crear FileWriters en JVM
 */
class JvmFileWriterFactory : FileWriterFactory {
    override fun createFileWriter(): FileWriter {
        return JvmFileWriter()
    }
}
