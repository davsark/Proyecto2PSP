package com.dam2.flashdownloader.utils

/**
 * Interfaz para abrir archivos y carpetas en el explorador del sistema
 */
expect object FileOpener {
    /**
     * Abre el explorador de archivos en la ubicación del archivo
     * @param filePath Ruta completa del archivo
     * @return true si se abrió correctamente, false si hubo error
     */
    fun openFileLocation(filePath: String): Boolean

    /**
     * Abre el archivo directamente con la aplicación predeterminada del sistema
     * @param filePath Ruta completa del archivo
     * @return true si se abrió correctamente, false si hubo error
     */
    fun openFile(filePath: String): Boolean
}
