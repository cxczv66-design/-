package com.example.workprofile

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/** Receives Android's managed-profile provisioning callback. */
class IsolateBoxDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        val admin = ComponentName(context, IsolateBoxDeviceAdminReceiver::class.java)

        runCatching { dpm.setProfileName(admin, "IsolateBox — Work") }
            .onFailure { Log.w(TAG, "Could not name managed profile", it) }
        runCatching { dpm.setProfileEnabled(admin) }
            .onFailure { Log.w(TAG, "Could not enable managed profile", it) }
        runCatching { dpm.enableSystemApp(admin, "com.android.vending") }
            .onFailure { Log.i(TAG, "Managed Google Play is unavailable on this device", it) }

        WorkProfileHelper.applyProfileDataRestrictions(context)
        val lockdownEnabled = WorkProfileHelper.enableAlwaysOnLockdown(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOCKDOWN_CONFIGURED, lockdownEnabled)
            .apply()
        if (!lockdownEnabled) {
            Log.e(TAG, "Managed profile created, but always-on VPN lockdown could not be confirmed")
        }
    }

    companion object {
        private const val TAG = "IsolateBoxDPC"
        private const val PREFS = "profile_provisioning"
        private const val KEY_LOCKDOWN_CONFIGURED = "lockdown_configured"
    }
}
