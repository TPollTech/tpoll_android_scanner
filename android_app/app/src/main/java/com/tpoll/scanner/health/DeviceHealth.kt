package com.tpoll.scanner.health

enum class HealthStatus {
    GOOD, WARNING, CRITICAL, UNKNOWN
}

data class BatteryHealth(
    val level: Int,
    val temperature: Float,
    val isCharging: Boolean,
    val health: String
) {
    val status: HealthStatus
        get() = when {
            level <= 15 -> HealthStatus.CRITICAL
            level <= 30 || temperature > 45f -> HealthStatus.WARNING
            else -> HealthStatus.GOOD
        }
}

data class StorageHealth(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedPercent: Int
) {
    val status: HealthStatus
        get() = when {
            usedPercent >= 95 -> HealthStatus.CRITICAL
            usedPercent >= 80 -> HealthStatus.WARNING
            else -> HealthStatus.GOOD
        }
    val usedFormatted: String get() = formatBytes(totalBytes - freeBytes)
    val totalFormatted: String get() = formatBytes(totalBytes)
    val freeFormatted: String get() = formatBytes(freeBytes)
}

data class MemoryHealth(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedPercent: Int
) {
    val status: HealthStatus
        get() = when {
            usedPercent >= 90 -> HealthStatus.CRITICAL
            usedPercent >= 75 -> HealthStatus.WARNING
            else -> HealthStatus.GOOD
        }
    val usedFormatted: String get() = formatBytes(totalBytes - availableBytes)
    val totalFormatted: String get() = formatBytes(totalBytes)
    val availableFormatted: String get() = formatBytes(availableBytes)
}

data class SensorHealth(
    val name: String,
    val isPresent: Boolean,
    val isWorking: Boolean = true
)

data class NetworkHealth(
    val isWifiEnabled: Boolean,
    val isMobileDataEnabled: Boolean,
    val isConnected: Boolean
) {
    val status: HealthStatus
        get() = if (isConnected) HealthStatus.GOOD else HealthStatus.WARNING
}

data class BluetoothHealth(
    val isEnabled: Boolean
) {
    val status: HealthStatus get() = HealthStatus.GOOD
}

data class CpuHealth(
    val usagePercent: Int,
    val temperature: Float?
) {
    val status: HealthStatus
        get() = when {
            temperature != null && temperature > 60f -> HealthStatus.WARNING
            temperature != null && temperature > 75f -> HealthStatus.CRITICAL
            else -> HealthStatus.GOOD
        }
}

data class DeviceHealthReport(
    val battery: BatteryHealth,
    val internalStorage: StorageHealth,
    val externalStorage: StorageHealth?,
    val memory: MemoryHealth,
    val sensors: List<SensorHealth>,
    val network: NetworkHealth,
    val bluetooth: BluetoothHealth,
    val cpu: CpuHealth,
    val screenInfo: String,
    val uptimeDays: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    val overallStatus: HealthStatus
        get() {
            val statuses = listOf(
                battery.status,
                internalStorage.status,
                memory.status,
                network.status,
                cpu.status
            )
            return when {
                statuses.any { it == HealthStatus.CRITICAL } -> HealthStatus.CRITICAL
                statuses.any { it == HealthStatus.WARNING } -> HealthStatus.WARNING
                else -> HealthStatus.GOOD
            }
        }

    val healthyCount: Int get() = countByStatus(HealthStatus.GOOD)
    val warningCount: Int get() = countByStatus(HealthStatus.WARNING)
    val criticalCount: Int get() = countByStatus(HealthStatus.CRITICAL)

    private fun countByStatus(s: HealthStatus): Int {
        var count = 0
        if (battery.status == s) count++
        if (internalStorage.status == s) count++
        if (memory.status == s) count++
        if (network.status == s) count++
        if (cpu.status == s) count++
        if (externalStorage?.status == s) count++
        return count
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
