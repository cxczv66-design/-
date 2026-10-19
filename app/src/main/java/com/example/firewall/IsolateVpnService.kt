package com.example.firewall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.model.FirewallLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Real Android VpnService acting as an on-device local firewall.
 * Intercepts, routes, and safely drops network packets from sandboxed applications
 * without leaking data to external servers or the internet.
 */
class IsolateVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var workerJob: Job? = null

    companion object {
        const val ACTION_START = "com.example.firewall.START"
        const val ACTION_STOP = "com.example.firewall.STOP"
        const val ACTION_RELOAD = "com.example.firewall.RELOAD"
        const val NOTIFICATION_ID = 4040
        const val CHANNEL_ID = "isolatebox_firewall_channel"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _totalBlocked = MutableStateFlow(0L)
        val totalBlocked = _totalBlocked.asStateFlow()

        private val _activeBlockedPackages = MutableStateFlow<Set<String>>(emptySet())
        val activeBlockedPackages = _activeBlockedPackages.asStateFlow()

        private val _recentLogs = MutableSharedFlow<FirewallLogEntry>(extraBufferCapacity = 50)
        val recentLogs = _recentLogs.asSharedFlow()

        fun isPrepared(context: Context): Boolean {
            return prepare(context) == null
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START, ACTION_RELOAD -> {
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
                serviceScope.launch {
                    reloadAndEstablishVpn()
                }
            }
            ACTION_STOP -> {
                stopVpn()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private suspend fun reloadAndEstablishVpn() {
        try {
            stopVpnWorker()

            val db = AppDatabase.getInstance(applicationContext)
            val sandboxedApps = db.sandboxedAppDao().getActiveSandboxedApps()
            val packagesToBlock = sandboxedApps
                .filter { it.blockAllNetwork || it.blockIpv4 || it.blockIpv6 || it.blockDns }
                .map { it.packageName }
                .toSet()

            _activeBlockedPackages.value = packagesToBlock

            if (packagesToBlock.isEmpty()) {
                Log.i("IsolateVpn", "No apps configured for network isolation. Idle tunnel.")
            }

            val builder = Builder()
                .setSession("IsolateBox Local Firewall")
                .setMtu(1500)
                // Assign local isolated virtual addresses
                .addAddress("10.120.0.1", 32)
                .addRoute("0.0.0.0", 0) // Route all IPv4
                .addAddress("fd00:1::1", 128)
                .addRoute("::", 0)       // Route all IPv6
                .addDnsServer("10.120.0.1")

            // Attach per-app routing rules
            var addedAppCount = 0
            for (pkg in packagesToBlock) {
                try {
                    builder.addAllowedApplication(pkg)
                    addedAppCount++
                } catch (e: Exception) {
                    Log.w("IsolateVpn", "App not installed or cannot be added: $pkg: ${e.message}")
                }
            }

            // Also exclude IsolateBox itself so the app can perform local checks
            try {
                builder.addDisallowedApplication(packageName)
            } catch (ignored: Exception) {}

            vpnInterface?.close()
            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                _isRunning.value = true
                startPacketDropperWorker(packagesToBlock)
                Log.i("IsolateVpn", "VPN Interface established. Isolated $addedAppCount apps.")
            } else {
                Log.e("IsolateVpn", "Failed to establish VPN interface (null).")
                _isRunning.value = false
            }
        } catch (e: Exception) {
            Log.e("IsolateVpn", "Error starting VPN: ${e.message}", e)
            _isRunning.value = false
        }
    }

    private fun startPacketDropperWorker(blockedPackages: Set<String>) {
        val pfd = vpnInterface ?: return
        val db = AppDatabase.getInstance(applicationContext)

        workerJob = serviceScope.launch(Dispatchers.IO) {
            val inStream = FileInputStream(pfd.fileDescriptor)
            val packet = ByteBuffer.allocate(32768)

            while (isActive && _isRunning.value) {
                try {
                    packet.clear()
                    val length = inStream.read(packet.array())
                    if (length > 0) {
                        packet.limit(length)
                        val logEntry = parseAndDropPacket(packet, length, blockedPackages)
                        if (logEntry != null) {
                            _totalBlocked.value += 1
                            _recentLogs.tryEmit(logEntry)
                            // Persist to Room in batches or asynchronously
                            serviceScope.launch {
                                try {
                                    db.firewallLogDao().insertLog(logEntry)
                                    db.sandboxedAppDao().incrementBlockedPackets(logEntry.packageName, 1)
                                } catch (e: Exception) {
                                    // Ignore insertion failure during tight loops
                                }
                            }
                        }
                    } else if (length < 0) {
                        break
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e("IsolateVpn", "Packet read loop interrupted: ${e.message}")
                    }
                    break
                }
            }
        }
    }

    private fun parseAndDropPacket(buffer: ByteBuffer, length: Int, blockedPackages: Set<String>): FirewallLogEntry? {
        if (length < 20) return null
        val bytes = buffer.array()

        val versionAndIHL = bytes[0].toInt() and 0xFF
        val version = versionAndIHL ushr 4
        var protocolStr = "OTHER"
        var srcIp = "0.0.0.0"
        var dstIp = "0.0.0.0"
        var dstPort = 0
        var isDns = false

        if (version == 4) {
            val protocol = bytes[9].toInt() and 0xFF
            srcIp = "${bytes[12].toInt() and 0xFF}.${bytes[13].toInt() and 0xFF}.${bytes[14].toInt() and 0xFF}.${bytes[15].toInt() and 0xFF}"
            dstIp = "${bytes[16].toInt() and 0xFF}.${bytes[17].toInt() and 0xFF}.${bytes[18].toInt() and 0xFF}.${bytes[19].toInt() and 0xFF}"

            val ihl = (versionAndIHL and 0x0F) * 4
            if (length >= ihl + 4) {
                val destPortOffset = ihl + 2
                dstPort = ((bytes[destPortOffset].toInt() and 0xFF) shl 8) or (bytes[destPortOffset + 1].toInt() and 0xFF)
            }

            protocolStr = when (protocol) {
                6 -> "TCP"
                17 -> {
                    if (dstPort == 53) {
                        isDns = true
                        "DNS (UDP)"
                    } else "UDP"
                }
                1 -> "ICMP"
                else -> "IPv4 ($protocol)"
            }
        } else if (version == 6) {
            protocolStr = "IPv6"
            if (length >= 40) {
                val nextHeader = bytes[6].toInt() and 0xFF
                if (nextHeader == 17 && length >= 44) {
                    dstPort = ((bytes[42].toInt() and 0xFF) shl 8) or (bytes[43].toInt() and 0xFF)
                    if (dstPort == 53) {
                        isDns = true
                        protocolStr = "DNS (IPv6)"
                    }
                }
            }
        }

        var queriedDomain: String? = null
        if (isDns && version == 4) {
            val ihl = (versionAndIHL and 0x0F) * 4
            val dnsOffset = ihl + 8 + 12
            if (length > dnsOffset) {
                queriedDomain = parseDnsQuestionDomain(bytes, dnsOffset, length)
            }
        }

        val primaryPkg = blockedPackages.firstOrNull() ?: "isolated.app"

        return FirewallLogEntry(
            packageName = primaryPkg,
            appName = primaryPkg.substringAfterLast("."),
            protocol = protocolStr,
            sourceIp = srcIp,
            destinationIp = dstIp,
            destinationPort = dstPort,
            queriedDomain = queriedDomain,
            action = "BLOCKED",
            ruleReason = when {
                queriedDomain != null -> "DNS Sinkhole: $queriedDomain"
                isDns -> "DNS Leak Prevention"
                else -> "Complete Network Isolation Active"
            }
        )
    }

    private fun parseDnsQuestionDomain(bytes: ByteArray, startOffset: Int, totalLength: Int): String? {
        return try {
            val sb = StringBuilder()
            var ptr = startOffset
            while (ptr < totalLength) {
                val labelLen = bytes[ptr].toInt() and 0xFF
                if (labelLen == 0) break
                if (ptr + 1 + labelLen > totalLength) break
                if (sb.isNotEmpty()) sb.append(".")
                for (i in 0 until labelLen) {
                    sb.append(bytes[ptr + 1 + i].toInt().toChar())
                }
                ptr += 1 + labelLen
            }
            val res = sb.toString()
            if (res.isNotBlank() && res.contains(".")) res else null
        } catch (e: Exception) {
            null
        }
    }

    private fun stopVpnWorker() {
        workerJob?.cancel()
        workerJob = null
    }

    private fun stopVpn() {
        stopVpnWorker()
        _isRunning.value = false
        try {
            vpnInterface?.close()
        } catch (ignored: Exception) {}
        vpnInterface = null
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IsolateBox Local Firewall",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "جدار الحماية وعزل شبكة التطبيقات داخل IsolateBox"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("جدار الحماية المحلي نشط | IsolateBox")
            .setContentText("يتم حجب كافة اتصالات التطبيقات المعزولة محلياً")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
