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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.SandboxedApp
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanDark
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberEmeraldDark
import com.example.ui.theme.CyberRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    app: SandboxedApp,
    onUpdateApp: (SandboxedApp) -> Unit,
    onDeleteApp: (SandboxedApp) -> Unit,
    onLaunchApp: (SandboxedApp) -> Unit,
    onDiagnosticsClick: (SandboxedApp) -> Unit,
    onToggleFreeze: (SandboxedApp) -> Unit,
    onPanicWipe: (String) -> Unit,
    onBack: () -> Unit
) {
    var currentApp by remember(app) { mutableStateOf(app) }
    var showPanicDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات العزل المتقدمة والصارمة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
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
            // Header card
            item {
                CyberCard(borderColor = CyberCyan.copy(alpha = 0.5f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconView(packageName = currentApp.packageName, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentApp.appName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = currentApp.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("حالة السبات", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            NeonStatusChip(
                                text = if (currentApp.isFrozen) "مجمد تماماً (Zero-Trace)" else "جاهز للتشغيل",
                                color = if (currentApp.isFrozen) CyberCyan else CyberEmerald
                            )
                        }

                        Button(
                            onClick = {
                                onToggleFreeze(currentApp)
                                currentApp = currentApp.copy(isFrozen = !currentApp.isFrozen)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentApp.isFrozen) CyberCyanDark else CyberAmber.copy(alpha = 0.2f),
                                contentColor = if (currentApp.isFrozen) Color.White else CyberAmber
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("freeze_toggle_btn")
                        ) {
                            Icon(imageVector = Icons.Default.AcUnit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (currentApp.isFrozen) "إلغاء التجميد" else "تجميد التطبيق الآن")
                        }
                    }
                }
            }

            // Bold Feature 1: Zero-Trace Hibernation & Auto-Freeze
            item {
                Text(
                    text = "وضع التجميد والسبات القسري (Zero-Trace Hibernation)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )
            }

            item {
                CyberCard(borderColor = CyberCyan.copy(alpha = 0.35f)) {
                    SettingToggleRow(
                        title = "التجميد التلقائي عند إغلاق التطبيق",
                        description = "يقوم بإخفاء التطبيق فورياً وتعطيله في النظام لمنع أي استيقاظ بالخلفية أو فحص alarms",
                        checked = currentApp.autoFreezeOnExit,
                        onCheckedChange = { checked ->
                            val updated = currentApp.copy(autoFreezeOnExit = checked)
                            currentApp = updated
                            onUpdateApp(updated)
                        }
                    )
                }
            }

            // Bold Feature 2: Deep AppOps Restrictor
            item {
                Text(
                    text = "خنق الصلاحيات العميقة والحافظة (Deep AppOps)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyberAmber
                )
            }

            item {
                CyberCard {
                    SettingToggleRow(
                        title = "حظر التلصص على الحافظة (Clipboard Snooping)",
                        description = "منع التطبيق من قراءة ما تم نسخه في ذاكرة الهاتف تلقائياً",
                        checked = currentApp.blockClipboardSnooping,
                        onCheckedChange = { checked ->
                            val updated = currentApp.copy(blockClipboardSnooping = checked)
                            currentApp = updated
                            onUpdateApp(updated)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                    SettingToggleRow(
                        title = "حظر إبقاء المعالج نشطاً (WakeLock Neutering)",
                        description = "منع التطبيق من استنزاف البطارية أو تشغيل مهام خفية بعد خروجك",
                        checked = currentApp.blockWakeLock,
                        onCheckedChange = { checked ->
                            val updated = currentApp.copy(blockWakeLock = checked)
                            currentApp = updated
                            onUpdateApp(updated)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                    SettingToggleRow(
                        title = "خنق تام لعمليات الخلفية (Kill Phantom Services)",
                        description = "إلغاء أذونات START_FOREGROUND و RUN_IN_BACKGROUND عبر النظام",
                        checked = currentApp.blockBackgroundRun,
                        onCheckedChange = { checked ->
                            val updated = currentApp.copy(blockBackgroundRun = checked)
                            currentApp = updated
                            onUpdateApp(updated)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                    SettingToggleRow(
                        title = "عزل مستشعرات الحركة والنشاط البدني",
                        description = "حظر التعرف على النشاط والخطوات ومستشعرات التسارع الجسدي",
                        checked = currentApp.blockSensors,
                        onCheckedChange = { checked ->
                            val updated = currentApp.copy(blockSensors = checked)
                            currentApp = updated
                            onUpdateApp(updated)
                        }
                    )
                }
            }

            // Section: Network Isolation
            item {
                Text(
                    text = "عزل الشبكة والإنترنت (جدار الحماية VpnService)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )
            }

            item {
                CyberCard {
                    SettingToggleRow(
                        title = "منع جميع الاتصالات الخارجية (Blackhole Sink)",
                        description = "حظر كافة حزم TCP وUDP الصادرة من التطبيق محلياً",
                        checked = currentApp.blockAllNetwork,
                        onCheckedChange = { checked ->
                            val updated = currentApp.copy(blockAllNetwork = checked)
                            currentApp = updated
                            onUpdateApp(updated)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                    SettingToggleRow(
                        title = "حظر استعلامات DNS المنفصلة (DNS Sinkhole)",
                        description = "اعتراض طلبات المنفذ 53 واستخراج النطاقات وإسقاطها لمنع التتبع",
                        checked = currentApp.blockDns,
                        onCheckedChange = { checked ->
                            val updated = currentApp.copy(blockDns = checked)
                            currentApp = updated
                            onUpdateApp(updated)
                        }
                    )
                }
            }

            // Bold Feature 3: Emergency Panic Wipe Button Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberRed.copy(alpha = 0.12f)),
                    border = BorderStroke(1.2.dp, CyberRed.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = CyberRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "زر الطوارئ والتطهير الشامل (Panic Wipe)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CyberRed
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "في حال الاشتباه بأي نشاط مريب: ينهي عمليات التطبيق فورياً، ويمسح الذاكرة المؤقتة، ويجمد الحزمة تماماً لمنع أي أثر.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { showPanicDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("panic_wipe_button")
                        ) {
                            Icon(imageVector = Icons.Default.Dangerous, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تطهير الجلسة وتجميد التطبيق فورياً", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Standard Action Buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onLaunchApp(currentApp) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberEmeraldDark),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("app_settings_launch_button")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تشغيل التطبيق في البيئة الآن", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = { onDiagnosticsClick(currentApp) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("app_settings_diagnostics_button")
                    ) {
                        Icon(imageVector = Icons.Default.GppGood, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("اختبار عزل التطبيق ورادار التتبع")
                    }

                    Button(
                        onClick = {
                            onDeleteApp(currentApp)
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("app_settings_delete_button")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إزالة التطبيق من البيئة المعزولة")
                    }
                }
            }
        }
    }

    if (showPanicDialog) {
        AlertDialog(
            onDismissRequest = { showPanicDialog = false },
            title = { Text("تأكيد التطهير الفوري (Panic Wipe)") },
            text = { Text("سيتم إيقاف التطبيق قسرياً، ومسح بيانات الجلسة والمخزن المؤقت، وتجميد الحزمة لمنع تشغيلها. هل ترغب بالمتابعة؟") },
            confirmButton = {
                Button(
                    onClick = {
                        showPanicDialog = false
                        onPanicWipe(currentApp.packageName)
                        currentApp = currentApp.copy(isFrozen = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed)
                ) {
                    Text("تطهير فوري", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CyberCyanDark,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
