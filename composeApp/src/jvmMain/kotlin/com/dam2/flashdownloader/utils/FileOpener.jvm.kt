package com.dam2.flashdownloader.utils

import java.awt.Desktop
import java.io.File

/**
 * Implementación JVM para abrir archivos y carpetas en el explorador del sistema
 */
actual object FileOpener {
    /**
     * Abre el explorador de archivos en la ubicación del archivo
     * @param filePath Ruta completa del archivo
     * @return true si se abrió correctamente, false si hubo error
     */
    actual fun openFileLocation(filePath: String): Boolean {
        return try {
            if (!Desktop.isDesktopSupported()) {
                return false
            }

            val file = File(filePath)
            if (!file.exists()) {
                return false
            }

            val desktop = Desktop.getDesktop()

            // Intentar abrir la carpeta padre y seleccionar el archivo
            // En algunos sistemas, browse() abre la carpeta directamente
            val parentDir = file.parentFile
            if (parentDir != null && parentDir.exists()) {
                desktop.open(parentDir)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("Error al abrir ubicación del archivo: ${e.message}")
            false
        }
    }

    /**
     * Abre el archivo directamente con la aplicación predeterminada del sistema
     * @param filePath Ruta completa del archivo
     * @return true si se abrió correctamente, false si hubo error
     */
    actual fun openFile(filePath: String): Boolean {
        return try {
            if (!Desktop.isDesktopSupported()) {
                return false
            }

            val file = File(filePath)
            if (!file.exists()) {
                return false
            }

            val desktop = Desktop.getDesktop()
            desktop.open(file)
            true
        } catch (e: Exception) {
            println("Error al abrir archivo: ${e.message}")
            false
        }
    }
}
