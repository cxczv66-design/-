package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.VaultManager
import com.example.shizuku.ShizukuManager
import com.example.workprofile.WorkProfileHelper
import android.util.Log

class IsolateBoxApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var vaultManager: VaultManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        vaultManager = VaultManager(this)
        ShizukuManager.init()
        if (WorkProfileHelper.isCurrentProfileOwner(this)) {
            WorkProfileHelper.applyProfileDataRestrictions(this)
            if (!WorkProfileHelper.enableAlwaysOnLockdown(this)) {
                Log.e("IsolateBox", "Always-on VPN lockdown could not be confirmed for the managed profile")
            }
        }
    }

    override fun onTerminate() {
        ShizukuManager.cleanup()
        super.onTerminate()
    }
}
