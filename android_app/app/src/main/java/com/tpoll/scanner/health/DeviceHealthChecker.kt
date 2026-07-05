package com.tpoll.scanner.health

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.UserManager
import android.provider.Settings
import java.io.File

class DeviceHealthChecker(private val context: Context) {

    fun checkAll(): DeviceHealthReport {
        return DeviceHealthReport(
            battery = checkBattery(),
            internalStorage = checkInternalStorage(),
            externalStorage = checkExternalStorage(),
            memory = checkMemory(),
            sensors = checkSensors(),
            network = checkNetwork(),
            bluetooth = checkBluetooth(),
            cpu = checkCpu(),
            screenInfo = getScreenInfo(),
            uptimeDays = ((System.currentTimeMillis() - readBootTime()) / (1000 * 60 * 60 * 24)).toInt()
        )
    }

    private fun checkBattery(): BatteryHealth {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val isCharging = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
                || intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL
        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Boa"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Superaquecida"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Morta"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensão"
            BatteryManager.BATTERY_HEALTH_COLD -> "Fria"
            else -> "Desconhecida"
        }
        return BatteryHealth(
            level = level * 100 / scale,
            temperature = temp,
            isCharging = isCharging,
            health = health
        )
    }

    private fun checkInternalStorage(): StorageHealth {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = if (total > 0) ((total - free) * 100 / total).toInt() else 0
        return StorageHealth(totalBytes = total, freeBytes = free, usedPercent = used)
    }

    private fun checkExternalStorage(): StorageHealth? {
        return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = if (total > 0) ((total - free) * 100 / total).toInt() else 0
            StorageHealth(totalBytes = total, freeBytes = free, usedPercent = used)
        } else null
    }

    private fun checkMemory(): MemoryHealth {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val total = info.totalMem
        val available = info.availMem
        val used = if (total > 0) ((total - available) * 100 / total).toInt() else 0
        return MemoryHealth(totalBytes = total, availableBytes = available, usedPercent = used)
    }

    private fun checkSensors(): List<SensorHealth> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorTypes = listOf(
            Sensor.TYPE_ACCELEROMETER to "Acelerômetro",
            Sensor.TYPE_GYROSCOPE to "Giroscópio",
            Sensor.TYPE_MAGNETIC_FIELD to "Magnetômetro",
            Sensor.TYPE_LIGHT to "Luz ambiente",
            Sensor.TYPE_PROXIMITY to "Proximidade",
            Sensor.TYPE_PRESSURE to "Pressão",
            Sensor.TYPE_AMBIENT_TEMPERATURE to "Temperatura",
            Sensor.TYPE_RELATIVE_HUMIDITY to "Umidade",
            Sensor.TYPE_STEP_COUNTER to "Passos",
            Sensor.TYPE_HEART_RATE to "Frequência cardíaca",
            Sensor.TYPE_GRAVITY to "Gravidade",
            Sensor.TYPE_LINEAR_ACCELERATION to "Aceleração linear"
        )
        val availableSensors = sm.getSensorList(Sensor.TYPE_ALL).map { it.type }.toSet()
        return sensorTypes.map { (type, name) ->
            SensorHealth(name = name, isPresent = type in availableSensors)
        }
    }

    private fun checkNetwork(): NetworkHealth {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        return NetworkHealth(isWifiEnabled = isWifi, isMobileDataEnabled = isMobile, isConnected = isConnected)
    }

    private fun checkBluetooth(): BluetoothHealth {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return BluetoothHealth(isEnabled = adapter != null && adapter.isEnabled)
    }

    private fun checkCpu(): CpuHealth {
        val usage = readCpuUsage()
        val temp = readCpuTemperature()
        return CpuHealth(usagePercent = usage, temperature = temp)
    }

    private fun getScreenInfo(): String {
        val display = context.resources.displayMetrics
        return "${display.widthPixels}x${display.heightPixels} (${display.densityDpi}dpi)"
    }

    private fun readBootTime(): Long {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val bootTime = method.invoke(null, "ro.runtime.firstboot") as? String
            if (bootTime != null) bootTime.toLongOrNull() ?: System.currentTimeMillis()
            else System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun readCpuUsage(): Int {
        return try {
            val reader = File("/proc/stat").bufferedReader()
            val line = reader.readLine() ?: return 0
            reader.close()
            val parts = line.split("\\s+".toRegex())
            if (parts.size < 5) return 0
            val idle = parts[4].toLongOrNull() ?: return 0
            val total = parts.drop(1).mapNotNull { it.toLongOrNull() }.sum()
            if (total == 0L) return 0
            ((total - idle) * 100 / total).toInt()
        } catch (_: Exception) { 0 }
    }

    private fun readCpuTemperature(): Float? {
        val paths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/class/thermal/thermal_zone3/temp",
            "/sys/class/thermal/thermal_zone4/temp",
            "/sys/class/thermal/thermal_zone5/temp",
            "/sys/class/thermal/thermal_zone6/temp",
            "/sys/class/thermal/thermal_zone7/temp"
        )
        for (path in paths) {
            try {
                val text = File(path).readText().trim()
                val value = text.toIntOrNull()
                if (value != null) return value / 1000f
            } catch (_: Exception) { }
        }
        return null
    }
}
