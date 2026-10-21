package com.example.pipeline

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.example.data.AppDatabase
import com.example.firewall.FirewallController
import com.example.firewall.IsolateVpnService
import com.example.model.SandboxSession
import com.example.model.SandboxedApp
import com.example.workprofile.IsolateBoxDeviceAdminReceiver
import com.example.workprofile.WorkProfileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

sealed class PipelineStepStatus {
    data class Progress(val stepNumber: Int, val stepName: String, val detail: String) : PipelineStepStatus()
    data class Success(val sessionId: String, val appName: String, val summary: String) : PipelineStepStatus()
    data class Failure(val stepNumber: Int, val error: String) : PipelineStepStatus()
    object Idle : PipelineStepStatus()
}

/** Launch gate: never describes the personal-profile copy as an isolated environment. */
class AppLaunchPipeline(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val _status = MutableStateFlow<PipelineStepStatus>(PipelineStepStatus.Idle)
    val status = _status.asStateFlow()

    suspend fun launchAppInSandbox(app: SandboxedApp): Boolean = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        try {
            _status.value = PipelineStepStatus.Progress(
                1,
                "التحقق من ملف العمل المدار",
                "لن تُشغّل النسخة الشخصية للتطبيق على أنها بيئة معزولة."
            )
            if (!WorkProfileHelper.isCurrentProfileOwner(context)) {
                _status.value = PipelineStepStatus.Failure(
                    1,
                    "افتح نسخة IsolateBox ذات رمز الحقيبة داخل ملف العمل. هذه النسخة ليست مالك الملف، لذلك لا تستطيع فرض عزل الملفات وقفل الشبكة."
                )
                return@withContext false
            }
            if (!app.blockAllNetwork) {
                _status.value = PipelineStepStatus.Failure(
                    1,
                    "الحماية الافتراضية تتطلب حظر الشبكة بالكامل؛ لا يمكن تشغيل هذا التطبيق بإعداد يسمح بالشبكة."
                )
                return@withContext false
            }

            val packageManager = context.packageManager
            val targetInfo = try {
                packageManager.getApplicationInfo(app.packageName, 0)
            } catch (_: Exception) {
                null
            }
            if (targetInfo == null) {
                _status.value = PipelineStepStatus.Failure(1, "التطبيق غير مثبت في ملف العمل الحالي؛ ثبّت نسخته داخل الملف أولاً.")
                return@withContext false
            }
            if ((targetInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                _status.value = PipelineStepStatus.Failure(
                    1,
                    "حزم النظام غير مدعومة كأهداف؛ قد تملك استثناءات شبكة خاصة على Android. لم يُشغّل الهدف."
                )
                return@withContext false
            }
            val launchIntent = try {
                packageManager.getLaunchIntentForPackage(app.packageName)
            } catch (_: Exception) {
                null
            }
            if (launchIntent == null) {
                _status.value = PipelineStepStatus.Failure(
                    1,
                    "لم يُعثر على نسخة قابلة للتشغيل من ${app.packageName} في ملف العمل الحالي. ثبّت التطبيق من متجر Play داخل الملف أولاً."
                )
                return@withContext false
            }

            _status.value = PipelineStepStatus.Progress(
                2,
                "فرض سياسات ملف العمل",
                "تطبيق قيود المشاركة بين الملفات ورفض أذونات Android الحساسة المطلوبة للحزمة."
            )
            WorkProfileHelper.applyProfileDataRestrictions(context)
            WorkProfileHelper.denySensitiveRuntimePermissions(context, app.packageName)

            _status.value = PipelineStepStatus.Progress(
                3,
                "تأكيد VPN الدائم مع قفل المرور",
                "يجب أن يكون ملف العمل مقيداً حتى إن توقف عامل VPN."
            )
            if (!WorkProfileHelper.enableAlwaysOnLockdown(context) ||
                !WorkProfileHelper.isLockdownEnabled(context)
            ) {
                _status.value = PipelineStepStatus.Failure(
                    3,
                    "تعذر تأكيد always-on VPN مع lockdown في ملف العمل؛ أُوقف الإطلاق لتجنب تسرب الشبكة. تحقق من دعم الجهاز أو وجود VPN آخر."
                )
                return@withContext false
            }

            if (!IsolateVpnService.isPrepared(context)) {
                _status.value = PipelineStepStatus.Failure(
                    3,
                    "لم يمنح Android إذن VPN في ملف العمل. وافق على نافذة VPN ثم أعد التشغيل."
                )
                return@withContext false
            }

            if (app.isFrozen) {
                val dpm = context.getSystemService(DevicePolicyManager::class.java)
                val admin = ComponentName(context, IsolateBoxDeviceAdminReceiver::class.java)
                val failedToResume = runCatching {
                    dpm?.setPackagesSuspended(admin, arrayOf(app.packageName), false).orEmpty()
                }.getOrElse { arrayOf(app.packageName) }
                if (failedToResume.isNotEmpty()) {
                    _status.value = PipelineStepStatus.Failure(
                        2,
                        "تعذر إلغاء تعليق التطبيق من سياسة ملف العمل: ${failedToResume.joinToString()}"
                    )
                    return@withContext false
                }
            }

            _status.value = PipelineStepStatus.Progress(
                4,
                "إنشاء/تحديث مرشح الحزم المحلي",
                "إدراج ${app.packageName} في VPN المحلي الذي لا يمرر الحزم إلى أي خادم."
            )
            db.sandboxedAppDao().updateApp(app.copy(isEnabled = true, isFrozen = false))
            FirewallController.reloadRules(context)

            val protected = withTimeoutOrNull(8_000) {
                combine(
                    IsolateVpnService.isRunning,
                    IsolateVpnService.activeBlockedPackages
                ) { running, packages -> running && app.packageName in packages }
                    .first { it }
            } == true
            if (!protected || !WorkProfileHelper.isLockdownEnabled(context)) {
                _status.value = PipelineStepStatus.Failure(
                    4,
                    "لم يؤكد النظام أن الحزمة محمية فعلياً داخل واجهة TUN مع lockdown؛ لم يُفتح التطبيق."
                )
                return@withContext false
            }

            _status.value = PipelineStepStatus.Progress(
                5,
                "بدء النسخة المثبتة في ملف العمل",
                "أصبح حجب الشبكة وقفل الملف مؤكدين قبل طلب تشغيل التطبيق."
            )
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launchIntent)

            db.sandboxSessionDao().insertSession(
                SandboxSession(
                    sessionId = sessionId,
                    packageName = app.packageName,
                    appName = app.appName,
                    status = "RUNNING",
                    vpnActive = true,
                    notes = "Work Profile owner + always-on VPN lockdown confirmed; local TUN drops routed packets."
                )
            )
            db.sandboxedAppDao().updateLastLaunched(app.packageName, System.currentTimeMillis())
            _status.value = PipelineStepStatus.Success(
                sessionId = sessionId,
                appName = app.appName,
                summary = "شُغّلت نسخة ملف العمل بعد تأكيد قفل VPN. الحزم الموجهة إلى VPN المحلي تُسقط؛ لا تُفحص ذاكرة التطبيق أو محتوى ملفاته."
            )
            true
        } catch (e: Exception) {
            _status.value = PipelineStepStatus.Failure(0, "فشل التشغيل الآمن: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    /** Suspend the app in this managed profile; never clears its data. */
    suspend fun stopApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!WorkProfileHelper.isCurrentProfileOwner(context)) {
            _status.value = PipelineStepStatus.Failure(1, "لا يمكن تعليق الحزمة؛ نسخة IsolateBox الحالية ليست مالك ملف العمل.")
            return@withContext false
        }
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm == null) {
            _status.value = PipelineStepStatus.Failure(1, "خدمة Android DevicePolicyManager غير متاحة.")
            return@withContext false
        }
        val admin = ComponentName(context, IsolateBoxDeviceAdminReceiver::class.java)
        val failed = runCatching { dpm.setPackagesSuspended(admin, arrayOf(packageName), true) }
            .getOrElse { arrayOf(packageName) }
        if (failed.isNotEmpty()) {
            _status.value = PipelineStepStatus.Failure(
                1,
                "لم يؤكد Android تعليق $packageName. لم يُحذف التطبيق أو بياناته."
            )
            return@withContext false
        }

        val app = db.sandboxedAppDao().getAppByPackage(packageName)
        if (app != null) db.sandboxedAppDao().updateApp(app.copy(isFrozen = true))
        val activeSession = db.sandboxSessionDao().getActiveSessionSync()
        if (activeSession?.packageName == packageName) {
            db.sandboxSessionDao().endSession(activeSession.sessionId)
        }
        _status.value = PipelineStepStatus.Success(
            sessionId = "suspended-${System.currentTimeMillis()}",
            appName = app?.appName ?: packageName,
            summary = "أكد Android تعليق الحزمة في ملف العمل وإنهاء جلسة IsolateBox. لم تُمسح بيانات التطبيق."
        )
        true
    }

    fun resetStatus() {
        _status.value = PipelineStepStatus.Idle
    }
}
