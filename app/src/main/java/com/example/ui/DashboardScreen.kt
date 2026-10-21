package com.example.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SandboxSession
import com.example.model.SandboxedApp
import androidx.compose.foundation.BorderStroke
import com.example.shizuku.ShizukuState
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanDark
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberEmeraldDark
import com.example.ui.theme.CyberRed
import com.example.workprofile.WorkProfileStatus

@Composable
fun DashboardScreen(
    sandboxedApps: List<SandboxedApp>,
    activeSession: SandboxSession?,
    firewallActive: Boolean,
    totalBlockedPackets: Long,
    shizukuState: ShizukuState,
    workProfileStatus: WorkProfileStatus,
    onToggleFirewall: () -> Unit,
    onRequestShizuku: () -> Unit,
    onAddAppClick: () -> Unit,
    onLaunchApp: (SandboxedApp) -> Unit,
    onStopApp: (String) -> Unit,
    onSettingsClick: (SandboxedApp) -> Unit,
    onDiagnosticsClick: (SandboxedApp) -> Unit,
    onOpenVaultClick: () -> Unit,
    onSetupWorkProfile: () -> Unit,
    onShowFloatingPanel: () -> Unit,
    onStopFloatingPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "IsolateBox",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = CyberCyan
                        )
                        Text(
                            text = "بيئة التشغيل المعزولة وجدار الحماية المحلي",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onOpenVaultClick,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("vault_quick_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "الخزنة المشفرة",
                            tint = CyberCyan
                        )
                    }
                }
            }

            // Active Session Alert if running
            if (activeSession != null) {
                item {
                    ActiveSessionBanner(
                        session = activeSession,
                        totalBlocked = totalBlockedPackets,
                        vpnActive = firewallActive,
                        lockdownActive = workProfileStatus.networkLockdownEnabled,
                        onStopClick = { onStopApp(activeSession.packageName) }
                    )
                }
            }

            // System Status Triple Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "محركات الحماية والعزل الأساسية",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // 1. Local Firewall (VpnService)
                    CyberCard(
                        borderColor = if (firewallActive) CyberEmerald.copy(alpha = 0.5f) else CyberAmber.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (firewallActive) CyberEmerald.copy(alpha = 0.15f) else CyberAmber.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = if (firewallActive) CyberEmerald else CyberAmber
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "جدار الحماية المحلي (VpnService)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            firewallActive && workProfileStatus.networkLockdownEnabled -> "VPN دائم مع قفل المرور؛ ${totalBlockedPackets} حزمة قرأها TUN"
                                            firewallActive -> "حجب محلي ما دامت الخدمة فعالة؛ ${totalBlockedPackets} حزمة قرأها TUN"
                                            workProfileStatus.isProfileOwner && workProfileStatus.networkLockdownEnabled -> "TUN غير نشط؛ قفل Android ما زال يمنع المرور غير المار عبر VPN. قد تتأثر تطبيقات الملف الأخرى."
                                            else -> "متوقف: لا يوجد حجب نشط الآن"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (workProfileStatus.isProfileOwner && workProfileStatus.networkLockdownEnabled) {
                                NeonStatusChip(
                                    text = if (firewallActive) "قفل دائم" else "قفل DPC؛ TUN غير نشط",
                                    color = if (firewallActive) CyberEmerald else CyberAmber
                                )
                            } else {
                                Button(
                                    onClick = onToggleFirewall,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (firewallActive) CyberEmerald.copy(alpha = 0.2f) else CyberCyanDark,
                                        contentColor = if (firewallActive) CyberEmerald else Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("firewall_toggle_button")
                                ) {
                                    Text(if (firewallActive) "إيقاف" else "تشغيل")
                                }
                            }
                        }
                    }

                    // 2. Shizuku Service Card
                    CyberCard(
                        borderColor = when (shizukuState) {
                            ShizukuState.CONNECTED -> CyberEmerald.copy(alpha = 0.5f)
                            ShizukuState.PERMISSION_REQUIRED -> CyberAmber.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when (shizukuState) {
                                                ShizukuState.CONNECTED -> CyberEmerald.copy(alpha = 0.15f)
                                                ShizukuState.PERMISSION_REQUIRED -> CyberAmber.copy(alpha = 0.15f)
                                                else -> Color.Gray.copy(alpha = 0.15f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = when (shizukuState) {
                                            ShizukuState.CONNECTED -> CyberEmerald
                                            ShizukuState.PERMISSION_REQUIRED -> CyberAmber
                                            else -> Color.Gray
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "حالة Shizuku (اختياري، دون أوامر تلقائية)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when (shizukuState) {
                                            ShizukuState.CONNECTED -> "Binder متصل ومصرح؛ لا ينفذ التطبيق أوامر shell عبره"
                                            ShizukuState.PERMISSION_REQUIRED -> "خدمة Shizuku نشطة؛ يلزم إذن المستخدم قبل أي استخدام"
                                            ShizukuState.SERVICE_NOT_RUNNING -> "غير متصل؛ VPN وملف العمل لا يعتمدان عليه"
                                            ShizukuState.UNSUPPORTED -> "غير مدعوم على هذه البيئة"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (shizukuState == ShizukuState.PERMISSION_REQUIRED) {
                                OutlinedButton(
                                    onClick = onRequestShizuku,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("request_shizuku_button")
                                ) {
                                    Text("طلب الإذن")
                                }
                            } else if (shizukuState == ShizukuState.CONNECTED) {
                                NeonStatusChip(text = "جاهز", color = CyberEmerald)
                            }
                        }
                    }

                    // 3. Work Profile / Managed User Card
                    CyberCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderShared,
                                    contentDescription = null,
                                    tint = CyberBlue
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ملف العمل ومستخدمو أندرويد (عزل الملفات)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = workProfileStatus.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (workProfileStatus.isProfileOwner && workProfileStatus.networkLockdownEnabled) {
                                NeonStatusChip(text = "Lockdown فعال", color = CyberEmerald)
                            } else if (workProfileStatus.canProvisionManagedProfile) {
                                Button(
                                    onClick = onSetupWorkProfile,
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark),
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text("إعداد ملف العمل", color = Color.White) }
                            } else {
                                NeonStatusChip(
                                    text = when {
                                        workProfileStatus.isProfileOwner -> "تهيئة مطلوبة"
                                        workProfileStatus.hasWorkProfile -> "افتح نسخة الحقيبة"
                                        else -> "غير متاح"
                                    },
                                    color = if (workProfileStatus.hasWorkProfile) CyberAmber else CyberBlue
                                )
                            }
                        }
                    }
                }
            }

            item {
                CyberCard(borderColor = CyberCyan.copy(alpha = 0.45f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(CyberCyan.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = CyberCyan)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "لوحة عائمة تجريبية",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "فقاعة قابلة للسحب ومظهر متحرك فوق التطبيقات. الأزرار شكلية فقط؛ لا تتحكم بلعبة ولا تقرأ ذاكرتها.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onShowFloatingPanel,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("floating_panel_button")
                        ) {
                            Text("إظهار اللوحة", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = onStopFloatingPanel,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("floating_panel_stop_button")
                        ) {
                            Text("إيقاف")
                        }
                    }
                }
            }

            // Sandboxed Apps Header & List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تطبيقات مسجلة في IsolateBox (${sandboxedApps.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = onAddAppClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_app_header_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة تطبيق", color = Color.White)
                    }
                }
            }

            if (sandboxedApps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "لم تقم بإضافة أي تطبيق إلى البيئة بعد",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "أضف التطبيقات المثبتة في الملف الحالي فقط. العزل يتطلب فتح نسخة IsolateBox ذات رمز الحقيبة داخل ملف العمل؛ النسخة الشخصية لا تعزل ملفات التطبيقات.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = onAddAppClick,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("empty_add_app_button")
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إضافة تطبيق الآن", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                items(sandboxedApps, key = { it.packageName }) { app ->
                    SandboxedAppItemCard(
                        app = app,
                        isRunning = activeSession?.packageName == app.packageName,
                        onLaunch = { onLaunchApp(app) },
                        onStop = { onStopApp(app.packageName) },
                        onSettings = { onSettingsClick(app) },
                        onDiagnostics = { onDiagnosticsClick(app) }
                    )
                }
            }
        }

        // Floating Action Button
        ExtendedFloatingActionButton(
            onClick = onAddAppClick,
            icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black) },
            text = { Text("إضافة تطبيق", color = Color.Black, fontWeight = FontWeight.Bold) },
            containerColor = CyberCyan,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("fab_add_app")
        )
    }
}

@Composable
fun SandboxedAppItemCard(
    app: SandboxedApp,
    isRunning: Boolean,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit,
    onDiagnostics: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sandboxed_app_${app.packageName}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.2.dp,
            if (isRunning) CyberCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconView(packageName = app.packageName, modifier = Modifier.size(50.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isRunning) {
                            Spacer(modifier = Modifier.width(8.dp))
                            NeonStatusChip(text = "جلسة مفتوحة", color = CyberEmerald)
                        }
                    }
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDiagnostics,
                    modifier = Modifier.testTag("diagnostics_btn_${app.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.GppGood,
                        contentDescription = "فحص الأمان",
                        tint = CyberCyan
                    )
                }

                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.testTag("settings_btn_${app.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "إعدادات العزل",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (app.blockAllNetwork) {
                    NeonStatusChip(text = "قاعدة: حجب الشبكة", color = CyberRed)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberEmeraldDark,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("launch_button_${app.packageName}")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "تشغيل التطبيق", fontWeight = FontWeight.Bold)
                }

                if (isRunning) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("stop_button_${app.packageName}")
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "تعليق", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
