package com.dam2.flashdownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dam2.flashdownloader.ui.DownloadApp

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.core.app.ActivityCompat
import com.dam2.flashdownloader.domain.manager.DownloadManager
import com.dam2.flashdownloader.domain.model.DownloadStatus
import com.dam2.flashdownloader.service.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val downloadManager: DownloadManager by inject()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Request permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        setContent {
            DownloadApp()
        }
        
        // Observe downloads to start service
        downloadManager.downloads
            .onEach { downloads ->
                val hasActive = downloads.any { 
                    it.status is DownloadStatus.Downloading || it.status is DownloadStatus.Queued 
                }
                
                if (hasActive) {
                    val intent = Intent(this, DownloadService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            }
            .launchIn(CoroutineScope(Dispatchers.Main))
    }
}