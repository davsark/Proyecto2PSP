package com.dam2.flashdownloader.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.dam2.flashdownloader.domain.manager.DownloadManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.android.ext.android.inject

/**
 * Servicio foreground para mantener las descargas activas en segundo plano
 * 
 * Características:
 * - Mantiene el proceso vivo cuando la app está en segundo plano
 * - Muestra notificación persistente con progreso de descargas
 * - Se auto-detiene cuando no hay descargas activas
 * - Maneja acciones desde la notificación (pausar/cancelar)
 */
class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"
    }

    // Inyección de dependencias via Koin
    private val manager: DownloadManager by inject()
    
    // Helper para notificaciones
    private lateinit var notificationHelper: NotificationHelper
    
    // Scope del servicio para corrutinas
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Job para observar estadísticas
    private var statisticsJob: Job? = null
    
    // Binder para comunicación con la Activity
    private val binder = DownloadServiceBinder()

    inner class DownloadServiceBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Inicializar helper de notificaciones
        notificationHelper = NotificationHelper(this)
        
        // Iniciar como foreground service inmediatamente (requerido en Android O+)
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            notificationHelper.buildForegroundNotification()
        )
        
        // Cargar descargas guardadas
        serviceScope.launch {
            manager.loadSavedDownloads()
        }
        
        // Observar estadísticas para actualizar notificación
        startObservingStatistics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        // Manejar acciones desde la notificación
        when (intent?.action) {
            NotificationHelper.ACTION_PAUSE_ALL -> {
                serviceScope.launch {
                    manager.pauseAllDownloads()
                }
            }
            NotificationHelper.ACTION_CANCEL_ALL -> {
                serviceScope.launch {
                    manager.cancelAll()
                }
            }
        }
        
        // START_STICKY: el servicio se reinicia si es matado por el sistema
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        // Cancelar observación de estadísticas
        statisticsJob?.cancel()
        
        // Detener el DownloadManager
        serviceScope.launch {
            manager.shutdown()
        }
        
        // Cancelar todas las corrutinas del servicio
        serviceScope.cancel()
        
        // Cancelar notificación
        notificationHelper.cancelNotification()
    }

    /**
     * Observa las estadísticas del DownloadManager y actualiza la notificación
     */
    private fun startObservingStatistics() {
        statisticsJob = serviceScope.launch {
            manager.statistics.collectLatest { statistics ->
                // Actualizar notificación con estadísticas actuales
                notificationHelper.updateNotification(statistics)
                
                // Auto-detener el servicio si no hay descargas activas
                // (comentado por ahora para mantener el servicio vivo)
                /*
                if (statistics.activeDownloads == 0 && 
                    statistics.queuedDownloads == 0 && 
                    statistics.pausedDownloads == 0) {
                    Log.d(TAG, "No active downloads, stopping service")
                    stopSelf()
                }
                */
            }
        }
    }

    /**
     * Obtiene el DownloadManager (para uso desde la Activity si es necesario)
     */
    fun getDownloadManager(): DownloadManager = manager
}
