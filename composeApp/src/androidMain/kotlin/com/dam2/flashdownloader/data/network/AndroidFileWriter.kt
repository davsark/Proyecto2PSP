package com.dam2.flashdownloader.data.network

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Implementación de FileWriter para Android
 */
class AndroidFileWriter : FileWriter {
    private var outputStream: FileOutputStream? = null
    private var randomAccessFile: RandomAccessFile? = null

    override fun openForWrite(path: String, append: Boolean) {
        try {
            val file = File(path)
            file.parentFile?.mkdirs()

            if (append && file.exists()) {
                // Usar RandomAccessFile para append
                randomAccessFile = RandomAccessFile(file, "rw")
                randomAccessFile?.seek(file.length())
            } else {
                // Crear nuevo archivo
                outputStream = FileOutputStream(file)
            }
        } catch (e: Exception) {
            android.util.Log.e("AndroidFileWriter", "Error abriendo archivo", e)
            throw e
        }
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        try {
            randomAccessFile?.write(buffer, offset, length)
                ?: outputStream?.write(buffer, offset, length)
        } catch (e: Exception) {
            android.util.Log.e("AndroidFileWriter", "Error escribiendo", e)
            throw e
        }
    }

    override fun close() {
        try {
            randomAccessFile?.close()
            outputStream?.close()
        } catch (e: Exception) {
            android.util.Log.e("AndroidFileWriter", "Error cerrando", e)
        } finally {
            randomAccessFile = null
            outputStream = null
        }
    }

    override fun delete(path: String): Boolean {
        return try {
            val file = File(path)
            file.delete()
        } catch (e: Exception) {
            android.util.Log.e("AndroidFileWriter", "Error eliminando archivo", e)
            false
        }
    }
}
