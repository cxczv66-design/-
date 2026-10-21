package com.example.ui

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IsolateBoxApp
import com.example.data.AppDatabase
import com.example.data.VaultManager
import com.example.diagnostics.SecurityAuditor
import com.example.firewall.FirewallController
import com.example.firewall.IsolateVpnService
import com.example.model.CompatibilityRating
import com.example.model.FirewallLogEntry
import com.example.model.InstalledAppItem
import com.example.model.SandboxSession
import com.example.model.SandboxedApp
import com.example.model.SecurityTestResult
import com.example.pipeline.AppLaunchPipeline
import com.example.pipeline.PipelineStepStatus
import com.example.shizuku.ShizukuManager
import com.example.shizuku.ShizukuState
import com.example.workprofile.WorkProfileHelper
import com.example.workprofile.WorkProfileStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IsolateBoxViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val appLaunchPipeline = AppLaunchPipeline(application)
    private val securityAuditor = SecurityAuditor(application)
    private val vaultManager = (application as IsolateBoxApp).vaultManager

    val sandboxedApps = db.sandboxedAppDao().getAllSandboxedApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession = db.sandboxSessionDao().getActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentFirewallLogs = db.firewallLogDao().getRecentLogs(60)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val firewallActive = IsolateVpnService.isRunning
    val totalBlockedPackets = IsolateVpnService.totalBlocked
    val shizukuState = ShizukuManager.shizukuState

    private val _workProfileStatus = MutableStateFlow(WorkProfileHelper.getWorkProfileStatus(application))
    val workProfileStatus = _workProfileStatus.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val filteredInstalledApps: StateFlow<List<InstalledAppItem>> = combine(
        _installedApps,
        _searchQuery,
        sandboxedApps
    ) { apps, query, sandboxed ->
        val sandboxedPkgs = sandboxed.map { it.packageName }.toSet()
        val mapped = apps.map { it.copy(isAlreadySandboxed = it.packageName in sandboxedPkgs) }
        if (query.isBlank()) {
            mapped
        } else {
            mapped.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pipelineStatus = appLaunchPipeline.status

    private val _testResults = MutableStateFlow<List<SecurityTestResult>>(emptyList())
    val testResults = _testResults.asStateFlow()

    private val _vaultItems = MutableStateFlow<List<VaultManager.VaultItem>>(emptyList())
    val vaultItems = _vaultItems.asStateFlow()

    private val _selectedApp = MutableStateFlow<SandboxedApp?>(null)
    val selectedApp = _selectedApp.asStateFlow()

    private val _isTestingInProgress = MutableStateFlow(false)
    val isTestingInProgress = _isTestingInProgress.asStateFlow()

    init {
        loadInstalledApps()
        refreshVaultItems()
        refreshWorkProfileStatus()
    }

    fun selectApp(app: SandboxedApp?) {
        _selectedApp.value = app
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshWorkProfileStatus() {
        _workProfileStatus.value = WorkProfileHelper.getWorkProfileStatus(getApplication())
    }

    fun refreshVaultItems() {
        _vaultItems.value = vaultManager.listVaultItems()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val context: Context = getApplication()
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val appItems = mutableListOf<InstalledAppItem>()
            for (appInfo in installed) {
                // Ignore self
                if (appInfo.packageName == context.packageName) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    appInfo.packageName
                }

                var versionName = "1.0"
                var requestedPerms = emptyList<String>()
                var grantedPerms = emptyList<String>()

                try {
                    val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(appInfo.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                    }
                    versionName = pkgInfo.versionName ?: "1.0"
                    val req = pkgInfo.requestedPermissions ?: emptyArray()
                    val flags = pkgInfo.requestedPermissionsFlags ?: intArrayOf()
                    requestedPerms = req.toList()

                    val granted = mutableListOf<String>()
                    for (i in req.indices) {
                        if (i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                            granted.add(req[i])
                        }
                    }
                    grantedPerms = granted
                } catch (ignored: Exception) {}

                // Evaluate compatibility
                val rating = when {
                    isSystem -> CompatibilityRating.LIMITED
                    grantedPerms.any { it.contains("CAMERA") || it.contains("RECORD_AUDIO") || it.contains("MEDIA") } -> CompatibilityRating.GOOD
                    else -> CompatibilityRating.EXCELLENT
                }

                val notice = when (rating) {
                    CompatibilityRating.LIMITED -> "حزمة نظام؛ لا تضفها قبل فهم أثر تعليقها وحجب شبكتها"
                    CompatibilityRating.GOOD -> "يطلب أذونات حساسة؛ قد تتعطل ميزات بعد تطبيق سياسة الرفض"
                    CompatibilityRating.EXCELLENT -> "لم تُرصد الأذونات المحددة؛ هذا ليس اختبار توافق أو ضمان أمان"
                }

                appItems.add(
                    InstalledAppItem(
                        packageName = appInfo.packageName,
                        appName = label,
                        versionName = versionName,
                        isSystemApp = isSystem,
                        requestedPermissions = requestedPerms,
                        grantedPermissions = grantedPerms,
                        compatibilityRating = rating,
                        compatibilityNotice = notice
                    )
                )
            }

            // Sort: non-system first, then alphabetical
            val sorted = appItems.sortedWith(
                compareBy<InstalledAppItem> { it.isSystemApp }
                    .thenBy { it.appName.lowercase() }
            )

            _installedApps.value = sorted
        }
    }

    fun addAppToSandbox(item: InstalledAppItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (item.isSystemApp) return@launch
            val sandboxed = SandboxedApp(
                packageName = item.packageName,
                appName = item.appName,
                isEnabled = true,
                blockAllNetwork = true,
                blockIpv4 = true,
                blockIpv6 = true,
                blockDns = true,
                restrictSharedStorage = true,
                restrictMediaAccess = true,
                useEncryptedVaultOnly = true,
                revokeLocation = true,
                revokeCameraMic = true,
                revokeContactsCalendar = true,
                blockBackgroundRun = true
            )
            db.sandboxedAppDao().insertApp(sandboxed)
            WorkProfileHelper.applyProfileDataRestrictions(context = getApplication())
            WorkProfileHelper.denySensitiveRuntimePermissions(getApplication(), item.packageName)
            FirewallController.reloadRules(getApplication())
        }
    }

    fun removeAppFromSandbox(app: SandboxedApp) {
        viewModelScope.launch(Dispatchers.IO) {
            db.sandboxedAppDao().deleteApp(app)
            FirewallController.reloadRules(getApplication())
            if (_selectedApp.value?.packageName == app.packageName) {
                _selectedApp.value = null
            }
        }
    }

    fun updateAppSettings(app: SandboxedApp) {
        viewModelScope.launch(Dispatchers.IO) {
            db.sandboxedAppDao().updateApp(app)
            _selectedApp.value = app
            FirewallController.reloadRules(getApplication())
        }
    }

    fun launchAppInSandbox(app: SandboxedApp) {
        viewModelScope.launch {
            appLaunchPipeline.launchAppInSandbox(app)
        }
    }

    fun stopApp(packageName: String) {
        viewModelScope.launch {
            appLaunchPipeline.stopApp(packageName)
        }
    }

    fun resetPipelineStatus() {
        appLaunchPipeline.resetStatus()
    }

    fun toggleFirewall() {
        val context: Context = getApplication()
        if (firewallActive.value) {
            if (WorkProfileHelper.isLockdownEnabled(context)) return
            FirewallController.stopFirewall(context)
        } else {
            FirewallController.startFirewall(context)
        }
    }

    fun requestShizukuPermission() {
        ShizukuManager.requestPermission()
    }

    fun runDiagnostics(packageName: String) {
        viewModelScope.launch {
            _isTestingInProgress.value = true
            val results = mutableListOf<SecurityTestResult>()

            // 1. Network & DNS blockade test
            results.add(securityAuditor.runNetworkBlockadeTest(packageName))

            // 2. Permission and shared media test
            results.add(securityAuditor.runPermissionAudit(packageName))

            _testResults.value = results
            _isTestingInProgress.value = false
        }
    }

    fun saveVaultSecret(name: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultManager.storeEncryptedData(name, content)
            refreshVaultItems()
        }
    }

    fun deleteVaultItem(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultManager.deleteVaultItem(name)
            refreshVaultItems()
        }
    }

    fun readVaultItem(name: String): String? {
        return vaultManager.readEncryptedData(name)
    }

    private val _intrusionReport = MutableStateFlow<com.example.model.IntrusionReport?>(null)
    val intrusionReport = _intrusionReport.asStateFlow()

    fun runTrackerAnalysis(packageName: String) {
        viewModelScope.launch {
            _intrusionReport.value = securityAuditor.analyzeAppTrackers(packageName)
        }
    }

    fun clearFirewallLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            db.firewallLogDao().clearLogs()
        }
    }
}
