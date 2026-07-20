// Copyright (c) 2026 TPollTech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPollTech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.

package com.tpoll.scanner.cleaner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

class CleanerScanner(private val context: Context) {

    suspend fun scan(): CleanerReport = withContext(Dispatchers.IO) {
        val items = queryFiles()
        val duplicateGroups = findDuplicateCandidates(items)
        val largeFiles = items
            .filter { it.sizeBytes >= LARGE_FILE_BYTES }
            .sortedByDescending { it.sizeBytes }
            .take(80)

        val whatsappItems = items.filter { it.isFromWhatsApp }
        val screenshots = items.filter { it.isScreenshot }
        val apkFiles = items.filter { it.category == CleanerCategory.APK }
        val oldDownloads = items.filter { it.isOldDownload }

        val duplicateRecoverable = duplicateGroups.sumOf { group ->
            group.items.drop(1).sumOf { it.sizeBytes }
        }
        val largeRecoverable = largeFiles.take(20).sumOf { it.sizeBytes }
        val oldDownloadRecoverable = oldDownloads.sumOf { it.sizeBytes }
        val apkRecoverable = apkFiles.sumOf { it.sizeBytes }

        CleanerReport(
            totalFiles = items.size,
            totalSizeBytes = items.sumOf { it.sizeBytes },
            imageCount = items.count { it.category == CleanerCategory.IMAGE },
            videoCount = items.count { it.category == CleanerCategory.VIDEO },
            audioCount = items.count { it.category == CleanerCategory.AUDIO },
            documentCount = items.count { it.category == CleanerCategory.DOCUMENT },
            apkCount = apkFiles.size,
            largeFiles = largeFiles,
            duplicateGroups = duplicateGroups,
            whatsappCount = whatsappItems.size,
            whatsappSizeBytes = whatsappItems.sumOf { it.sizeBytes },
            screenshotCount = screenshots.size,
            screenshotSizeBytes = screenshots.sumOf { it.sizeBytes },
            oldDownloadCount = oldDownloads.size,
            oldDownloadSizeBytes = oldDownloadRecoverable,
            recoverableBytesEstimate = duplicateRecoverable + oldDownloadRecoverable + apkRecoverable + largeRecoverable
        )
    }

    private fun queryFiles(): List<CleanerFileItem> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            add(MediaStore.Files.FileColumns.MIME_TYPE)
            add(MediaStore.Files.FileColumns.MEDIA_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"
        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"
        val result = mutableListOf<CleanerFileItem>()

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
            } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn).orEmpty()
                val size = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                val modified = cursor.getLong(modifiedColumn) * 1000L
                val mime = cursor.getString(mimeColumn).orEmpty()
                val mediaType = cursor.getInt(mediaTypeColumn)
                val relativePath = if (relativePathColumn >= 0) {
                    cursor.getString(relativePathColumn).orEmpty()
                } else ""

                val category = categoryFor(name, mime, mediaType)
                val uri = ContentUris.withAppendedId(collection, id)

                result.add(
                    CleanerFileItem(
                        id = id,
                        uri = uri,
                        name = name.ifBlank { "Arquivo sem nome" },
                        relativePath = relativePath,
                        mimeType = mime,
                        category = category,
                        sizeBytes = size,
                        modifiedAtMillis = modified
                    )
                )
            }
        }

        return result
    }

    private fun findDuplicateCandidates(items: List<CleanerFileItem>): List<DuplicateGroup> {
        return items
            .filter { it.sizeBytes > 0 && it.category != CleanerCategory.OTHER }
            .groupBy { item ->
                "${item.category}|${item.sizeBytes}|${normalizeName(item.name)}"
            }
            .values
            .filter { it.size >= 2 }
            .mapIndexed { index, group ->
                DuplicateGroup(
                    id = "dup_$index",
                    title = duplicateTitle(group.first()),
                    items = group.sortedByDescending { it.modifiedAtMillis },
                    recoverableBytes = group.drop(1).sumOf { it.sizeBytes }
                )
            }
            .sortedByDescending { it.recoverableBytes }
            .take(50)
    }

    private fun duplicateTitle(item: CleanerFileItem): String {
        return when (item.category) {
            CleanerCategory.IMAGE -> "Fotos duplicadas prováveis"
            CleanerCategory.VIDEO -> "Vídeos duplicados prováveis"
            CleanerCategory.AUDIO -> "Áudios duplicados prováveis"
            CleanerCategory.DOCUMENT -> "Documentos duplicados prováveis"
            CleanerCategory.APK -> "APKs duplicados prováveis"
            CleanerCategory.OTHER -> "Arquivos duplicados prováveis"
        }
    }

    private fun normalizeName(name: String): String {
        val lower = Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")

        return lower
            .replace("copy", "")
            .replace("copia", "")
            .replace("cópia", "")
            .replace("\\(\\d+\\)".toRegex(), "")
            .replace("[-_ ]+".toRegex(), "")
            .trim()
    }

    private fun categoryFor(name: String, mime: String, mediaType: Int): CleanerCategory {
        val lowerName = name.lowercase(Locale.ROOT)
        val lowerMime = mime.lowercase(Locale.ROOT)

        return when {
            mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE || lowerMime.startsWith("image/") -> CleanerCategory.IMAGE
            mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO || lowerMime.startsWith("video/") -> CleanerCategory.VIDEO
            mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO || lowerMime.startsWith("audio/") -> CleanerCategory.AUDIO
            lowerName.endsWith(".apk") -> CleanerCategory.APK
            lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") || lowerName.endsWith(".docx") ||
                lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".ppt") ||
                lowerName.endsWith(".pptx") || lowerName.endsWith(".txt") || lowerMime.contains("pdf") -> CleanerCategory.DOCUMENT
            else -> CleanerCategory.OTHER
        }
    }

    companion object {
        private const val LARGE_FILE_BYTES = 100L * 1024L * 1024L
    }
}

data class CleanerReport(
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val imageCount: Int,
    val videoCount: Int,
    val audioCount: Int,
    val documentCount: Int,
    val apkCount: Int,
    val largeFiles: List<CleanerFileItem>,
    val duplicateGroups: List<DuplicateGroup>,
    val whatsappCount: Int,
    val whatsappSizeBytes: Long,
    val screenshotCount: Int,
    val screenshotSizeBytes: Long,
    val oldDownloadCount: Int,
    val oldDownloadSizeBytes: Long,
    val recoverableBytesEstimate: Long
)

data class CleanerFileItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val relativePath: String,
    val mimeType: String,
    val category: CleanerCategory,
    val sizeBytes: Long,
    val modifiedAtMillis: Long
) {
    val isFromWhatsApp: Boolean
        get() {
            val text = "$relativePath/$name".lowercase(Locale.ROOT)
            return text.contains("whatsapp") || text.contains("media/com.whatsapp")
        }

    val isScreenshot: Boolean
        get() {
            val text = "$relativePath/$name".lowercase(Locale.ROOT)
            return text.contains("screenshot") || text.contains("screenshots") || text.contains("captura")
        }

    val isOldDownload: Boolean
        get() {
            val text = relativePath.lowercase(Locale.ROOT)
            val ninetyDaysAgo = System.currentTimeMillis() - 90L * 24L * 60L * 60L * 1000L
            return text.contains("download") && modifiedAtMillis in 1 until ninetyDaysAgo
        }
}

data class DuplicateGroup(
    val id: String,
    val title: String,
    val items: List<CleanerFileItem>,
    val recoverableBytes: Long
)

enum class CleanerCategory(val label: String) {
    IMAGE("Fotos"),
    VIDEO("Vídeos"),
    AUDIO("Áudios"),
    DOCUMENT("Documentos"),
    APK("APKs"),
    OTHER("Outros")
}

fun formatCleanerBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1 -> String.format(Locale.getDefault(), "%.1f GB", gb)
        mb >= 1 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1 -> String.format(Locale.getDefault(), "%.1f KB", kb)
        else -> "$bytes B"
    }
}
