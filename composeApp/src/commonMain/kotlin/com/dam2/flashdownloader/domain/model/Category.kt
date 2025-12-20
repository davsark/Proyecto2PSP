package com.dam2.flashdownloader.domain.model

/**
 * Categorías de descarga para organización visual y lógica
 */
enum class Category(val displayName: String, val iconName: String) {
    DOCUMENTS("Documentos", "📄"),
    MULTIMEDIA("Multimedia", "🎬"),
    SOFTWARE("Software", "💿"),
    COMPRESSED("Comprimidos", "📦"),
    OTHERS("Otros", "📁");

    companion object {
        fun fromFileName(fileName: String): Category {
            return when {
                fileName.matches(Regex(".*\\.(pdf|doc|docx|txt|xls|xlsx|ppt|pptx)$", RegexOption.IGNORE_CASE)) -> DOCUMENTS
                fileName.matches(Regex(".*\\.(mp4|avi|mkv|mp3|wav|flac|jpg|jpeg|png|gif)$", RegexOption.IGNORE_CASE)) -> MULTIMEDIA
                fileName.matches(Regex(".*\\.(exe|dmg|apk|deb|rpm|msi|app)$", RegexOption.IGNORE_CASE)) -> SOFTWARE
                fileName.matches(Regex(".*\\.(zip|rar|7z|tar|gz|bz2)$", RegexOption.IGNORE_CASE)) -> COMPRESSED
                else -> OTHERS
            }
        }
    }
}
