package com.example

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.firewall.FirewallController
import com.example.model.SandboxedApp
import com.example.overlay.FloatingPanelService
import com.example.ui.AddAppBottomSheet
import com.example.ui.AppSettingsScreen
import com.example.ui.DashboardScreen
import com.example.ui.DiagnosticsScreen
import com.example.ui.IsolateBoxViewModel
import com.example.ui.PipelineExecutionDialog
import com.example.ui.VaultScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.workprofile.WorkProfileHelper

private enum class Screen { DASHBOARD, APP_SETTINGS, DIAGNOSTICS, VAULT }

class MainActivity : ComponentActivity() {
    private val viewModel: IsolateBoxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
                var showAddSheet by remember { mutableStateOf(false) }
                var pendingLaunch by remember { mutableStateOf<SandboxedApp?>(null) }

                val sandboxedApps by viewModel.sandboxedApps.collectAsState()
                val activeSession by viewModel.activeSession.collectAsState()
                val firewallActive by viewModel.firewallActive.collectAsState()
                val totalBlocked by viewModel.totalBlockedPackets.collectAsState()
                val shizukuState by viewModel.shizukuState.collectAsState()
                val workProfileStatus by viewModel.workProfileStatus.collectAsState()
                val filteredInstalledApps by viewModel.filteredInstalledApps.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                val pipelineStatus by viewModel.pipelineStatus.collectAsState()
                val testResults by viewModel.testResults.collectAsState()
                val isTestingInProgress by viewModel.isTestingInProgress.collectAsState()
                val recentLogs by viewModel.recentFirewallLogs.collectAsState()
                val vaultItems by viewModel.vaultItems.collectAsState()
                val intrusionReport by viewModel.intrusionReport.collectAsState()
                val selectedApp by viewModel.selectedApp.collectAsState()

                val vpnLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val target = pendingLaunch
                    pendingLaunch = null
                    if (result.resultCode == Activity.RESULT_OK) {
                        FirewallController.startFirewall(this)
                        if (target != null) viewModel.launchAppInSandbox(target)
                        else Toast.makeText(this, "بدأت محاولة تشغيل VPN المحلي", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "لم يُمنح إذن VPN؛ لم يتم تشغيل التطبيق المستهدف", Toast.LENGTH_LONG).show()
                    }
                }

                val profileProvisioningLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    viewModel.refreshWorkProfileStatus()
                    val profileExists = WorkProfileHelper.getWorkProfileStatus(this).hasWorkProfile
                    if (profileExists) {
                        Toast.makeText(
                            this,
                            "أُنشئ ملف العمل. افتح نسخة IsolateBox ذات رمز الحقيبة من مشغل التطبيقات، وثبّت التطبيقات المختارة داخل الملف.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val message = if (result.resultCode == Activity.RESULT_OK) "عاد معالج Android لكن لم يظهر ملف مُدار؛ تحقق من إعداد الجهاز." else "لم يكتمل إعداد ملف العمل. لا تُشغّل التطبيقات من النسخة الشخصية بوصفها معزولة."
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }

                val overlayPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    if (Settings.canDrawOverlays(this)) {
                        ContextCompat.startForegroundService(
                            this,
                            Intent(this, FloatingPanelService::class.java).setAction(FloatingPanelService.ACTION_SHOW)
                        )
                    } else {
                        Toast.makeText(this, "لم يُمنح إذن الظهور فوق التطبيقات؛ لم تبدأ اللوحة.", Toast.LENGTH_LONG).show()
                    }
                }

                fun showFloatingDemoPanel() {
                    if (Settings.canDrawOverlays(this)) {
                        ContextCompat.startForegroundService(
                            this,
                            Intent(this, FloatingPanelService::class.java).setAction(FloatingPanelService.ACTION_SHOW)
                        )
                    } else {
                        overlayPermissionLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                }

                fun stopFloatingDemoPanel() {
                    startService(
                        Intent(this, FloatingPanelService::class.java).setAction(FloatingPanelService.ACTION_STOP)
                    )
                }

                fun launchTarget(app: SandboxedApp) {
                    if (!WorkProfileHelper.isCurrentProfileOwner(this)) {
                        Toast.makeText(
                            this,
                            "هذه النسخة ليست مالك ملف العمل. أنشئ الملف وافتح نسخة IsolateBox ذات رمز الحقيبة قبل إضافة/تشغيل التطبيقات.",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                    val prepareIntent: Intent? = VpnService.prepare(this)
                    if (prepareIntent != null) {
                        pendingLaunch = app
                        vpnLauncher.launch(prepareIntent)
                    } else {
                        viewModel.launchAppInSandbox(app)
                    }
                }

                fun checkAndStartFirewall() {
                    if (firewallActive) {
                        if (WorkProfileHelper.isCurrentProfileOwner(this)) {
                            Toast.makeText(this, "VPN دائم ويديره DPC؛ لا يمكن إيقافه من لوحة التطبيق.", Toast.LENGTH_LONG).show()
                            return
                        }
                        viewModel.toggleFirewall()
                        return
                    }
                    if (WorkProfileHelper.isCurrentProfileOwner(this) && WorkProfileHelper.isLockdownEnabled(this)) {
                        Toast.makeText(this, "قفل المرور مُدار من ملف العمل؛ شغّل الهدف عبر خط العزل لإعادة إعداد TUN.", Toast.LENGTH_LONG).show()
                        return
                    }
                    val prepareIntent = FirewallController.getPrepareIntent(this)
                    if (prepareIntent != null) vpnLauncher.launch(prepareIntent)
                    else viewModel.toggleFirewall()
                }

                if (currentScreen != Screen.DASHBOARD) {
                    BackHandler { currentScreen = Screen.DASHBOARD }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        Screen.DASHBOARD -> DashboardScreen(
                            sandboxedApps = sandboxedApps,
                            activeSession = activeSession,
                            firewallActive = firewallActive,
                            totalBlockedPackets = totalBlocked,
                            shizukuState = shizukuState,
                            workProfileStatus = workProfileStatus,
                            onToggleFirewall = { checkAndStartFirewall() },
                            onRequestShizuku = { viewModel.requestShizukuPermission() },
                            onAddAppClick = {
                                if (!WorkProfileHelper.isCurrentProfileOwner(this)) {
                                    Toast.makeText(this, "افتح نسخة IsolateBox داخل ملف العمل أولًا؛ لا تُضاف تطبيقات الملف الشخصي إلى حاوية افتراضية.", Toast.LENGTH_LONG).show()
                                } else {
                                    viewModel.loadInstalledApps()
                                    showAddSheet = true
                                }
                            },
                            onLaunchApp = { app -> launchTarget(app) },
                            onStopApp = { pkg -> viewModel.stopApp(pkg) },
                            onSettingsClick = { app ->
                                viewModel.selectApp(app)
                                viewModel.runTrackerAnalysis(app.packageName)
                                currentScreen = Screen.APP_SETTINGS
                            },
                            onDiagnosticsClick = { app ->
                                viewModel.selectApp(app)
                                viewModel.runDiagnostics(app.packageName)
                                viewModel.runTrackerAnalysis(app.packageName)
                                currentScreen = Screen.DIAGNOSTICS
                            },
                            onOpenVaultClick = { currentScreen = Screen.VAULT },
                            onSetupWorkProfile = {
                                val intent = WorkProfileHelper.createProvisioningIntent(this)
                                if (intent != null) profileProvisioningLauncher.launch(intent)
                                else Toast.makeText(
                                    this,
                                    "تعذر بدء التوفير: الجهاز لا يدعم ملفًا مُدارًا أو يوجد ملف عمل قائم. لن يُستبدل الملف القائم تلقائيًا.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onShowFloatingPanel = { showFloatingDemoPanel() },
                            onStopFloatingPanel = { stopFloatingDemoPanel() },
                            modifier = Modifier.padding(innerPadding)
                        )

                        Screen.APP_SETTINGS -> {
                            val target = selectedApp
                            if (target != null) {
                                AppSettingsScreen(
                                    app = target,
                                    onDeleteApp = { app -> viewModel.removeAppFromSandbox(app) },
                                    onLaunchApp = { app -> launchTarget(app) },
                                    onDiagnosticsClick = { app ->
                                        viewModel.runDiagnostics(app.packageName)
                                        viewModel.runTrackerAnalysis(app.packageName)
                                        currentScreen = Screen.DIAGNOSTICS
                                    },
                                    onBack = { currentScreen = Screen.DASHBOARD }
                                )
                            } else currentScreen = Screen.DASHBOARD
                        }

                        Screen.DIAGNOSTICS -> {
                            val diagTarget = selectedApp ?: sandboxedApps.firstOrNull()
                            DiagnosticsScreen(
                                targetApp = diagTarget,
                                testResults = testResults,
                                intrusionReport = intrusionReport,
                                isTestingInProgress = isTestingInProgress,
                                firewallLogs = recentLogs,
                                onRunTests = { pkg -> viewModel.runDiagnostics(pkg) },
                                onScanTrackers = { pkg -> viewModel.runTrackerAnalysis(pkg) },
                                onClearLogs = { viewModel.clearFirewallLogs() },
                                onBack = { currentScreen = Screen.DASHBOARD }
                            )
                        }

                        Screen.VAULT -> VaultScreen(
                            vaultItems = vaultItems,
                            onSaveSecret = { name, content -> viewModel.saveVaultSecret(name, content) },
                            onDeleteSecret = { name -> viewModel.deleteVaultItem(name) },
                            onReadSecret = { name -> viewModel.readVaultItem(name) },
                            onBack = { currentScreen = Screen.DASHBOARD }
                        )
                    }

                    if (showAddSheet) {
                        AddAppBottomSheet(
                            installedApps = filteredInstalledApps,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            onAppSelected = { item ->
                                viewModel.addAppToSandbox(item)
                                showAddSheet = false
                            },
                            onDismiss = { showAddSheet = false }
                        )
                    }

                    PipelineExecutionDialog(
                        status = pipelineStatus,
                        onDismiss = { viewModel.resetPipelineStatus() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshWorkProfileStatus()
    }
}
