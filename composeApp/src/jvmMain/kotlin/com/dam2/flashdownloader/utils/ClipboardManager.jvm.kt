package com.dam2.flashdownloader.utils

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException

/**
 * Implementación de ClipboardManager para JVM usando AWT
 */
actual class ClipboardManager {
    private val clipboard = Toolkit.getDefaultToolkit().systemClipboard

    actual suspend fun getText(): String? {
        return try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                clipboard.getData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        } catch (e: UnsupportedFlavorException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun setText(text: String) {
        try {
            val stringSelection = java.awt.datatransfer.StringSelection(text)
            clipboard.setContents(stringSelection, null)
        } catch (e: Exception) {
            println("Error al establecer texto en portapapeles: ${e.message}")
        }
    }

    actual suspend fun hasUrl(): Boolean {
        val text = getText()
        return text?.let {
            it.contains("http://", ignoreCase = true) ||
                    it.contains("https://", ignoreCase = true)
        } ?: false
    }
}
