package com.example.firewall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.model.FirewallLogEntry
import com.example.workprofile.WorkProfileHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Local packet sink. It establishes an Android TUN interface for the selected packages and
 * intentionally never writes packets back or forwards them to a remote VPN server.
 */
class IsolateVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workerJob: Job? = null
    private val establishMutex = Mutex()
    private val lastPersistedByFlow = ConcurrentHashMap<String, Long>()
    private var lastNotificationUpdate = 0L

    enum class State { STOPPED, STARTING, ACTIVE, NO_APPS, FAILED }

    companion object {
        const val ACTION_START = "com.example.firewall.START"
        const val ACTION_STOP = "com.example.firewall.STOP"
        const val ACTION_RELOAD = "com.example.firewall.RELOAD"
        const val NOTIFICATION_ID = 4040
        const val CHANNEL_ID = "isolatebox_firewall_channel"
        private const val TAG = "IsolateVpn"
        private const val LOG_SAMPLE_INTERVAL_MS = 15_000L
        private const val COUNTER_BATCH_SIZE = 32
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 10_000L

        private val _state = MutableStateFlow(State.STOPPED)
        val state = _state.asStateFlow()
        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _serviceMessage = MutableStateFlow("متوقف")
        val serviceMessage = _serviceMessage.asStateFlow()

        private val _totalBlocked = MutableStateFlow(0L)
        val totalBlocked = _totalBlocked.asStateFlow()

        private val _activeBlockedPackages = MutableStateFlow<Set<String>>(emptySet())
        val activeBlockedPackages = _activeBlockedPackages.asStateFlow()

        private val _recentLogs = MutableSharedFlow<FirewallLogEntry>(extraBufferCapacity = 50)
        val recentLogs = _recentLogs.asSharedFlow()

        fun isPrepared(context: android.content.Context): Boolean = prepare(context) == null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                if (WorkProfileHelper.isCurrentProfileOwner(this)) {
                    _serviceMessage.value = "VPN مُدار من مالك ملف العمل؛ لا يمكن إيقافه من لوحة التطبيق."
                    return START_STICKY
                }
                stopVpn()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_RELOAD -> {
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
                _state.value = State.STARTING
                _serviceMessage.value = "جارٍ إعداد واجهة VPN المحلية…"
                serviceScope.launch { establishMutex.withLock { establishDropper() } }
            }
        }
        return START_STICKY
    }

    private suspend fun establishDropper() {
        val profileOwner = WorkProfileHelper.isCurrentProfileOwner(this)
        val lockdown = WorkProfileHelper.isLockdownEnabled(this)
        val apps = runCatching {
            AppDatabase.getInstance(applicationContext).sandboxedAppDao().getActiveSandboxedApps()
        }.getOrElse {
            Log.e(TAG, "Could not load firewall rules", it)
            if (!profileOwner) {
                failClosed("تعذر قراءة القواعد؛ لم يُنشأ نفق شخصي غير محدد.")
                return
            }
            emptyList()
        }

        val packages = apps
            .filter { it.isEnabled && it.blockAllNetwork }
            .map { it.packageName }
            .toSet()

        // Never create an empty per-app allowlist: Android interprets that as all apps using the
        // TUN. In an owned profile, DPM lockdown itself blocks non-VPN traffic while no target
        // rules exist. Keep the VPN disconnected until at least one target is explicitly added.
        if (packages.isEmpty()) {
            workerJob?.cancel()
            workerJob = null
            runCatching { vpnInterface?.close() }
            vpnInterface = null
            _activeBlockedPackages.value = emptySet()
            _isRunning.value = false
            _state.value = State.NO_APPS
            _serviceMessage.value = if (profileOwner && lockdown) {
                "لا توجد قواعد هدف؛ لم يُنشأ TUN شامل. قفل DPC يمنع المرور غير المار عبر VPN، وقد تتأثر تطبيقات الملف الأخرى."
            } else {
                "لا توجد تطبيقات محددة للحجب؛ لم يُنشأ TUN."
            }
            return
        }

        try {
            val builder = Builder()
                .setSession("IsolateBox local packet dropper")
                .setMtu(1500)
                .setBlocking(true)
                .addAddress("10.120.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:1::1", 128)
                .addRoute("::", 0)
                .addDnsServer("10.120.0.1")

            if (packages.isNotEmpty()) {
                val added = mutableSetOf<String>()
                val errors = mutableListOf<String>()
                for (pkg in packages) {
                    try {
                        builder.addAllowedApplication(pkg)
                        added.add(pkg)
                    } catch (e: Exception) {
                        errors.add("$pkg: ${e.message ?: "not installed in this profile"}")
                    }
                }
                // Never declare protection if any intended package would bypass the TUN.
                if (errors.isNotEmpty() || added != packages) {
                    failClosed("تعذر إدراج كل الحزم في VPN؛ لم يُعلن نجاح الحماية. ${errors.joinToString("; ")}")
                    return
                }
            }

            val nextInterface = builder.establish()
            if (nextInterface == null) {
                failClosed("رفض Android إنشاء واجهة TUN. تحقق من إذن VPN أو من VPN آخر نشط.")
                return
            }

            val previousInterface = vpnInterface
            val previousWorker = workerJob
            vpnInterface = nextInterface
            _activeBlockedPackages.value = packages
            _isRunning.value = true
            _state.value = State.ACTIVE
            _serviceMessage.value = if (profileOwner && lockdown) {
                "TUN نشط؛ الحزم الموجهة تُسقط، وملف العمل مقفول عند توقف VPN."
            } else {
                "TUN نشط؛ الحجب للشبكة فقط ويستمر ما دامت الخدمة فعالة."
            }
            workerJob = startPacketDropperWorker(nextInterface, packages)
            previousWorker?.cancel()
            runCatching { previousInterface?.close() }
            Log.i(TAG, "Local packet sink established for ${packages.size} package(s); lockdown=$lockdown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish local VPN dropper", e)
            failClosed("فشل إعداد VPN: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun startPacketDropperWorker(
        descriptor: ParcelFileDescriptor,
        protectedPackages: Set<String>
    ): Job = serviceScope.launch(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val bytes = ByteArray(32 * 1024)
        var packetsSincePersist = 0
        try {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                while (currentCoroutineContext().isActive) {
                    val length = input.read(bytes)
                    if (length <= 0) break

                    // A successfully read TUN packet is an actual intercepted outbound packet.
                    _totalBlocked.value = _totalBlocked.value + 1
                    packetsSincePersist++
                    val entry = parsePacket(bytes, length, protectedPackages)
                    if (entry != null) {
                        val flowKey = "${entry.protocol}|${entry.destinationIp}|${entry.destinationPort}|${entry.queriedDomain.orEmpty()}"
                        val now = System.currentTimeMillis()
                        val previous = lastPersistedByFlow.put(flowKey, now)
                        // Persist a sample rather than creating an unbounded DB row per retry.
                        if (previous == null || now - previous >= LOG_SAMPLE_INTERVAL_MS) {
                            _recentLogs.tryEmit(entry)
                            runCatching { db.firewallLogDao().insertLog(entry) }
                        }
                        if (lastPersistedByFlow.size > 512) {
                            lastPersistedByFlow.entries.removeIf { now - it.value > LOG_SAMPLE_INTERVAL_MS * 4 }
                        }
                    }

                    if (packetsSincePersist >= COUNTER_BATCH_SIZE && protectedPackages.size == 1) {
                        val pkg = protectedPackages.first()
                        runCatching { db.sandboxedAppDao().incrementBlockedPackets(pkg, packetsSincePersist.toLong()) }
                        packetsSincePersist = 0
                    }
                    val notifyAt = System.currentTimeMillis()
                    if (notifyAt - lastNotificationUpdate >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                        lastNotificationUpdate = notifyAt
                        runCatching {
                            getSystemService(NotificationManager::class.java)
                                ?.notify(NOTIFICATION_ID, buildForegroundNotification())
                        }
                    }
                    // Deliberately do not write the packet to TUN or a network socket.
                }
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) Log.w(TAG, "TUN reader stopped", e)
        } finally {
            if (packetsSincePersist > 0 && protectedPackages.size == 1) {
                runCatching {
                    db.sandboxedAppDao().incrementBlockedPackets(protectedPackages.first(), packetsSincePersist.toLong())
                }
            }
        }
    }

    private fun parsePacket(bytes: ByteArray, length: Int, protectedPackages: Set<String>): FirewallLogEntry? {
        if (length < 20) return null
        val version = (bytes[0].toInt() and 0xFF) ushr 4
        var protocol = "OTHER"
        var sourceIp = ""
        var destinationIp = ""
        var destinationPort = 0
        var dnsOffset = -1
        var isDns = false

        when (version) {
            4 -> {
                val headerLength = (bytes[0].toInt() and 0x0F) * 4
                if (headerLength < 20 || length < headerLength) return null
                val protocolNumber = bytes[9].toInt() and 0xFF
                sourceIp = (12..15).joinToString(".") { (bytes[it].toInt() and 0xFF).toString() }
                destinationIp = (16..19).joinToString(".") { (bytes[it].toInt() and 0xFF).toString() }
                if ((protocolNumber == 6 || protocolNumber == 17) && length >= headerLength + 4) {
                    destinationPort = ((bytes[headerLength + 2].toInt() and 0xFF) shl 8) or
                        (bytes[headerLength + 3].toInt() and 0xFF)
                }
                protocol = when (protocolNumber) {
                    6 -> "TCP"
                    17 -> if (destinationPort == 53) "DNS (UDP)" else "UDP"
                    1 -> "ICMP"
                    else -> "IPv4 ($protocolNumber)"
                }
                if (protocolNumber == 17 && destinationPort == 53) {
                    isDns = true
                    dnsOffset = headerLength + 8 + 12
                }
            }
            6 -> {
                if (length < 40) return null
                sourceIp = address(bytes, 8, 16)
                destinationIp = address(bytes, 24, 16)
                val nextHeader = bytes[6].toInt() and 0xFF
                protocol = when (nextHeader) {
                    6 -> "TCP (IPv6)"
                    17 -> "UDP (IPv6)"
                    58 -> "ICMPv6"
                    else -> "IPv6 ($nextHeader)"
                }
                if (nextHeader == 17 && length >= 44) {
                    destinationPort = ((bytes[42].toInt() and 0xFF) shl 8) or (bytes[43].toInt() and 0xFF)
                    if (destinationPort == 53) {
                        isDns = true
                        protocol = "DNS (IPv6)"
                        dnsOffset = 40 + 8 + 12
                    }
                }
            }
            else -> return null
        }

        val domain = if (isDns && dnsOffset >= 0) parseDnsQuestion(bytes, dnsOffset, length) else null
        val singlePackage = protectedPackages.singleOrNull()
        val packageName = singlePackage ?: if (protectedPackages.isEmpty()) "managed-profile-default-deny" else "multiple-protected-apps"
        val appName = singlePackage?.let { pkg ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
                .getOrDefault(pkg)
        } ?: if (protectedPackages.isEmpty()) "كل تطبيقات ملف العمل" else "عدة تطبيقات محمية"

        return FirewallLogEntry(
            packageName = packageName,
            appName = appName,
            protocol = protocol,
            sourceIp = sourceIp,
            destinationIp = destinationIp,
            destinationPort = destinationPort,
            queriedDomain = domain,
            action = "BLOCKED",
            ruleReason = if (domain != null) "حجب استعلام DNS ظاهر: $domain" else "أُسقطت الحزمة محليًا دون تمريرها إلى شبكة خارجية"
        )
    }

    private fun address(bytes: ByteArray, start: Int, count: Int): String =
        runCatching { InetAddress.getByAddress(bytes.copyOfRange(start, start + count)).hostAddress.orEmpty() }
            .getOrDefault("")

    private fun parseDnsQuestion(bytes: ByteArray, startOffset: Int, totalLength: Int): String? {
        if (startOffset < 0 || startOffset >= totalLength) return null
        return runCatching {
            val labels = mutableListOf<String>()
            var ptr = startOffset
            var totalNameLength = 0
            while (ptr < totalLength) {
                val labelLength = bytes[ptr].toInt() and 0xFF
                if (labelLength == 0) break
                if (labelLength > 63 || ptr + 1 + labelLength > totalLength) return null
                val labelBytes = bytes.copyOfRange(ptr + 1, ptr + 1 + labelLength)
                val label = String(labelBytes, StandardCharsets.US_ASCII)
                if (label.any { it.code !in 33..126 || it == '.' }) return null
                labels.add(label)
                totalNameLength += labelLength + 1
                if (totalNameLength > 253) return null
                ptr += labelLength + 1
            }
            labels.takeIf { it.isNotEmpty() }?.joinToString(".")
        }.getOrNull()
    }

    private fun failClosed(message: String, state: State = State.FAILED) {
        val existingInterfaceIsPresent = vpnInterface != null
        _isRunning.value = existingInterfaceIsPresent
        _state.value = if (existingInterfaceIsPresent) State.ACTIVE else state
        if (!existingInterfaceIsPresent) _activeBlockedPackages.value = emptySet()
        _serviceMessage.value = if (existingInterfaceIsPresent) {
            "فشل تحديث القواعد؛ بقيت واجهة VPN السابقة مفتوحة. $message"
        } else message
        Log.e(TAG, message)
        // Keep the previous TUN and its worker on a failed reload; never create a bypass window.
    }

    private fun stopVpn() {
        workerJob?.cancel()
        workerJob = null
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        _isRunning.value = false
        _state.value = State.STOPPED
        _serviceMessage.value = "متوقف"
        _activeBlockedPackages.value = emptySet()
    }

    override fun onRevoke() {
        stopVpn()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "IsolateBox Local Firewall", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = "عدّاد إسقاط الحزم بواسطة VPN المحلي في ملف العمل"
                    setShowBadge(false)
                }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val networkMode = if (WorkProfileHelper.isLockdownEnabled(this)) "VPN دائم + lockdown" else "حجب شبكي أثناء نشاط الخدمة"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IsolateBox — حاجب الحزم المحلي")
            .setContentText("$networkMode · ${_totalBlocked.value} حزمة أُسقطت")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

}
