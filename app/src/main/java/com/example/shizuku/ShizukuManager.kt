package com.example.shizuku

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuState {
    CONNECTED,
    PERMISSION_REQUIRED,
    SERVICE_NOT_RUNNING,
    UNSUPPORTED
}

/**
 * Shizuku status only. The previous implementation reflected into a non-public process API and
 * reported AppOps actions as applied without checking results. No arbitrary shell or wipe commands
 * are executed by this manager. Network isolation is provided independently by VpnService/DPM.
 */
object ShizukuManager {
    private const val TAG = "IsolateShizuku"
    const val REQUEST_CODE_SHIZUKU = 9001

    private val _shizukuState = MutableStateFlow(ShizukuState.SERVICE_NOT_RUNNING)
    val shizukuState = _shizukuState.asStateFlow()

    private val _backendUid = MutableStateFlow<Int?>(null)
    val backendUid = _backendUid.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { checkState() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _backendUid.value = null
        _shizukuState.value = ShizukuState.SERVICE_NOT_RUNNING
        Log.w(TAG, "Shizuku binder died")
    }
    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == REQUEST_CODE_SHIZUKU) checkState()
        }

    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            checkState()
        } catch (e: Throwable) {
            Log.e(TAG, "Could not register Shizuku listeners", e)
            _backendUid.value = null
            _shizukuState.value = ShizukuState.UNSUPPORTED
        }
    }

    fun cleanup() {
        runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
        runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
        runCatching { Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener) }
    }

    fun checkState() {
        try {
            if (!Shizuku.pingBinder()) {
                _backendUid.value = null
                _shizukuState.value = ShizukuState.SERVICE_NOT_RUNNING
                return
            }
            if (Shizuku.isPreV11()) {
                _backendUid.value = null
                _shizukuState.value = ShizukuState.UNSUPPORTED
                return
            }
            val uid = runCatching { Shizuku.getUid() }.getOrNull()
            _backendUid.value = uid
            _shizukuState.value = if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuState.CONNECTED
            } else {
                ShizukuState.PERMISSION_REQUIRED
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not inspect Shizuku binder", e)
            _backendUid.value = null
            _shizukuState.value = ShizukuState.SERVICE_NOT_RUNNING
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
            else _shizukuState.value = ShizukuState.SERVICE_NOT_RUNNING
        } catch (e: Throwable) {
            Log.e(TAG, "Could not request Shizuku permission", e)
            _shizukuState.value = ShizukuState.UNSUPPORTED
        }
    }
}
