package com.dam2.flashdownloader

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Flash Downloader",
    ) {
        App()
    }
}