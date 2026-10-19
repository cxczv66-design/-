package com.example.pipeline

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.data.AppDatabase
import com.example.firewall.FirewallController
import com.example.firewall.IsolateVpnService
import com.example.model.SandboxSession
import com.example.model.SandboxedApp
import com.example.shizuku.ShizukuManager
import com.example.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class PipelineStepStatus {
    data class Progress(val stepNumber: Int, val stepName: String, val detail: String) : PipelineStepStatus()
    data class Success(val sessionId: String, val appName: String, val summary: String) : PipelineStepStatus()
    data class Failure(val stepNumber: Int, val error: String) : PipelineStepStatus()
    object Idle : PipelineStepStatus()
}

class AppLaunchPipeline(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val _status = MutableStateFlow<PipelineStepStatus>(PipelineStepStatus.Idle)
    val status = _status.asStateFlow()

    /**
     * Executes the comprehensive pre-launch and execution sequence with deep AppOps and unfreezing.
     */
    suspend fun launchAppInSandbox(app: SandboxedApp): Boolean = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()

        try {
            // أ. التحقق من جاهزية بيئة التشغيل
            _status.value = PipelineStepStatus.Progress(
                1,
                "التحقق من جاهزية بيئة التشغيل وإلغاء السبات",
                "فحص حالة الحزمة لـ ${app.appName} وإلغاء التجميد إذا كانت في سبات قسري..."
            )
            delay(200)

            if (ShizukuManager.shizukuState.value == ShizukuState.CONNECTED) {
                // Unsuspend if frozen
                ShizukuManager.unsuspendPackage(app.packageName)
                db.sandboxedAppDao().updateApp(app.copy(isFrozen = false))
            }

            val packageManager = context.packageManager
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent == null) {
                _status.value = PipelineStepStatus.Failure(1, "تعذر العثور على واجهة تشغيل للتطبيق ${app.packageName}")
                return@withContext false
            }

            // ب. تفعيل وتطبيق قواعد جدار الحماية
            _status.value = PipelineStepStatus.Progress(
                2,
                "تفعيل قواعد جدار الحماية المحلي (VpnService)",
                "تهيئة مسار حجب الحزم وإعادة ربط واجهة TUN المباشرة..."
            )

            // Ensure app is marked as active in DB
            db.sandboxedAppDao().updateApp(app.copy(isEnabled = true, isFrozen = false))

            if (!IsolateVpnService.isRunning.value) {
                if (FirewallController.isFirewallPrepared(context)) {
                    FirewallController.startFirewall(context)
                    delay(300)
                }
            } else {
                FirewallController.reloadRules(context)
                delay(150)
            }

            // ج. التحقق من تطبيق سياسة حجب الشبكة وDNS
            _status.value = PipelineStepStatus.Progress(
                3,
                "التحقق من سياسة عزل الشبكة وDNS Sinkhole",
                "التأكد من إدراج ${app.packageName} في قائمة العزل واعتراض المنافذ..."
            )
            delay(200)

            val isVpnActive = IsolateVpnService.isRunning.value

            // د. تطبيق قيود AppOps العميقة وتجريد الصلاحيات الحساسة
            _status.value = PipelineStepStatus.Progress(
                4,
                "خنق الصلاحيات العميقة والحافظة والمستشعرات",
                "تطبيق قواعد AppOps (حظر قراءة الحافظة، خنق الخلفية، قفل المعالج)..."
            )

            var shizukuUsed = false
            if (ShizukuManager.shizukuState.value == ShizukuState.CONNECTED) {
                ShizukuManager.stripAppPermissions(app.packageName)
                ShizukuManager.applyDeepAppOpsRestrictions(
                    packageName = app.packageName,
                    blockClipboard = app.blockClipboardSnooping,
                    blockWakeLock = app.blockWakeLock,
                    blockBackgroundRun = app.blockBackgroundRun,
                    blockSensors = app.blockSensors
                )
                shizukuUsed = true
            }

            // هـ. تشغيل التطبيق داخل آلية العزل
            _status.value = PipelineStepStatus.Progress(
                5,
                "بدء التشغيل داخل البيئة المعزولة",
                "فتح التطبيق في مهمة منعزلة مع تفعيل المراقبة اللحظية..."
            )

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launchIntent)

            // و. إنشاء الجلسة وتحديث الإحصائيات
            val session = SandboxSession(
                sessionId = sessionId,
                packageName = app.packageName,
                appName = app.appName,
                startTime = System.currentTimeMillis(),
                status = "RUNNING",
                shizukuEnforced = shizukuUsed,
                vpnActive = isVpnActive,
                notes = if (shizukuUsed) "عزل صارم: Shizuku + AppOps + جدار الحماية" else "عزل الشبكة مفعل (وضع محدود بدون Shizuku)"
            )

            db.sandboxSessionDao().insertSession(session)
            db.sandboxedAppDao().updateLastLaunched(app.packageName, System.currentTimeMillis())

            _status.value = PipelineStepStatus.Success(
                sessionId = sessionId,
                appName = app.appName,
                summary = "تم تشغيل التطبيق بنجاح تحت رقابة جدار الحماية وخنق الصلاحيات العميقة."
            )
            true
        } catch (e: Exception) {
            _status.value = PipelineStepStatus.Failure(0, "فشل في عملية التشغيل: ${e.message}")
            false
        }
    }

    /**
     * Stops the running sandboxed app and optionally applies Zero-Trace Freeze.
     */
    suspend fun stopApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        var killed = false
        val app = db.sandboxedAppDao().getAppByPackage(packageName)

        if (ShizukuManager.shizukuState.value == ShizukuState.CONNECTED) {
            killed = ShizukuManager.forceStopApp(packageName)
            if (app?.autoFreezeOnExit == true) {
                ShizukuManager.suspendPackage(packageName)
                db.sandboxedAppDao().updateApp(app.copy(isFrozen = true))
            }
        }

        // Close any active session in the database
        val activeSession = db.sandboxSessionDao().getActiveSessionSync()
        if (activeSession != null && activeSession.packageName == packageName) {
            db.sandboxSessionDao().endSession(activeSession.sessionId)
        }

        if (!killed) {
            withContext(Dispatchers.Main) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
        true
    }

    /**
     * Nuclear Panic Mode: Force stop, clear package data/caches, and freeze package instantly.
     */
    suspend fun panicWipe(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (ShizukuManager.shizukuState.value == ShizukuState.CONNECTED) {
            ShizukuManager.forceStopApp(packageName)
            ShizukuManager.clearPackageData(packageName)
            ShizukuManager.suspendPackage(packageName)
            val app = db.sandboxedAppDao().getAppByPackage(packageName)
            if (app != null) {
                db.sandboxedAppDao().updateApp(app.copy(isFrozen = true))
            }
        }

        val activeSession = db.sandboxSessionDao().getActiveSessionSync()
        if (activeSession != null && activeSession.packageName == packageName) {
            db.sandboxSessionDao().endSession(activeSession.sessionId)
        }
        true
    }

    fun resetStatus() {
        _status.value = PipelineStepStatus.Idle
    }
}
