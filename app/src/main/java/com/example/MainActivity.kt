package com.example

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import com.example.ui.AddAppBottomSheet
import com.example.ui.AppSettingsScreen
import com.example.ui.DashboardScreen
import com.example.ui.DiagnosticsScreen
import com.example.ui.IsolateBoxViewModel
import com.example.ui.PipelineExecutionDialog
import com.example.ui.VaultScreen
import com.example.ui.theme.MyApplicationTheme

enum class Screen {
    DASHBOARD,
    APP_SETTINGS,
    DIAGNOSTICS,
    VAULT
}

class MainActivity : ComponentActivity() {

    private val viewModel: IsolateBoxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
                var showAddSheet by remember { mutableStateOf(false) }

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

                // Launcher for VpnService preparation dialog
                val vpnLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        FirewallController.startFirewall(this)
                        Toast.makeText(this, "تم تفعيل جدار الحماية بنجاح", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "يلزم إذن VPN لحجب الاتصالات", Toast.LENGTH_SHORT).show()
                    }
                }

                fun checkAndStartFirewall() {
                    val prepareIntent = FirewallController.getPrepareIntent(this)
                    if (prepareIntent != null) {
                        vpnLauncher.launch(prepareIntent)
                    } else {
                        viewModel.toggleFirewall()
                    }
                }

                // Handle system back navigation
                if (currentScreen != Screen.DASHBOARD) {
                    BackHandler {
                        currentScreen = Screen.DASHBOARD
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        Screen.DASHBOARD -> {
                            DashboardScreen(
                                sandboxedApps = sandboxedApps,
                                activeSession = activeSession,
                                firewallActive = firewallActive,
                                totalBlockedPackets = totalBlocked,
                                shizukuState = shizukuState,
                                workProfileStatus = workProfileStatus,
                                onToggleFirewall = { checkAndStartFirewall() },
                                onRequestShizuku = { viewModel.requestShizukuPermission() },
                                onAddAppClick = {
                                    viewModel.loadInstalledApps()
                                    showAddSheet = true
                                },
                                onLaunchApp = { app ->
                                    val prepIntent = FirewallController.getPrepareIntent(this)
                                    if (prepIntent != null) {
                                        vpnLauncher.launch(prepIntent)
                                    } else {
                                        viewModel.launchAppInSandbox(app)
                                    }
                                },
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
                                onOpenVaultClick = {
                                    currentScreen = Screen.VAULT
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        Screen.APP_SETTINGS -> {
                            val target = selectedApp
                            if (target != null) {
                                AppSettingsScreen(
                                    app = target,
                                    onUpdateApp = { updated -> viewModel.updateAppSettings(updated) },
                                    onDeleteApp = { del -> viewModel.removeAppFromSandbox(del) },
                                    onLaunchApp = { appToLaunch ->
                                        val prepIntent = FirewallController.getPrepareIntent(this)
                                        if (prepIntent != null) {
                                            vpnLauncher.launch(prepIntent)
                                        } else {
                                            viewModel.launchAppInSandbox(appToLaunch)
                                        }
                                    },
                                    onDiagnosticsClick = { appToTest ->
                                        viewModel.runDiagnostics(appToTest.packageName)
                                        viewModel.runTrackerAnalysis(appToTest.packageName)
                                        currentScreen = Screen.DIAGNOSTICS
                                    },
                                    onToggleFreeze = { appToFreeze -> viewModel.toggleFreeze(appToFreeze) },
                                    onPanicWipe = { pkgToWipe ->
                                        viewModel.panicWipe(pkgToWipe)
                                        Toast.makeText(this, "تم تنفيذ التطهير الفوري وتجميد الحزمة", Toast.LENGTH_LONG).show()
                                    },
                                    onBack = { currentScreen = Screen.DASHBOARD }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
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

                        Screen.VAULT -> {
                            VaultScreen(
                                vaultItems = vaultItems,
                                onSaveSecret = { name, content -> viewModel.saveVaultSecret(name, content) },
                                onDeleteSecret = { name -> viewModel.deleteVaultItem(name) },
                                onReadSecret = { name -> viewModel.readVaultItem(name) },
                                onBack = { currentScreen = Screen.DASHBOARD }
                            )
                        }
                    }

                    // Add App Bottom Sheet
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

                    // Pre-flight execution pipeline dialog
                    PipelineExecutionDialog(
                        status = pipelineStatus,
                        onDismiss = { viewModel.resetPipelineStatus() }
                    )
                }
            }
        }
    }
}
