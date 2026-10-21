package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diagnostics.SecurityAuditor
import com.example.model.FirewallLogEntry
import com.example.model.IntrusionReport
import com.example.model.SandboxedApp
import com.example.model.SecurityTestResult
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanDark
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    targetApp: SandboxedApp?,
    testResults: List<SecurityTestResult>,
    intrusionReport: IntrusionReport?,
    isTestingInProgress: Boolean,
    firewallLogs: List<FirewallLogEntry>,
    onRunTests: (String) -> Unit,
    onScanTrackers: (String) -> Unit,
    onClearLogs: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val auditor = remember { SecurityAuditor(context) }
    val limitations = remember { auditor.getOsLimitationsDocument() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("معمل الفحص ورادار التتبع", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (targetApp != null) {
                        IconButton(
                            onClick = {
                                onRunTests(targetApp.packageName)
                                onScanTrackers(targetApp.packageName)
                            },
                            enabled = !isTestingInProgress
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "إعادة الفحص")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header for Target App
            if (targetApp != null) {
                item {
                    CyberCard(borderColor = CyberCyan.copy(alpha = 0.5f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconView(packageName = targetApp.packageName, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "التطبيق المفحوص: ${targetApp.appName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = targetApp.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onRunTests(targetApp.packageName) },
                                enabled = !isTestingInProgress,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("run_diagnostics_button")
                            ) {
                                if (isTestingInProgress) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                } else {
                                    Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("فحص العزل", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = { onScanTrackers(targetApp.packageName) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberAmber.copy(alpha = 0.2f), contentColor = CyberAmber),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("scan_trackers_button")
                            ) {
                                Icon(imageVector = Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("رادار التتبع", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Tracker Radar Card (Bold Feature)
            if (intrusionReport != null) {
                item {
                    Text(
                        text = "أنماط SDK معروفة في أسماء المكونات (فحص محدود)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyberAmber
                    )
                }

                item {
                    CyberCard(borderColor = if (intrusionReport.score > 40) CyberRed.copy(alpha = 0.5f) else CyberEmerald.copy(alpha = 0.5f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "مؤشر داخلي للأنماط (ليس نسبة): ${intrusionReport.score}/100",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (intrusionReport.score > 40) CyberRed else CyberEmerald
                                )
                                Text(
                                    text = "المستوى: ${intrusionReport.level} (${intrusionReport.totalTrackersFound} أدوات مكتشفة)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            NeonStatusChip(
                                text = "كشف أسماء فقط؛ لم يثبت التعطيل",
                                color = CyberAmber
                            )
                        }

                        if (intrusionReport.trackers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                intrusionReport.trackers.forEach { tracker ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(tracker.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text(tracker.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        NeonStatusChip(text = "مطابقة اسم؛ لم يُختبر الاتصال", color = CyberAmber)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Test Results Section
            if (testResults.isNotEmpty()) {
                item {
                    Text(
                        text = "نتائج حالة الإعداد والأدلة المرصودة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                }

                items(testResults) { test ->
                    CyberCard(
                        borderColor = if (test.isPassed) CyberEmerald.copy(alpha = 0.5f) else CyberRed.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (test.isPassed) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (test.isPassed) CyberEmerald else CyberRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = test.testName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = test.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = test.resultDetails,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (test.isPassed) CyberEmerald else CyberRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Real System OS Limitations Section (Transparency & Honesty)
            item {
                Text(
                    text = "توثيق القيود البنيوية لنظام أندرويد (الشفافية الهندسية)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyberAmber
                )
            }

            items(limitations) { item ->
                CyberCard(
                    borderColor = when (item.statusType) {
                        "CAUTION" -> CyberAmber.copy(alpha = 0.4f)
                        "RECOMMENDED" -> CyberEmerald.copy(alpha = 0.4f)
                        "SHIZUKU" -> CyberCyan.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        val iconColor = when (item.statusType) {
                            "CAUTION" -> CyberAmber
                            "RECOMMENDED" -> CyberEmerald
                            "SHIZUKU" -> CyberCyan
                            else -> CyberBlue
                        }
                        Icon(
                            imageVector = when (item.statusType) {
                                "CAUTION" -> Icons.Default.Warning
                                "RECOMMENDED" -> Icons.Default.Shield
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = iconColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Live Firewall Log Section with DNS Domains
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "عينات الحزم التي التقطها VPN المحلي",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )

                    if (firewallLogs.isNotEmpty()) {
                        IconButton(onClick = onClearLogs) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "مسح السجل", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (firewallLogs.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد عينات بعد. يظهر سجل محدود للعناوين والاتصالات التي قرأها TUN، وقد يظهر اسم النطاق فقط لاستعلام DNS غير المشفر. لا تُفك حماية TLS ولا تُعرض محتويات التطبيق.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(firewallLogs, key = { it.id }) { log ->
                    FirewallLogRow(log = log)
                }
            }
        }
    }
}

@Composable
fun FirewallLogRow(log: FirewallLogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(log.timestamp) { timeFormat.format(Date(log.timestamp)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeonStatusChip(text = log.protocol, color = CyberRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.queriedDomain ?: "${log.destinationIp}:${log.destinationPort}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (log.queriedDomain != null) CyberCyan else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${log.appName} | ${log.ruleReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
