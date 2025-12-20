package com.dam2.flashdownloader.utils

import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context

/**
 * Implementación de ClipboardManager para Android
 */
actual class ClipboardManager(private val context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager

    actual suspend fun getText(): String? {
        return try {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun setText(text: String) {
        try {
            val clip = android.content.ClipData.newPlainText("text", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            android.util.Log.e("ClipboardManager", "Error al establecer texto", e)
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
