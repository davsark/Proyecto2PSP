package com.dam2.flashdownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.dam2.flashdownloader.service.DownloadService
import com.dam2.flashdownloader.ui.DownloadApp

class MainActivity : ComponentActivity() {

    // Launcher para solicitar permiso de notificaciones (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permiso concedido, iniciar servicio
            startDownloadService()
        } else {
            // Permiso denegado, aún así iniciar servicio (notificación no se mostrará)
            startDownloadService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Solicitar permiso de notificaciones si es necesario (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permiso ya concedido
                    startDownloadService()
                }
                else -> {
                    // Solicitar permiso
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android < 13, no requiere permiso de notificaciones
            startDownloadService()
        }

        setContent {
            DownloadApp()
        }
    }

    /**
     * Inicia el servicio de descargas en segundo plano
     */
    private fun startDownloadService() {
        val intent = Intent(this, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // El servicio continúa ejecutándose en segundo plano
        // No detenemos el servicio aquí para permitir descargas en background
    }
}
