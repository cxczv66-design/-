package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an installed application managed inside the IsolateBox sandbox.
 */
@Entity(tableName = "sandboxed_apps")
data class SandboxedApp(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    // Network Isolation Rules
    val blockAllNetwork: Boolean = true,
    val blockIpv4: Boolean = true,
    val blockIpv6: Boolean = true,
    val blockDns: Boolean = true,
    // File & Storage Isolation
    val restrictSharedStorage: Boolean = true,
    val restrictMediaAccess: Boolean = true,
    val useEncryptedVaultOnly: Boolean = true,
    // Deep AppOps & Sensitive Sensor Isolation
    val revokeLocation: Boolean = true,
    val revokeCameraMic: Boolean = true,
    val revokeContactsCalendar: Boolean = true,
    val blockClipboardSnooping: Boolean = true,
    val blockWakeLock: Boolean = true,
    val blockBackgroundRun: Boolean = true,
    val blockSensors: Boolean = true,
    // Zero-Trace Hibernation & Lifecycle
    val autoFreezeOnExit: Boolean = false,
    val isFrozen: Boolean = false,
    val autoStopOnExit: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastLaunchedAt: Long = 0L,
    val totalBlockedPackets: Long = 0L
)

/**
 * Log entry for network traffic intercepted and dropped by the local VpnService firewall.
 */
@Entity(tableName = "firewall_logs")
data class FirewallLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val appName: String,
    val protocol: String, // "TCP", "UDP", "DNS", "ICMP", "OTHER"
    val sourceIp: String,
    val destinationIp: String,
    val destinationPort: Int,
    val queriedDomain: String? = null,
    val action: String = "BLOCKED",
    val ruleReason: String
)

/**
 * Represents an active or historical sandboxed execution session.
 */
@Entity(tableName = "sandbox_sessions")
data class SandboxSession(
    @PrimaryKey
    val sessionId: String,
    val packageName: String,
    val appName: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val status: String = "RUNNING", // "RUNNING", "STOPPED", "BLOCKED"
    val blockedPacketsCount: Int = 0,
    val shizukuEnforced: Boolean = false,
    val vpnActive: Boolean = true,
    val notes: String = ""
)

/**
 * Item in the installed apps picker list.
 */
data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val requestedPermissions: List<String>,
    val grantedPermissions: List<String>,
    val isAlreadySandboxed: Boolean = false,
    val compatibilityRating: CompatibilityRating = CompatibilityRating.EXCELLENT,
    val compatibilityNotice: String = ""
)

enum class CompatibilityRating {
    EXCELLENT, // Fully compatible with network drop & permission revoke
    GOOD,      // Compatible, requires Shizuku or manual permissions strip
    LIMITED    // Heavy system dependency (e.g. system services)
}

/**
 * Model for diagnostic tests results.
 */
data class SecurityTestResult(
    val testName: String,
    val description: String,
    val isPassed: Boolean,
    val resultDetails: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Discovered Tracking SDK / Telemetry component in APK.
 */
data class DiscoveredTracker(
    val name: String,
    val category: String, // "تحليلات سلوكية", "إعلانات وتتبع", "بصمة الجهاز"
    val signature: String,
    val isNeutralized: Boolean = true
)

data class IntrusionReport(
    val score: Int, // 0 - 100
    val level: String, // "منخفض", "متوسط", "شديد التطفل"
    val totalTrackersFound: Int,
    val trackers: List<DiscoveredTracker>
)
