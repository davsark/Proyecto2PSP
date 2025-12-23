package com.dam2.flashdownloader.data.repository

import com.dam2.flashdownloader.domain.model.*
import com.dam2.flashdownloader.domain.repository.DownloadRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implementación del repositorio usando almacenamiento en JSON
 * Thread-safe mediante el uso de Mutex
 */
class DownloadRepositoryImpl(
    private val storage: PersistentStorage
) : DownloadRepository {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override suspend fun saveDownload(download: DownloadItem): Result<Unit> = mutex.withLock {
        return try {
            val downloads = loadAllDownloadsInternal().toMutableList()
            downloads.add(download.toSerializable())
            storage.saveDownloads(json.encodeToString(downloads))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateDownload(download: DownloadItem): Result<Unit> = mutex.withLock {
        return try {
            val downloads = loadAllDownloadsInternal().toMutableList()
            val index = downloads.indexOfFirst { it.id == download.id }
            if (index != -1) {
                downloads[index] = download.toSerializable()
                storage.saveDownloads(json.encodeToString(downloads))
                Result.success(Unit)
            } else {
                Result.failure(Exception("Download not found: ${download.id}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateAll(downloads: List<DownloadItem>): Result<Unit> = mutex.withLock {
        return try {
            // Convierte toda la lista a serializable y guarda
            val serializableList = downloads.map { it.toSerializable() }
            storage.saveDownloads(json.encodeToString(serializableList))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDownload(id: String): Result<Unit> = mutex.withLock {
        return try {
            val downloads = loadAllDownloadsInternal().toMutableList()
            downloads.removeAll { it.id == id }
            storage.saveDownloads(json.encodeToString(downloads))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDownload(id: String): Result<DownloadItem?> = mutex.withLock {
        return try {
            val download = loadAllDownloadsInternal().firstOrNull { it.id == id }?.toDomain()
            Result.success(download)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllDownloads(): Result<List<DownloadItem>> = mutex.withLock {
        return try {
            val downloads = loadAllDownloadsInternal().map { it.toDomain() }
            Result.success(downloads)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearCompleted(): Result<Unit> = mutex.withLock {
        return try {
            val downloads = loadAllDownloadsInternal().toMutableList()
            downloads.removeAll { it.status is SerializableDownloadStatus.Completed }
            storage.saveDownloads(json.encodeToString(downloads))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun savePartialData(id: String, bytesDownloaded: Long): Result<Unit> = mutex.withLock {
        return try {
            storage.savePartialData(id, bytesDownloaded.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPartialData(id: String): Result<Long> = mutex.withLock {
        return try {
            val data = storage.getPartialData(id)?.toLongOrNull() ?: 0L
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadAllDownloadsInternal(): List<SerializableDownloadItem> {
        val data = storage.loadDownloads() ?: return emptyList()
        return try {
            json.decodeFromString<List<SerializableDownloadItem>>(data)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Interfaz para abstracción del almacenamiento específico de plataforma
 */
interface PersistentStorage {
    fun saveDownloads(data: String)
    fun loadDownloads(): String?
    fun savePartialData(id: String, data: String)
    fun getPartialData(id: String): String?
}

/**
 * Versión serializable de DownloadItem para persistencia
 */
@Serializable
private data class SerializableDownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val category: String,
    val priority: Int,
    val status: SerializableDownloadStatus,
    val createdAt: Long,
    val speedLimit: Long? = null,
    val localPath: String? = null,
    val hash: String? = null,
    val totalBytes: Long = -1L
)

@Serializable
private sealed class SerializableDownloadStatus {
    @Serializable
    data class Queued(
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = -1L,
        val elapsedSeconds: Long? = null
    ) : SerializableDownloadStatus()

    @Serializable
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val elapsedSeconds: Long = 0L,
        val sessionStartTime: Long
    ) : SerializableDownloadStatus()

    @Serializable
    data class Paused(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val elapsedSeconds: Long
    ) : SerializableDownloadStatus()

    @Serializable
    data class Completed(
        val filePath: String,
        val totalBytes: Long
    ) : SerializableDownloadStatus()

    @Serializable
    data class Failed(
        val error: String,
        val bytesDownloaded: Long = 0L
    ) : SerializableDownloadStatus()

    @Serializable
    data object Cancelled : SerializableDownloadStatus()
}

// Extension functions para conversión
private fun DownloadItem.toSerializable() = SerializableDownloadItem(
    id = id,
    url = url,
    fileName = fileName,
    category = category.name,
    priority = priority.level,
    status = status.toSerializable(),
    createdAt = createdAt,
    speedLimit = speedLimit,
    localPath = localPath,
    hash = hash,
    totalBytes = totalSize
)

private fun DownloadStatus.toSerializable(): SerializableDownloadStatus = when (this) {
    is DownloadStatus.Queued -> SerializableDownloadStatus.Queued(bytesDownloaded, totalBytes, elapsedSeconds)
    is DownloadStatus.Downloading -> SerializableDownloadStatus.Downloading(bytesDownloaded, totalBytes, elapsedSeconds, sessionStartTime)
    is DownloadStatus.Paused -> SerializableDownloadStatus.Paused(bytesDownloaded, totalBytes, elapsedSeconds)
    is DownloadStatus.Completed -> SerializableDownloadStatus.Completed(filePath, totalBytes)
    is DownloadStatus.Failed -> SerializableDownloadStatus.Failed(error, bytesDownloaded)
    is DownloadStatus.Cancelled -> SerializableDownloadStatus.Cancelled
}

private fun SerializableDownloadItem.toDomain() = DownloadItem(
    id = id,
    url = url,
    fileName = fileName,
    category = Category.valueOf(category),
    priority = Priority.fromLevel(priority),
    status = status.toDomain(),
    createdAt = createdAt,
    speedLimit = speedLimit,
    localPath = localPath,
    hash = hash,
    metadata = DownloadMetadata(totalBytes = totalBytes)
)

private fun SerializableDownloadStatus.toDomain(): DownloadStatus = when (this) {
    is SerializableDownloadStatus.Queued -> DownloadStatus.Queued(bytesDownloaded, totalBytes, elapsedSeconds)
    is SerializableDownloadStatus.Downloading -> {
        DownloadStatus.Downloading(bytesDownloaded, totalBytes, 0L, elapsedSeconds, sessionStartTime)
    }
    is SerializableDownloadStatus.Paused -> DownloadStatus.Paused(bytesDownloaded, totalBytes, elapsedSeconds)
    is SerializableDownloadStatus.Completed -> DownloadStatus.Completed(filePath, totalBytes)
    is SerializableDownloadStatus.Failed -> DownloadStatus.Failed(error, bytesDownloaded)
    is SerializableDownloadStatus.Cancelled -> DownloadStatus.Cancelled
}
