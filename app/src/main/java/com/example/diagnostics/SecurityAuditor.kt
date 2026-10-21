package com.example.diagnostics

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.firewall.IsolateVpnService
import com.example.model.SecurityTestResult
import com.example.workprofile.WorkProfileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecurityAuditor(private val context: Context) {

    /** Checks live Android policy state and only reports packet evidence actually observed by TUN. */
    suspend fun runNetworkBlockadeTest(packageName: String): SecurityTestResult = withContext(Dispatchers.IO) {
        val isVpnActive = IsolateVpnService.isRunning.value
        val blockedPackages = IsolateVpnService.activeBlockedPackages.value
        val lockdown = WorkProfileHelper.isLockdownEnabled(context)

        if (!WorkProfileHelper.isCurrentProfileOwner(context) || !lockdown) {
            return@withContext SecurityTestResult(
                testName = "فحص VPN الدائم وملف العمل",
                description = "التحقق من امتلاك ملف العمل وقفل المرور الذي يمنع تجاوز VPN",
                isPassed = false,
                resultDetails = "لم يتم تأكيد ملكية ملف العمل و/أو VPN دائم مع lockdown. لا تعتبر النسخة الشخصية معزولة، ولم يُشغّل التطبيق المستهدف."
            )
        }

        if (!isVpnActive) {
            return@withContext SecurityTestResult(
                testName = "فحص واجهة VPN المحلية",
                description = "التحقق من نشاط TUN قبل تشغيل التطبيق",
                isPassed = false,
                resultDetails = "VPN غير نشط؛ lockdown يمنع المرور غير المحمي إذا ظل مضبوطًا في النظام، لكن لا يمكن تشغيل الهدف قبل تأكيد واجهة TUN."
            )
        }

        if (packageName !in blockedPackages) {
            return@withContext SecurityTestResult(
                testName = "فحص قائمة تطبيقات VPN",
                description = "التحقق من إدراج التطبيق في قائمة TUN قبل الإطلاق",
                isPassed = false,
                resultDetails = "الحزمة $packageName ليست ضمن قائمة الحزم الموجهة إلى TUN؛ لم تُعلن حمايتها."
            )
        }

        val observedPackets = IsolateVpnService.totalBlocked.value
        if (observedPackets == 0L) {
            return@withContext SecurityTestResult(
                testName = "فحص إعداد الحجب المحلي",
                description = "فحص الإعداد فقط؛ لم يُجرَ اتصال تجريبي من عملية التطبيق المستهدف",
                isPassed = false,
                resultDetails = "الإعدادات النشطة صحيحة، لكن لم تُرصد حزم TUN بعد. افتح التطبيق داخل ملف العمل وحاول تنفيذ طلب شبكة، ثم أعد الفحص."
            )
        }

        SecurityTestResult(
            testName = "فحص إسقاط حزم TUN المرصودة",
            description = "دليل عددي من واجهة VPN المحلية؛ ليس فحصًا لمحتوى التطبيق أو لكل حزمة على الجهاز",
            isPassed = true,
            resultDetails = if (blockedPackages.size == 1) {
                "قرأ عامل TUN وأسقط $observedPackets حزمة فعلية منذ تشغيل الخدمة، وقائمة VPN تتضمن الهدف وحده. لا يسجل هذا محتوى HTTPS أو هوية العملية داخل الحزمة."
            } else {
                "قرأ عامل TUN وأسقط $observedPackets حزمة فعلية إجمالًا. توجد ${blockedPackages.size} حزم تطبيقات في قائمة VPN؛ لا ينسب Android هذه الحزم إلى تطبيق بعينه في واجهة VpnService."
            }
        )
    }

    /**
     * Audits granted sensitive permissions for a target app.
     */
    suspend fun runPermissionAudit(packageName: String): SecurityTestResult = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }

            val reqPerms = packageInfo.requestedPermissions ?: emptyArray()
            val grantedDangerous = reqPerms.filter { permission ->
                val isRuntimeDangerous = runCatching {
                    val permissionInfo = pm.getPermissionInfo(permission, 0)
                    (permissionInfo.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE) ==
                        android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
                }.getOrDefault(false)
                isRuntimeDangerous && pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED
            }.map { it.substringAfterLast(".") }

            if (grantedDangerous.isEmpty()) {
                SecurityTestResult(
                    testName = "فحص الصلاحيات والوصول للملفات المشتركة",
                    description = "فحص الأذونات الخطرة وقت التشغيل التي تعلنها الحزمة فقط",
                    isPassed = true,
                    resultDetails = "لم يُعثر على إذن خطِر معلَن وممنوح. لا يشمل ذلك AppOps الخاصة أو وصول الملفات عبر URI أو الأذونات الخاصة في إعدادات النظام."
                )
            } else {
                SecurityTestResult(
                    testName = "فحص الصلاحيات والوصول للملفات المشتركة",
                    description = "الأذونات الخطرة المعلنة والممنوحة فعليًا في هذا الملف",
                    isPassed = false,
                    resultDetails = "أذونات خطرة ما زالت ممنوحة: [${grantedDangerous.joinToString(", ")}]. حاولت سياسة مالك الملف رفضها، لكن تحقّق من حالة Android لكل إذن."
                )
            }
        } catch (e: Exception) {
            SecurityTestResult(
                testName = "فحص الصلاحيات",
                description = "فحص سجل الصلاحيات للنظام",
                isPassed = false,
                resultDetails = "تعذر فحص الصلاحيات: ${e.message}"
            )
        }
    }

    /**
     * Documents the honest security boundaries of the Android OS.
     */
    fun getOsLimitationsDocument(): List<OsLimitationItem> {
        return listOf(
            OsLimitationItem(
                title = "استقلالية نظام التشغيل (Linux UID & Sandbox)",
                description = "يعمل كل تطبيق عادةً تحت UID مستقل؛ لا يمنح IsolateBox وصولًا لذاكرة التطبيقات أو ملفاتها الداخلية الخاصة. هذا ليس فحصًا لذاكرة الهدف، وقد تظل الملفات المشتركة أو الأذونات الممنوحة متاحة.",
                statusType = "INFO"
            ),
            OsLimitationItem(
                title = "حدود جدار الحماية VpnService (عزل الشبكة فقط)",
                description = "تُسقط الحزم التي يوجهها Android إلى TUN ما دامت واجهة VPN تعمل. يمنع always-on مع lockdown تجاوزها داخل الملف المدار عند توقفها؛ دون ذلك يكون الحجب محدودًا بحياة الخدمة. DNS المشفر ومحتوى TLS لا يُفك تشفيرهما.",
                statusType = "CAUTION"
            ),
            OsLimitationItem(
                title = "دور Shizuku (تجريد الصلاحيات الحساسة عبر ADB)",
                description = "Shizuku ليس root تلقائيًا؛ يعمل بهوية ADB أو root وفق طريقة تشغيله. IsolateBox يعرض حالته فقط ولا ينفذ أوامر shell غير موثقة. لا يحل محل ملف العمل ولا يوفر حاوية أو وصولًا عامًا لبيانات التطبيقات.",
                statusType = "SHIZUKU"
            ),
            OsLimitationItem(
                title = "ملف العمل ومستخدمو أندرويد (العزل الفعلي التام للملفات)",
                description = "ملف العمل ينشئ مستخدم Android مُدارًا وبيانات تطبيقات منفصلة. يجب تثبيت نسخة الهدف داخل الملف يدويًا؛ لا تُنسخ بيانات النسخة الشخصية، ولا يمثل ذلك عزلًا مطلقًا عن النظام أو عن المستخدم/مالك الجهاز.",
                statusType = "RECOMMENDED"
            )
        )
    }

    /**
     * Deep Tracker Radar: Scans APK declared services, receivers, and providers
     * for tracking, behavioral analytics, and telemetry SDKs.
     */
    suspend fun analyzeAppTrackers(packageName: String): com.example.model.IntrusionReport = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val knownTrackers = listOf(
            TrackerRule("Facebook Analytics & Graph", "تحليلات وتتبع سلوكي", listOf("com.facebook.appevents", "com.facebook.ads")),
            TrackerRule("Google Firebase Measurement", "قياس وتحليلات الأجهزة", listOf("com.google.android.gms.measurement", "com.google.firebase.analytics")),
            TrackerRule("AppsFlyer Attribution", "تتبع إسناد وتثبيت التطبيقات", listOf("com.appsflyer")),
            TrackerRule("Adjust SDK", "تتبع سلوك المستخدم وحملات الإعلانات", listOf("com.adjust.sdk")),
            TrackerRule("Flurry Analytics", "سجلات تتبع الجلسات", listOf("com.flurry")),
            TrackerRule("Kochava Tracker", "بصمة الجهاز ومراقبة الاستخدام", listOf("com.kochava")),
            TrackerRule("Mixpanel Analytics", "تتبع الأحداث وسلوك الشاشات", listOf("com.mixpanel")),
            TrackerRule("AppLovin / MAX", "شبكة إعلانات وتتبع تسويقي", listOf("com.applovin")),
            TrackerRule("Unity Ads / Analytics", "تتبع إعلانات ومستشعرات", listOf("com.unity3d.services", "com.unity3d.ads")),
            TrackerRule("Branch Metrics", "تتبع روابط الإسناد العميقة", listOf("io.branch")),
            TrackerRule("Criteo Advertising", "تتبع إعادة الاستهداف الإعلاني", listOf("com.criteo"))
        )

        try {
            val flags = PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }

            val components = mutableListOf<String>()
            pkgInfo.services?.forEach { components.add(it.name) }
            pkgInfo.receivers?.forEach { components.add(it.name) }
            pkgInfo.providers?.forEach { components.add(it.name) }

            val discovered = mutableListOf<com.example.model.DiscoveredTracker>()
            for (rule in knownTrackers) {
                val matched = components.any { comp ->
                    rule.patterns.any { pattern -> comp.contains(pattern, ignoreCase = true) }
                }
                if (matched) {
                    discovered.add(
                        com.example.model.DiscoveredTracker(
                            name = rule.name,
                            category = rule.category,
                            signature = rule.patterns.first(),
                            isNeutralized = false // A manifest-name match is not evidence of neutralization.
                        )
                    )
                }
            }

            val count = discovered.size
            val score = (count * 20).coerceAtMost(100)
            val level = when {
                score == 0 -> "لم تُكتشف أنماط معروفة (ليس فحصًا شاملًا)"
                score < 40 -> "عدد قليل من الأنماط المعروفة في أسماء المكونات"
                score < 70 -> "عدة أنماط أسماء معروفة؛ يلزم فحص مستقل"
                else -> "أنماط عديدة في أسماء المكونات؛ النتيجة ليست تقديرًا لاحتمال التتبع"
            }

            com.example.model.IntrusionReport(
                score = score,
                level = level,
                totalTrackersFound = count,
                trackers = discovered
            )
        } catch (e: Exception) {
            com.example.model.IntrusionReport(
                score = 0,
                level = "تعذر الفحص (${e.message})",
                totalTrackersFound = 0,
                trackers = emptyList()
            )
        }
    }

    private data class TrackerRule(
        val name: String,
        val category: String,
        val patterns: List<String>
    )

    data class OsLimitationItem(
        val title: String,
        val description: String,
        val statusType: String
    )
}
