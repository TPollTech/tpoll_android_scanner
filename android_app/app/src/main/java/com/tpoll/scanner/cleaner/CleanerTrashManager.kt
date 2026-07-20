// Copyright (c) 2026 TPollTech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPollTech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.

package com.tpoll.scanner.cleaner

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CleanerTrashManager(private val context: Context) {

    suspend fun prepareTrash(items: List<CleanerFileItem>): CleanerTrashResult = withContext(Dispatchers.IO) {
        val uniqueItems = items.distinctBy { it.uri }.filter { it.sizeBytes > 0 }
        if (uniqueItems.isEmpty()) {
            return@withContext CleanerTrashResult.Empty
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext try {
                val pendingIntent = MediaStore.createTrashRequest(
                    context.contentResolver,
                    uniqueItems.map { it.uri },
                    true
                )
                CleanerTrashResult.NeedsSystemConfirmation(
                    pendingIntent = pendingIntent,
                    itemCount = uniqueItems.size,
                    sizeBytes = uniqueItems.sumOf { it.sizeBytes }
                )
            } catch (e: Exception) {
                CleanerTrashResult.Failed(
                    message = "Não foi possível abrir a lixeira do Android: ${e.localizedMessage ?: e.javaClass.simpleName}"
                )
            }
        }

        var deleted = 0
        var failed = 0
        var deletedBytes = 0L
        uniqueItems.forEach { item ->
            try {
                val rows = context.contentResolver.delete(item.uri, null, null)
                if (rows > 0) {
                    deleted++
                    deletedBytes += item.sizeBytes
                } else {
                    failed++
                }
            } catch (_: Exception) {
                failed++
            }
        }

        CleanerTrashResult.DeletedImmediately(
            deletedCount = deleted,
            failedCount = failed,
            sizeBytes = deletedBytes
        )
    }
}

sealed class CleanerTrashResult {
    data object Empty : CleanerTrashResult()

    data class NeedsSystemConfirmation(
        val pendingIntent: PendingIntent,
        val itemCount: Int,
        val sizeBytes: Long
    ) : CleanerTrashResult()

    data class DeletedImmediately(
        val deletedCount: Int,
        val failedCount: Int,
        val sizeBytes: Long
    ) : CleanerTrashResult()

    data class Failed(
        val message: String
    ) : CleanerTrashResult()
}
