package com.example.workprofile

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

data class WorkProfileStatus(
    val hasWorkProfile: Boolean,
    val profileCount: Int,
    val isRunningInWorkProfile: Boolean,
    val description: String
)

object WorkProfileHelper {

    fun getWorkProfileStatus(context: Context): WorkProfileStatus {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

        var hasWorkProfile = false
        var profileCount = 1
        var isCurrentProfileManaged = false

        if (userManager != null && launcherApps != null) {
            val profiles: List<UserHandle> = launcherApps.profiles
            profileCount = profiles.size
            hasWorkProfile = profileCount > 1

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                isCurrentProfileManaged = userManager.isManagedProfile
            }
        }

        val description = when {
            isCurrentProfileManaged -> "أنت تعمل حالياً داخل ملف العمل المشفر والمنفصل كلياً."
            hasWorkProfile -> "تم العثور على ملف عمل إضافي (مستخدم أندرويد منفصل) مفعل على هذا الجهاز."
            else -> "لا يوجد ملف عمل مهيأ حالياً. يمكنك تفعيل عزل المستخدمين التام عبر إعدادات النظام أو أدوات مثل Shelter/Island."
        }

        return WorkProfileStatus(
            hasWorkProfile = hasWorkProfile,
            profileCount = profileCount,
            isRunningInWorkProfile = isCurrentProfileManaged,
            description = description
        )
    }

    /**
     * Creates an intent to launch Work Profile provisioning or Android Settings for Users.
     */
    fun createProvisioningIntent(context: Context): Intent {
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        if (intent.resolveActivity(context.packageManager) != null) {
            return intent
        }
        // Fallback to system user/account settings
        return Intent(android.provider.Settings.ACTION_SYNC_SETTINGS)
    }
}
