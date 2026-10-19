package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.VaultManager
import com.example.shizuku.ShizukuManager

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
    }

    override fun onTerminate() {
        ShizukuManager.cleanup()
        super.onTerminate()
    }
}
