package com.dam2.flashdownloader.data.network

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Implementación de FileWriter para JVM
 */
class JvmFileWriter : FileWriter {
    private var outputStream: FileOutputStream? = null
    private var randomAccessFile: RandomAccessFile? = null

    override fun openForWrite(path: String, append: Boolean) {
        try {
            val file = File(path)
            file.parentFile?.mkdirs()

            if (append && file.exists()) {
                // Usar RandomAccessFile para append eficiente
                randomAccessFile = RandomAccessFile(file, "rw")
                randomAccessFile?.seek(file.length())
            } else {
                // Crear nuevo archivo
                outputStream = FileOutputStream(file)
            }
        } catch (e: Exception) {
            println("Error abriendo archivo para escritura: ${e.message}")
            throw e
        }
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        try {
            randomAccessFile?.write(buffer, offset, length)
                ?: outputStream?.write(buffer, offset, length)
        } catch (e: Exception) {
            println("Error escribiendo datos: ${e.message}")
            throw e
        }
    }

    override fun flush() {
        try {
            randomAccessFile?.fd?.sync()  // Flush to disk for RandomAccessFile
            outputStream?.flush()  // Flush for FileOutputStream
        } catch (e: Exception) {
            println("Error haciendo flush: ${e.message}")
            // No lanzar excepción, flush es best-effort
        }
    }

    override fun close() {
        try {
            randomAccessFile?.close()
            outputStream?.close()
        } catch (e: Exception) {
            println("Error cerrando archivo: ${e.message}")
        } finally {
            randomAccessFile = null
            outputStream = null
        }
    }

    override fun delete(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            println("Error eliminando archivo: ${e.message}")
            false
        }
    }
}
