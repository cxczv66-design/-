package com.example.workprofile

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.UserManager

/** A snapshot of the current Android user/profile and IsolateBox's actual policy authority. */
data class WorkProfileStatus(
    val hasWorkProfile: Boolean,
    val profileCount: Int,
    val isRunningInWorkProfile: Boolean,
    val isProfileOwner: Boolean,
    val networkLockdownEnabled: Boolean,
    val canProvisionManagedProfile: Boolean,
    val description: String
)

object WorkProfileHelper {
    private val sensitiveRuntimePermissions = setOf(
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_PHONE_STATE",
        "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.BODY_SENSORS",
        "android.permission.BODY_SENSORS_BACKGROUND",
        "android.permission.POST_NOTIFICATIONS"
    )

    fun isCurrentProfileOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)
            ?.isProfileOwnerApp(context.packageName) == true

    fun isLockdownEnabled(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isProfileOwnerApp(context.packageName)) return false
        val admin = ComponentName(context, IsolateBoxDeviceAdminReceiver::class.java)
        return try {
            dpm.getAlwaysOnVpnPackage(admin) == context.packageName &&
                dpm.isAlwaysOnVpnLockdownEnabled(admin)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns null unless this device can start Android's managed-profile provisioning flow.
     * Provisioning is performed by Android System UI; this app never creates a profile silently.
     */
    fun createProvisioningIntent(context: Context): Intent? {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)) return null

        val profiles = runCatching {
            context.getSystemService(android.content.pm.LauncherApps::class.java)?.profiles.orEmpty()
        }.getOrDefault(emptyList())
        if (profiles.size > 1) return null

        val admin = ComponentName(context, IsolateBoxDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                admin.flattenToString()
            )
        }
        return intent.takeIf { it.resolveActivity(pm) != null }
    }

    fun getWorkProfileStatus(context: Context): WorkProfileStatus {
        val userManager = context.getSystemService(UserManager::class.java)
        val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
        val isManaged = runCatching { userManager?.isManagedProfile == true }.getOrDefault(false)
        val profileCount = runCatching { launcherApps?.profiles?.size ?: 1 }.getOrDefault(1)
        val hasWorkProfile = isManaged || profileCount > 1
        val owner = isCurrentProfileOwner(context)
        val lockdown = isLockdownEnabled(context)
        val supportsManagedProfile = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)

        val description = when {
            owner && lockdown -> "مالك ملف العمل: VPN دائم وقفل المرور مؤكَّدان. قد تفقد التطبيقات غير النظامية الأخرى اتصالها، ويبقى القفل بعد الجلسة حتى تغييره من إعدادات الملف."
            owner -> "مالك ملف العمل؛ يفعّل IsolateBox قفل المرور قبل التشغيل. قد تتأثر بقية تطبيقات الملف، ويبقى القفل حتى تغييره من إعدادات الملف."
            isManaged -> "داخل ملف عمل، لكن IsolateBox ليس مالك الملف؛ لا يمكنه فرض قفل VPN دائم أو أخذ الملكية من DPC آخر."
            hasWorkProfile -> "يوجد ملف/مستخدم مُدار قائم. إذا كان DPC آخر يملكه فلن يستطيع IsolateBox أخذ الملكية؛ لا تحذفه قبل نسخ البيانات. هذه النسخة الشخصية لا تدير ملفه."
            !supportsManagedProfile -> "هذا الجهاز لا يعلن دعم ملفات Android المُدارة؛ لا يمكن توفير عزل ملف عمل هنا."
            else -> "لا يوجد ملف عمل بعد. أنشئه عبر معالج Android، ثم افتح نسخة IsolateBox ذات رمز الحقيبة."
        }

        return WorkProfileStatus(
            hasWorkProfile = hasWorkProfile,
            profileCount = profileCount,
            isRunningInWorkProfile = isManaged,
            isProfileOwner = owner,
            networkLockdownEnabled = lockdown,
            canProvisionManagedProfile = supportsManagedProfile && !hasWorkProfile,
            description = description
        )
    }

    /** Configure the profile-owner's own VpnService as always-on and fail-closed. */
    fun enableAlwaysOnLockdown(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isProfileOwnerApp(context.packageName)) return false
        val admin = ComponentName(context, IsolateBoxDeviceAdminReceiver::class.java)
        return try {
            dpm.setAlwaysOnVpnPackage(admin, context.packageName, true)
            dpm.getAlwaysOnVpnPackage(admin) == context.packageName &&
                dpm.isAlwaysOnVpnLockdownEnabled(admin)
        } catch (_: Exception) {
            false
        }
    }

    /** Block cross-profile sharing and clipboard paths where Android permits a profile owner to do so. */
    fun applyProfileDataRestrictions(context: Context): List<String> {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return emptyList()
        if (!dpm.isProfileOwnerApp(context.packageName)) return emptyList()
        val admin = ComponentName(context, IsolateBoxDeviceAdminReceiver::class.java)
        val applied = mutableListOf<String>()
        val restrictions = listOf(
            UserManager.DISALLOW_SHARE_INTO_MANAGED_PROFILE,
            UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE
        )
        restrictions.forEach { restriction ->
            try {
                dpm.addUserRestriction(admin, restriction)
                applied.add(restriction)
            } catch (_: Exception) {
                // Restrictions vary by Android version and OEM policy; report only those applied.
            }
        }
        return applied
    }

    /**
     * Deny sensitive dangerous permissions requested by a package installed in this same profile.
     * Returns the permissions for which Android confirmed a policy change.
     */
    fun denySensitiveRuntimePermissions(context: Context, packageName: String): List<String> {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return emptyList()
        if (!dpm.isProfileOwnerApp(context.packageName)) return emptyList()
        val requested = try {
            val info: PackageInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
            info.requestedPermissions.orEmpty().toSet()
        } catch (_: Exception) {
            return emptyList()
        }

        val admin = ComponentName(context, IsolateBoxDeviceAdminReceiver::class.java)
        return requested.intersect(sensitiveRuntimePermissions).filter { permission ->
            runCatching {
                dpm.setPermissionGrantState(
                    admin,
                    packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                )
            }.getOrDefault(false)
        }
    }
}
