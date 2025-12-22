package com.dam2.flashdownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dam2.flashdownloader.MainActivity
import com.dam2.flashdownloader.R
import com.dam2.flashdownloader.domain.manager.DownloadManager
import com.dam2.flashdownloader.domain.model.DownloadStatus
import com.dam2.flashdownloader.utils.formatBytes
import com.dam2.flashdownloader.utils.formatSpeed
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject

class DownloadService : Service() {

    private val downloadManager: DownloadManager by inject()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(0, 0, 0L))
        observeDownloads()
        
        // Acquire WakeLock to keep CPU running during downloads
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "FlashDownloader::DownloadService")
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)
    }

    private fun observeDownloads() {
        downloadManager.downloads
            .onEach { downloads ->
                val activeDownloads = downloads.filter { it.status is DownloadStatus.Downloading }
                
                if (activeDownloads.isEmpty() && downloads.none { it.status is DownloadStatus.Queued }) {
                    // Stop service if no active or queued downloads
                    stopSelf()
                } else {
                    // Update notification
                    val count = activeDownloads.size
                    val totalSpeed = activeDownloads.sumOf { it.currentSpeed }
                    val progress = if (activeDownloads.size == 1) {
                         // If only one, show specific progress
                         val d = activeDownloads.first()
                         if (d.totalSize > 0) ((d.downloadedBytes.toFloat() / d.totalSize) * 100).toInt() else 0
                    } else 0
                    
                    updateNotification(count, activeDownloads.size, totalSpeed, progress)
                    
                    // Keep WakeLock held
                    if (wakeLock?.isHeld == false) {
                        wakeLock?.acquire(10*60*1000L)
                    }
                }
            }
            .launchIn(scope)
    }

    private fun createNotification(activeCount: Int, queueCount: Int, totalSpeed: Long, progress: Int = 0): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (activeCount > 0) {
            "Descargando $activeCount archivo(s) a ${totalSpeed.formatSpeed()}"
        } else {
            "Descargas en cola o finalizando..."
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Flash Downloader")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            
        if (activeCount == 1 && progress > 0) {
            builder.setProgress(100, progress, false)
        } else if (activeCount > 0) {
             builder.setProgress(0, 0, true)
        } else {
             builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    private fun updateNotification(activeCount: Int, queueCount: Int, totalSpeed: Long, progress: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(activeCount, queueCount, totalSpeed, progress))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de descargas"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        wakeLock?.release()
    }
}
