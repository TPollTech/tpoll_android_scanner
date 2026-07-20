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
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

class CleanerScanner(private val context: Context) {

    suspend fun scan(): CleanerReport = withContext(Dispatchers.IO) {
        val items = queryFiles()
        val exactDuplicateGroups = findExactDuplicateGroups(items)
        val duplicateGroups = findDuplicateCandidates(items, exactDuplicateGroups)
        val similarPhotoGroups = findSimilarPhotoCandidates(items)

        val largeFiles = items
            .filter { it.sizeBytes >= LARGE_FILE_BYTES }
            .sortedByDescending { it.sizeBytes }
            .take(100)

        val whatsappItems = items.filter { it.isFromWhatsApp }
        val screenshots = items.filter { it.isScreenshot }
        val apkFiles = items.filter { it.category == CleanerCategory.APK }
        val oldDownloads = items.filter { it.isOldDownload }

        val duplicateRecoverable = exactDuplicateGroups.sumOf { it.recoverableBytes } +
            duplicateGroups.sumOf { it.recoverableBytes } +
            similarPhotoGroups.sumOf { it.recoverableBytes }
        val largeRecoverable = largeFiles.take(20).sumOf { it.sizeBytes }
        val oldDownloadRecoverable = oldDownloads.sumOf { it.sizeBytes }
        val apkRecoverable = apkFiles.sumOf { it.sizeBytes }

        val whatsappBuckets = listOf(
            CleanerBucket("Fotos do WhatsApp", whatsappItems.filter { it.category == CleanerCategory.IMAGE }),
            CleanerBucket("Vídeos do WhatsApp", whatsappItems.filter { it.category == CleanerCategory.VIDEO }),
            CleanerBucket("Áudios do WhatsApp", whatsappItems.filter { it.category == CleanerCategory.AUDIO }),
            CleanerBucket("Documentos do WhatsApp", whatsappItems.filter { it.category == CleanerCategory.DOCUMENT }),
            CleanerBucket("APKs do WhatsApp", whatsappItems.filter { it.category == CleanerCategory.APK })
        ).filter { it.count > 0 }

        CleanerReport(
            totalFiles = items.size,
            totalSizeBytes = items.sumOf { it.sizeBytes },
            imageCount = items.count { it.category == CleanerCategory.IMAGE },
            videoCount = items.count { it.category == CleanerCategory.VIDEO },
            audioCount = items.count { it.category == CleanerCategory.AUDIO },
            documentCount = items.count { it.category == CleanerCategory.DOCUMENT },
            apkCount = apkFiles.size,
            largeFiles = largeFiles,
            exactDuplicateGroups = exactDuplicateGroups,
            duplicateGroups = duplicateGroups,
            similarPhotoGroups = similarPhotoGroups,
            whatsappBuckets = whatsappBuckets,
            whatsappCount = whatsappItems.size,
            whatsappSizeBytes = whatsappItems.sumOf { it.sizeBytes },
            screenshotItems = screenshots.sortedByDescending { it.modifiedAtMillis }.take(80),
            screenshotCount = screenshots.size,
            screenshotSizeBytes = screenshots.sumOf { it.sizeBytes },
            oldDownloadItems = oldDownloads.sortedBy { it.modifiedAtMillis }.take(80),
            oldDownloadCount = oldDownloads.size,
            oldDownloadSizeBytes = oldDownloadRecoverable,
            apkFiles = apkFiles.sortedByDescending { it.sizeBytes }.take(80),
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

    private fun findExactDuplicateGroups(items: List<CleanerFileItem>): List<DuplicateGroup> {
        val candidates = items
            .filter { it.sizeBytes > 0 && it.category != CleanerCategory.OTHER }
            .groupBy { "${it.category}|${it.sizeBytes}" }
            .values
            .filter { it.size >= 2 }
            .sortedByDescending { group -> group.first().sizeBytes * group.size }
            .flatten()
            .take(MAX_HASHED_FILES)

        val hashedItems = candidates.mapNotNull { item ->
            val hash = hashFile(item) ?: return@mapNotNull null
            HashedCleanerItem(item, hash)
        }

        return hashedItems
            .groupBy { "${it.item.category}|${it.item.sizeBytes}|${it.sha256}" }
            .values
            .filter { it.size >= 2 }
            .mapIndexed { index, group ->
                val sortedItems = group.map { it.item }.sortedByDescending { it.modifiedAtMillis }
                DuplicateGroup(
                    id = "exact_$index",
                    title = exactDuplicateTitle(sortedItems.first()),
                    items = sortedItems,
                    recoverableBytes = sortedItems.drop(1).sumOf { it.sizeBytes },
                    confidence = DuplicateConfidence.CONFIRMED_HASH,
                    recommendation = "Duplicado confirmado pelo conteúdo. Recomenda-se manter o arquivo mais recente e revisar as cópias."
                )
            }
            .sortedByDescending { it.recoverableBytes }
            .take(60)
    }

    private fun findDuplicateCandidates(
        items: List<CleanerFileItem>,
        exactGroups: List<DuplicateGroup>
    ): List<DuplicateGroup> {
        val exactIds = exactGroups.flatMap { it.items }.map { it.id }.toSet()

        return items
            .filter { it.sizeBytes > 0 && it.category != CleanerCategory.OTHER && it.id !in exactIds }
            .groupBy { item ->
                "${item.category}|${item.sizeBytes}|${normalizeName(item.name)}"
            }
            .values
            .filter { it.size >= 2 }
            .mapIndexed { index, group ->
                val sortedItems = group.sortedByDescending { it.modifiedAtMillis }
                DuplicateGroup(
                    id = "dup_$index",
                    title = duplicateTitle(sortedItems.first()),
                    items = sortedItems,
                    recoverableBytes = sortedItems.drop(1).sumOf { it.sizeBytes },
                    confidence = DuplicateConfidence.PROBABLE_METADATA,
                    recommendation = "Mesmo nome/tamanho. Revise antes de apagar, principalmente documentos importantes."
                )
            }
            .sortedByDescending { it.recoverableBytes }
            .take(50)
    }

    private fun findSimilarPhotoCandidates(items: List<CleanerFileItem>): List<DuplicateGroup> {
        return items
            .filter { it.category == CleanerCategory.IMAGE && it.sizeBytes > 0 }
            .groupBy { item ->
                similarPhotoKey(item.name)
            }
            .values
            .filter { group ->
                group.size >= 3 && group.map { it.sizeBytes / (128 * 1024L) }.distinct().size <= 3
            }
            .mapIndexed { index, group ->
                val sortedItems = group.sortedWith(
                    compareByDescending<CleanerFileItem> { it.sizeBytes }
                        .thenByDescending { it.modifiedAtMillis }
                )
                DuplicateGroup(
                    id = "similar_photo_$index",
                    title = "Fotos parecidas para revisar",
                    items = sortedItems,
                    recoverableBytes = sortedItems.drop(1).sumOf { it.sizeBytes },
                    confidence = DuplicateConfidence.SIMILAR_PHOTO_NAME,
                    recommendation = "Possíveis fotos parecidas por sequência/nome. Mantenha a melhor foto antes de limpar."
                )
            }
            .filter { it.recoverableBytes > 0 }
            .sortedByDescending { it.recoverableBytes }
            .take(40)
    }

    private fun hashFile(item: CleanerFileItem): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: return null
            digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
        } catch (_: Exception) {
            null
        }
    }

    private fun exactDuplicateTitle(item: CleanerFileItem): String {
        return when (item.category) {
            CleanerCategory.IMAGE -> "Fotos duplicadas confirmadas"
            CleanerCategory.VIDEO -> "Vídeos duplicados confirmados"
            CleanerCategory.AUDIO -> "Áudios duplicados confirmados"
            CleanerCategory.DOCUMENT -> "Documentos duplicados confirmados"
            CleanerCategory.APK -> "APKs duplicados confirmados"
            CleanerCategory.OTHER -> "Arquivos duplicados confirmados"
        }
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
            .substringBeforeLast('.', lower)
            .replace("copy", "")
            .replace("copia", "")
            .replace("cópia", "")
            .replace("duplicado", "")
            .replace("\\(\\d+\\)".toRegex(), "")
            .replace("[-_ ]+".toRegex(), "")
            .trim()
    }

    private fun similarPhotoKey(name: String): String {
        return normalizeName(name)
            .replace("img", "")
            .replace("image", "")
            .replace("photo", "")
            .replace("foto", "")
            .replace("wa", "")
            .replace("\\d{4,}".toRegex(), "")
            .take(24)
            .ifBlank { normalizeName(name).take(16) }
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
        private const val MAX_HASHED_FILES = 240
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}

private data class HashedCleanerItem(
    val item: CleanerFileItem,
    val sha256: String
)

data class CleanerReport(
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val imageCount: Int,
    val videoCount: Int,
    val audioCount: Int,
    val documentCount: Int,
    val apkCount: Int,
    val largeFiles: List<CleanerFileItem>,
    val exactDuplicateGroups: List<DuplicateGroup>,
    val duplicateGroups: List<DuplicateGroup>,
    val similarPhotoGroups: List<DuplicateGroup>,
    val whatsappBuckets: List<CleanerBucket>,
    val whatsappCount: Int,
    val whatsappSizeBytes: Long,
    val screenshotItems: List<CleanerFileItem>,
    val screenshotCount: Int,
    val screenshotSizeBytes: Long,
    val oldDownloadItems: List<CleanerFileItem>,
    val oldDownloadCount: Int,
    val oldDownloadSizeBytes: Long,
    val apkFiles: List<CleanerFileItem>,
    val recoverableBytesEstimate: Long
) {
    val allDuplicateGroups: List<DuplicateGroup>
        get() = exactDuplicateGroups + duplicateGroups + similarPhotoGroups
}

data class CleanerBucket(
    val title: String,
    val items: List<CleanerFileItem>
) {
    val count: Int get() = items.size
    val sizeBytes: Long get() = items.sumOf { it.sizeBytes }
}

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
    val recoverableBytes: Long,
    val confidence: DuplicateConfidence,
    val recommendation: String
)

enum class DuplicateConfidence(val label: String) {
    CONFIRMED_HASH("Confirmado"),
    PROBABLE_METADATA("Provável"),
    SIMILAR_PHOTO_NAME("Parecidas")
}

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
