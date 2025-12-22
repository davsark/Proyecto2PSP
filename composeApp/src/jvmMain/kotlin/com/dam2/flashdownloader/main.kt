package com.dam2.flashdownloader

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dam2.flashdownloader.di.commonModule
import com.dam2.flashdownloader.di.platformModule
import com.dam2.flashdownloader.ui.DownloadApp
import org.koin.compose.KoinApplication
import org.jetbrains.compose.resources.painterResource
import flashdownloader.composeapp.generated.resources.Res
import flashdownloader.composeapp.generated.resources.logo

fun main() = application {
    val windowState = rememberWindowState(
        width = 1400.dp,
        height = 900.dp
    )

    KoinApplication(
        application = {
            modules(commonModule(), platformModule())
        }
    ) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Flash Downloader - Gestor de descargas multiplataforma",
            state = windowState,
            icon = painterResource(Res.drawable.logo)
        ) {
            DownloadApp()
        }
    }
}