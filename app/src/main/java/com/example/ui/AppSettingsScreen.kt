package com.example.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.SandboxedApp
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanDark
import com.example.ui.theme.CyberEmerald
import com.example.workprofile.WorkProfileHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    app: SandboxedApp,
    onDeleteApp: (SandboxedApp) -> Unit,
    onLaunchApp: (SandboxedApp) -> Unit,
    onDiagnosticsClick: (SandboxedApp) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val profileStatus = remember { WorkProfileHelper.getWorkProfileStatus(context) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سياسة التطبيق", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CyberCard(borderColor = CyberCyan.copy(alpha = 0.5f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconView(app.packageName, Modifier.size(52.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(app.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                CyberCard(borderColor = if (profileStatus.isProfileOwner && profileStatus.networkLockdownEnabled) CyberEmerald.copy(alpha = 0.5f) else CyberAmber.copy(alpha = 0.5f)) {
                    Text("حالة بيئة Android", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(profileStatus.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                CyberCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = CyberCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("سياسة الشبكة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (app.blockAllNetwork) "حظر الشبكة بالكامل مطلوب. عندما تكون نسخة التطبيق داخل ملف العمل، توجه الحزم المشمولة إلى VPN محلي لا يعيد إرسالها." else "هذه الحزمة لا تحمل قاعدة الحظر الكامل؛ لن يسمح خط الإطلاق الآمن بتشغيلها.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "عداد الحزم رقم تجميعي لما قرأته واجهة TUN؛ لا يحدد مالك كل حزمة عندما تكون عدة تطبيقات محمية، ولا يكشف محتوى HTTPS أو DNS المشفر.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                CyberCard {
                    Text("الملفات والصلاحيات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "الفصل بين الملف الشخصي وملف العمل يفرضه Android. عند الإضافة داخل ملف يملكه IsolateBox، يحاول التطبيق رفض أذونات Android الخطرة المطلوبة. هذا لا يسمح للتطبيق بقراءة ذاكرة أو ملفات خاصة بتطبيق آخر، ولا يمسح بياناته.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (app.isFrozen) {
                        Spacer(Modifier.height(8.dp))
                        Text("التطبيق معلّق بسياسة مالك ملف العمل؛ لا يعني ذلك مسح بياناته.", color = CyberAmber, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onLaunchApp(app) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("app_settings_launch_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تحقق من الحماية ثم شغّل نسخة ملف العمل", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onDiagnosticsClick(app) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("app_settings_diagnostics_button")
                    ) {
                        Icon(Icons.Default.GppGood, contentDescription = null, tint = CyberCyanDark)
                        Spacer(Modifier.width(8.dp))
                        Text("فحص إعداد VPN والصلاحيات")
                    }
                    OutlinedButton(
                        onClick = { showRemoveDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("app_settings_delete_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("إزالة من قائمة IsolateBox (لا تحذف التطبيق أو بياناته)")
                    }
                }
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("إزالة من قائمة الحماية؟") },
            text = { Text("سيُزال إدخال التطبيق من قاعدة IsolateBox فقط. لن تُحذف نسخة التطبيق المثبتة أو بياناتها.") },
            confirmButton = {
                Button(onClick = { showRemoveDialog = false; onDeleteApp(app); onBack() }) { Text("إزالة من القائمة") }
            },
            dismissButton = { TextButton(onClick = { showRemoveDialog = false }) { Text("إلغاء") } }
        )
    }
}
