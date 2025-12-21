package com.dam2.flashdownloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dam2.flashdownloader.MainActivity
import com.dam2.flashdownloader.domain.model.DownloadStatistics

/**
 * Helper para gestión de notificaciones del servicio de descargas
 * Maneja la creación de canales (Android O+) y actualización de notificaciones
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "download_service_channel"
        const val CHANNEL_NAME = "Descargas"
        const val NOTIFICATION_ID = 1001
        
        // IDs para acciones de notificación
        const val ACTION_PAUSE_ALL = "com.dam2.flashdownloader.PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "com.dam2.flashdownloader.CANCEL_ALL"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Crea el canal de notificaciones para Android O+ (API 26+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // LOW para no molestar al usuario
            ).apply {
                description = "Notificaciones de descargas en segundo plano"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Construye la notificación inicial del servicio foreground
     */
    fun buildForegroundNotification(): Notification {
        return buildNotification(
            title = "FlashDownloader",
            text = "Iniciando servicio de descargas...",
            activeDownloads = 0,
            totalBytes = 0L,
            downloadedBytes = 0L
        )
    }

    /**
     * Construye una notificación actualizada con estadísticas
     */
    fun buildNotification(
        title: String = "FlashDownloader",
        text: String,
        activeDownloads: Int,
        totalBytes: Long = 0L,
        downloadedBytes: Long = 0L
    ): Notification {
        // Intent para abrir la app al tocar la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // No se puede deslizar para cerrar
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // Mostrar progreso si hay descargas activas y conocemos el total
        if (activeDownloads > 0 && totalBytes > 0) {
            val progress = ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()
            builder.setProgress(100, progress, false)
        }

        // Añadir acciones solo si hay descargas activas
        if (activeDownloads > 0) {
            // Acción: Pausar todas
            val pauseIntent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE_ALL
            }
            val pausePendingIntent = PendingIntent.getService(
                context,
                1,
                pauseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pausar",
                pausePendingIntent
            )

            // Acción: Cancelar todas
            val cancelIntent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_ALL
            }
            val cancelPendingIntent = PendingIntent.getService(
                context,
                2,
                cancelIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                android.R.drawable.ic_delete,
                "Cancelar",
                cancelPendingIntent
            )
        }

        return builder.build()
    }

    /**
     * Actualiza la notificación con las estadísticas actuales
     */
    fun updateNotification(statistics: DownloadStatistics) {
        val activeDownloads = statistics.activeDownloads
        val text = if (activeDownloads > 0) {
            val speedText = formatSpeed(statistics.currentGlobalSpeed)
            "$activeDownloads descarga${if (activeDownloads > 1) "s" else ""} activa${if (activeDownloads > 1) "s" else ""} • $speedText"
        } else {
            "Sin descargas activas"
        }

        val totalBytes = statistics.totalBytesDownloaded
        val downloadedBytes = statistics.totalBytesDownloaded

        val notification = buildNotification(
            text = text,
            activeDownloads = activeDownloads,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes
        )

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Formatea la velocidad de descarga en formato legible
     */
    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        }
    }

    /**
     * Cancela la notificación
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
