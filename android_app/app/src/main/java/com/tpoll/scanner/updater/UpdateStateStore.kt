package com.tpoll.scanner.updater

import android.content.Context

enum class UpdatePhase {
    IDLE,
    CHECKING,
    AVAILABLE,
    WAITING_FOR_WIFI,
    DOWNLOADING,
    READY_TO_INSTALL,
    PERMISSION_REQUIRED,
    INSTALLED,
    FAILED
}

data class PersistedUpdateState(
    val phase: UpdatePhase,
    val versionCode: Int,
    val versionName: String,
    val message: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val updatedAt: Long
)

object UpdateStateStore {

    private const val PREFS_NAME = "update_prefs"
    private const val KEY_PHASE = "update_phase"
    private const val KEY_VERSION_CODE = "update_version_code"
    private const val KEY_VERSION_NAME = "update_version_name"
    private const val KEY_MESSAGE = "update_message"
    private const val KEY_DOWNLOADED_BYTES = "update_downloaded_bytes"
    private const val KEY_TOTAL_BYTES = "update_total_bytes"
    private const val KEY_UPDATED_AT = "update_state_updated_at"

    fun read(context: Context): PersistedUpdateState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val phase = runCatching {
            UpdatePhase.valueOf(prefs.getString(KEY_PHASE, UpdatePhase.IDLE.name).orEmpty())
        }.getOrDefault(UpdatePhase.IDLE)
        return PersistedUpdateState(
            phase = phase,
            versionCode = prefs.getInt(KEY_VERSION_CODE, 0),
            versionName = prefs.getString(KEY_VERSION_NAME, "").orEmpty(),
            message = prefs.getString(KEY_MESSAGE, "").orEmpty(),
            downloadedBytes = prefs.getLong(KEY_DOWNLOADED_BYTES, 0L),
            totalBytes = prefs.getLong(KEY_TOTAL_BYTES, 0L),
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun write(
        context: Context,
        phase: UpdatePhase,
        versionCode: Int = 0,
        versionName: String = "",
        message: String = "",
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PHASE, phase.name)
            .putInt(KEY_VERSION_CODE, versionCode)
            .putString(KEY_VERSION_NAME, versionName)
            .putString(KEY_MESSAGE, message)
            .putLong(KEY_DOWNLOADED_BYTES, downloadedBytes)
            .putLong(KEY_TOTAL_BYTES, totalBytes)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun reconcileInstalledVersion(context: Context) {
        val state = read(context)
        if (state.versionCode <= 0 || state.phase !in setOf(
                UpdatePhase.AVAILABLE,
                UpdatePhase.WAITING_FOR_WIFI,
                UpdatePhase.DOWNLOADING,
                UpdatePhase.READY_TO_INSTALL,
                UpdatePhase.PERMISSION_REQUIRED
            )
        ) return

        val installed = InstalledAppVersion.read(context)
        if (installed.code >= state.versionCode) {
            ApkInstaller.clearCachedUpdates(context)
            write(
                context = context,
                phase = UpdatePhase.INSTALLED,
                versionCode = installed.code.toInt(),
                versionName = installed.name,
                message = "Atualização instalada com sucesso."
            )
        }
    }

    fun summary(context: Context): String? {
        val state = read(context)
        return when (state.phase) {
            UpdatePhase.IDLE -> null
            UpdatePhase.CHECKING -> "Verificando atualizações..."
            UpdatePhase.AVAILABLE -> "Versão ${state.versionName} disponível."
            UpdatePhase.WAITING_FOR_WIFI -> "Versão ${state.versionName} aguardando Wi-Fi."
            UpdatePhase.DOWNLOADING -> {
                val percent = if (state.totalBytes > 0L) {
                    ((state.downloadedBytes * 100L) / state.totalBytes).coerceIn(0L, 100L)
                } else {
                    0L
                }
                "Baixando a versão ${state.versionName}: $percent%."
            }
            UpdatePhase.READY_TO_INSTALL -> "Versão ${state.versionName} pronta para instalar."
            UpdatePhase.PERMISSION_REQUIRED -> "Permissão necessária para instalar ${state.versionName}."
            UpdatePhase.INSTALLED -> "Versão ${state.versionName} instalada."
            UpdatePhase.FAILED -> state.message.ifBlank { "Não foi possível concluir a atualização." }
        }
    }
}
