package com.dam2.flashdownloader.data.network

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Implementación de FileWriter para Android usando MediaStore (Scoped Storage)
 * Soporta versiones antiguas (Legacy) y nuevas (Android 10+)
 */
class AndroidFileWriter(private val context: Context) : FileWriter {
    private var outputStream: OutputStream? = null
    private var currentUri: Uri? = null

    override fun openForWrite(path: String, append: Boolean) {
        // En este diseño, 'path' es referencial para UI, pero en Android 10+ 
        // escribimos en MediaStore.Downloads.
        // Si append es true, intentamos recuperar el archivo existente.
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                openMediaStoreStream(path, append)
            } else {
                openLegacyStream(path, append)
            }
        } catch (e: Exception) {
            android.util.Log.e("AndroidFileWriter", "Error abriendo stream: ${e.message}", e)
            throw e
        }
    }

    private fun openMediaStoreStream(filenamePath: String, append: Boolean) {
        val filename = File(filenamePath).name
        
        // Si es append, necesitamos buscar si el archivo ya existe en MediaStore
        if (append) {
            val existingUri = findExistingFileUri(filename)
            if (existingUri != null) {
                currentUri = existingUri
                // Abrir en modo 'wa' (write append) o 'rwt'
                outputStream = context.contentResolver.openOutputStream(existingUri, "wa")
                return
            }
        }

        // Si no es append o no existe, crear nueva entrada
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            // Categoría genérica, podría refinarse según extensión
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream") 
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FlashDownloader")
            
            // Marcar como pendiente para evitar escaneos prematuros
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("No se pudo crear entrada en MediaStore")
        
        currentUri = uri
        outputStream = context.contentResolver.openOutputStream(uri)
    }

    private fun findExistingFileUri(filename: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(filename, "%FlashDownloader%")
        
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }

    private fun openLegacyStream(path: String, append: Boolean) {
        val file = File(path)
        file.parentFile?.mkdirs()
        outputStream = FileOutputStream(file, append)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        outputStream?.write(buffer, offset, length)
    }

    override fun flush() {
        try {
            outputStream?.flush()
        } catch (e: Exception) {
            android.util.Log.e("AndroidFileWriter", "Error haciendo flush", e)
            // No lanzar excepción, flush es best-effort
        }
    }

    override fun close() {
        try {
            outputStream?.flush()
            outputStream?.close()
        } catch (e: Exception) {
            android.util.Log.e("AndroidFileWriter", "Error cerrando stream", e)
        } finally {
            outputStream = null
        }
    }

    override fun delete(path: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Para Android 10+, buscar y eliminar del MediaStore
                val filename = File(path).name
                val uri = findExistingFileUri(filename)
                if (uri != null) {
                    context.contentResolver.delete(uri, null, null) > 0
                } else {
                    false
                }
            } else {
                // Para versiones antiguas, eliminar archivo directamente
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AndroidFileWriter", "Error eliminando archivo: ${e.message}", e)
            false
        }
    }
}
