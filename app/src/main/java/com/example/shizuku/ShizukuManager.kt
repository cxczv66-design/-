package com.example.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

enum class ShizukuState {
    CONNECTED,
    PERMISSION_REQUIRED,
    SERVICE_NOT_RUNNING,
    UNSUPPORTED
}

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val isSuccess: Boolean
)

object ShizukuManager {

    private const val TAG = "IsolateShizuku"
    const val REQUEST_CODE_SHIZUKU = 9001

    private val _shizukuState = MutableStateFlow(ShizukuState.SERVICE_NOT_RUNNING)
    val shizukuState = _shizukuState.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _shizukuState.value = ShizukuState.SERVICE_NOT_RUNNING
        Log.w(TAG, "Shizuku binder died.")
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_SHIZUKU) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    _shizukuState.value = ShizukuState.CONNECTED
                } else {
                    _shizukuState.value = ShizukuState.PERMISSION_REQUIRED
                }
            }
        }

    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            checkState()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register Shizuku listeners: ${e.message}")
            _shizukuState.value = ShizukuState.UNSUPPORTED
        }
    }

    fun cleanup() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        } catch (ignored: Throwable) {}
    }

    fun checkState() {
        try {
            if (!Shizuku.pingBinder()) {
                _shizukuState.value = ShizukuState.SERVICE_NOT_RUNNING
                return
            }

            val hasPermission = if (Shizuku.isPreV11()) {
                _shizukuState.value = ShizukuState.UNSUPPORTED
                return
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }

            _shizukuState.value = if (hasPermission) {
                ShizukuState.CONNECTED
            } else {
                ShizukuState.PERMISSION_REQUIRED
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking Shizuku state: ${e.message}")
            _shizukuState.value = ShizukuState.SERVICE_NOT_RUNNING
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission: ${e.message}")
        }
    }

    /**
     * Executes an ADB command via Shizuku binder IPC using reflection.
     */
    suspend fun executeCommand(cmd: Array<String>): CommandResult = withContext(Dispatchers.IO) {
        if (_shizukuState.value != ShizukuState.CONNECTED) {
            return@withContext CommandResult(-1, "Shizuku not connected or unauthorized", false)
        }

        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, cmd, null, null) as java.lang.Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val exitCode = process.waitFor()
            CommandResult(exitCode, output.toString().trim(), exitCode == 0)
        } catch (e: Throwable) {
            Log.e(TAG, "Command execution failed: ${cmd.joinToString(" ")}: ${e.message}")
            CommandResult(-1, e.message ?: "Execution error", false)
        }
    }

    /**
     * Deep AppOps restriction: Clipboard, Wakelock, Background Run, Camera, Location, Media.
     */
    suspend fun applyDeepAppOpsRestrictions(
        packageName: String,
        blockClipboard: Boolean = true,
        blockWakeLock: Boolean = true,
        blockBackgroundRun: Boolean = true,
        blockSensors: Boolean = true
    ): List<String> {
        val applied = mutableListOf<String>()

        if (blockClipboard) {
            executeCommand(arrayOf("cmd", "appops", "set", packageName, "READ_CLIPBOARD", "ignore"))
            executeCommand(arrayOf("cmd", "appops", "set", packageName, "WRITE_CLIPBOARD", "ignore"))
            applied.add("حماية الحافظة (Clipboard Snooping Blocked)")
        }

        if (blockWakeLock) {
            executeCommand(arrayOf("cmd", "appops", "set", packageName, "WAKE_LOCK", "ignore"))
            applied.add("منع إبقاء المعالج نشطاً (WakeLock Blocked)")
        }

        if (blockBackgroundRun) {
            executeCommand(arrayOf("cmd", "appops", "set", packageName, "RUN_IN_BACKGROUND", "ignore"))
            executeCommand(arrayOf("cmd", "appops", "set", packageName, "RUN_ANY_IN_BACKGROUND", "ignore"))
            executeCommand(arrayOf("cmd", "appops", "set", packageName, "START_FOREGROUND", "ignore"))
            applied.add("خنق العمل بالخلفية (Background Run Neutered)")
        }

        if (blockSensors) {
            executeCommand(arrayOf("cmd", "appops", "set", packageName, "BODY_SENSORS", "ignore"))
            executeCommand(arrayOf("cmd", "appops", "set", packageName, "ACTIVITY_RECOGNITION", "ignore"))
            executeCommand(arrayOf("cmd", "appops", "set", packageName, "COARSE_LOCATION", "ignore"))
            executeCommand(arrayOf("cmd", "appops", "set", packageName, "FINE_LOCATION", "ignore"))
            applied.add("عزل مستشعرات الحركة والنشاط")
        }

        return applied
    }

    /**
     * Strips dangerous permissions dynamically.
     */
    suspend fun stripAppPermissions(packageName: String): List<String> {
        val permissionsToRevoke = listOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_CALENDAR"
        )

        val revokedList = mutableListOf<String>()
        for (perm in permissionsToRevoke) {
            val result = executeCommand(arrayOf("pm", "revoke", packageName, perm))
            if (result.isSuccess) {
                revokedList.add(perm)
            }
        }

        return revokedList
    }

    /**
     * Freezes/Suspends the package completely from the system launcher and alarms.
     */
    suspend fun suspendPackage(packageName: String): Boolean {
        val res = executeCommand(arrayOf("pm", "suspend", packageName))
        return res.isSuccess
    }

    /**
     * Unfreezes/Unsuspends the package so it can execute.
     */
    suspend fun unsuspendPackage(packageName: String): Boolean {
        val res = executeCommand(arrayOf("pm", "unsuspend", packageName))
        return res.isSuccess
    }

    /**
     * Nuclear option: Clears all application data (Cache, cookies, local storage).
     */
    suspend fun clearPackageData(packageName: String): Boolean {
        val res = executeCommand(arrayOf("pm", "clear", packageName))
        return res.isSuccess
    }

    /**
     * Trims system cache for zero-trace footprint.
     */
    suspend fun trimCaches(): Boolean {
        val res = executeCommand(arrayOf("pm", "trim-caches", "999999999999"))
        return res.isSuccess
    }

    /**
     * Force stops the application completely.
     */
    suspend fun forceStopApp(packageName: String): Boolean {
        val result = executeCommand(arrayOf("am", "force-stop", packageName))
        return result.isSuccess
    }
}
