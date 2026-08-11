// Copyright (c) 2026 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.webguard

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.tpoll.scanner.R
import com.tpoll.scanner.notifications.NotificationHelper
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class WebBlockerVPNService : VpnService() {

    companion object {
        private const val VPN_ADDRESS = "10.0.0.1"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_MASK = "0"
        private const val VPN_DNS1 = "8.8.8.8"
        private const val VPN_DNS2 = "8.8.4.4"
        private const val NOTIFICATION_ID = 3010
        private const val MTU = 1500
        private const val DNS_PORT = 53
        private const val ACTION_STOP = "com.tpoll.scanner.webguard.STOP_VPN"

        private var isRunning = false
        private var blockedCount = 0

        fun isRunning(): Boolean = isRunning
        fun getBlockedCount(): Int = blockedCount

        fun start(context: Context) {
            val intent = Intent(context, WebBlockerVPNService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WebBlockerVPNService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnJob: Job? = null
    private var blocklistDb: URLBlocklistDatabase? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        blocklistDb = URLBlocklistDatabase.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            try {
                startVpn()
            } catch (e: Exception) {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        val builder = Builder()
        builder.setSession("TPoll WebGuard")
        builder.addAddress(VPN_ADDRESS, 32)
        builder.addRoute(VPN_ROUTE, VPN_MASK.toInt())
        builder.addDnsServer(VPN_DNS1)
        builder.addDnsServer(VPN_DNS2)
        builder.setMtu(MTU)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            stopSelf()
            return
        }

        isRunning = true
        blockedCount = 0

        val notification = buildNotification("Proteção web ativa")
        startForeground(NOTIFICATION_ID, notification)

        acquireWakeLock()
        startPacketLoop()
    }

    private fun stopVpn() {
        isRunning = false
        vpnJob?.cancel()
        scope.cancel()

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        wakeLock?.let { if (it.isHeld) it.release() }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startPacketLoop() {
        vpnJob?.cancel()
        vpnJob = scope.launch {
            while (isActive) {
                try {
                    val vpnFd = vpnInterface ?: break
                    val inputStream = FileInputStream(vpnFd.fileDescriptor)
                    val outputStream = FileOutputStream(vpnFd.fileDescriptor)
                    val buffer = ByteArray(MTU)

                    while (isActive) {
                        val length = inputStream.read(buffer)
                        if (length <= 0) {
                            delay(10)
                            continue
                        }

                        val packet = buffer.copyOf(length)
                        val handled = handlePacket(packet, outputStream)
                        if (!handled) {
                            forwardPacket(packet, outputStream)
                        }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (_: Exception) {
                    delay(100)
                }
            }
        }
    }

    private fun handlePacket(packet: ByteArray, outputStream: FileOutputStream): Boolean {
        if (packet.size < 12) return false

        try {
            val ipVersion = (packet[0].toInt() and 0xF0) shr 4
            if (ipVersion != 4) return false

            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
            val protocol = packet[9].toInt() and 0xFF

            if (protocol != 17) return false

            val udpHeaderOffset = ipHeaderLength
            if (packet.size < udpHeaderOffset + 8) return false

            val srcPort = ((packet[udpHeaderOffset].toInt() and 0xFF) shl 8) or
                (packet[udpHeaderOffset + 1].toInt() and 0xFF)
            val dstPort = ((packet[udpHeaderOffset + 2].toInt() and 0xFF) shl 8) or
                (packet[udpHeaderOffset + 3].toInt() and 0xFF)

            if (dstPort != DNS_PORT) return false

            val dnsOffset = udpHeaderOffset + 8
            if (packet.size < dnsOffset + 12) return false

            val dnsQuery = extractDnsQuery(packet, dnsOffset) ?: return false

            val blockResult = blocklistDb?.checkDomain(dnsQuery)
            if (blockResult != null) {
                blockedCount++
                val fakeResponse = createDnsBlockedResponse(packet, ipHeaderLength, udpHeaderOffset, dnsOffset)
                outputStream.write(fakeResponse)
                outputStream.flush()

                showBlockNotification(blockResult)
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun extractDnsQuery(packet: ByteArray, dnsOffset: Int): String? {
        try {
            val nameBuilder = StringBuilder()
            var offset = dnsOffset + 12

            while (offset < packet.size) {
                val labelLen = packet[offset].toInt() and 0xFF
                if (labelLen == 0) break
                if (offset + 1 + labelLen > packet.size) return null

                if (nameBuilder.isNotEmpty()) nameBuilder.append(".")
                nameBuilder.append(String(packet, offset + 1, labelLen))
                offset += 1 + labelLen
            }

            return nameBuilder.toString().lowercase()
        } catch (_: Exception) {
            return null
        }
    }

    private fun createDnsBlockedResponse(
        originalPacket: ByteArray,
        ipHeaderLen: Int,
        udpOffset: Int,
        dnsOffset: Int
    ): ByteArray {
        val response = originalPacket.copyOf()

        response[0] = ((response[0].toInt() and 0xF0) or 0x45).toByte()

        val srcIpOffset = 12
        val dstIpOffset = 16
        val srcTemp = response.copyOfRange(srcIpOffset, srcIpOffset + 4)
        System.arraycopy(response, dstIpOffset, response, srcIpOffset, 4)
        System.arraycopy(srcTemp, 0, response, dstIpOffset, 4)

        val ttlOffset = 8
        response[ttlOffset] = 64.toByte()

        response[dnsOffset + 2] = 0x81.toByte()
        response[dnsOffset + 3] = 0x80.toByte()

        val answerCountOffset = dnsOffset + 6
        response[answerCountOffset] = 0x00
        response[answerCountOffset + 1] = 0x01

        val answerOffset = dnsOffset + 12
        while (answerOffset < response.size && response[answerOffset].toInt() != 0) {
            val labelLen = response[answerOffset].toInt() and 0xFF
            if (labelLen == 0) break
            answerOffset += 1 + labelLen
        }
        val answerStart = answerOffset + 1

        if (answerStart + 16 <= response.size) {
            response[answerStart] = 0x00.toByte()
            response[answerStart + 1] = 0x01.toByte()
            response[answerStart + 2] = 0x00.toByte()
            response[answerStart + 3] = 0x01.toByte()
            response[answerStart + 4] = 0x00.toByte()
            response[answerStart + 5] = 0x00.toByte()
            response[answerStart + 6] = 0x00.toByte()
            response[answerStart + 7] = 0x3C.toByte()
            response[answerStart + 8] = 0x00.toByte()
            response[answerStart + 9] = 0x04.toByte()
            response[answerStart + 10] = 0x00.toByte()
            response[answerStart + 11] = 0x00.toByte()
            response[answerStart + 12] = 0x00.toByte()
            response[answerStart + 13] = 0x00.toByte()
            response[answerStart + 14] = 0x00.toByte()
            response[answerStart + 15] = 0x00.toByte()

            val newLen = answerStart + 16 - ipHeaderLen
            response[ipHeaderLen + 2] = ((newLen shr 8) and 0xFF).toByte()
            response[ipHeaderLen + 3] = (newLen and 0xFF).toByte()

            val checksumOffset = ipHeaderLen + 10
            response[checksumOffset] = 0x00
            response[checksumOffset + 1] = 0x00
            var sum = 0L
            for (i in ipHeaderLen until ipHeaderLen + ipHeaderLen step 2) {
                sum += ((response[i].toInt() and 0xFF) shl 8) or (response[i + 1].toInt() and 0xFF)
            }
            while (sum shr 16 != 0L) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            val checksum = sum.toInt().inv() and 0xFFFF
            response[checksumOffset] = ((checksum shr 8) and 0xFF).toByte()
            response[checksumOffset + 1] = (checksum and 0xFF).toByte()
        }

        return response
    }

    private fun forwardPacket(packet: ByteArray, outputStream: FileOutputStream) {
        try {
            val socket = DatagramSocket()
            socket.connect(java.net.InetSocketAddress(VPN_DNS1, DNS_PORT))
            socket.send(DatagramPacket(packet, packet.size))

            val responseBuffer = ByteArray(MTU)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.soTimeout = 3000
            try {
                socket.receive(responsePacket)
                outputStream.write(responseBuffer, 0, responsePacket.length)
                outputStream.flush()
            } catch (_: Exception) {}
            socket.close()
        } catch (_: Exception) {}
    }

    private fun showBlockNotification(blockResult: BlockResult) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val app = application as com.tpoll.scanner.TPollApp
            app.notificationHelper.showWebProtectionAlert(
                domain = blockResult.domain,
                category = blockResult.categoryDescription,
                severity = blockResult.severity
            )
        } catch (_: Exception) {}
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, com.tpoll.scanner.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_WEBPROTECTION)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("TPoll WebGuard")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "tpoll::webguard_vpn_lock"
            ).apply {
                acquire(30 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
