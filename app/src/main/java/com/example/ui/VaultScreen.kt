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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.VaultManager
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanDark
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    vaultItems: List<VaultManager.VaultItem>,
    onSaveSecret: (name: String, content: String) -> Unit,
    onDeleteSecret: (name: String) -> Unit,
    onReadSecret: (name: String) -> String?,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newSecretName by remember { mutableStateOf("") }
    var newSecretContent by remember { mutableStateOf("") }
    var selectedItemDecrypted by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الخزنة المشفرة (Android KeyStore)", fontWeight = FontWeight.Bold) },
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
            // Hardware Keystore Card
            item {
                CyberCard(borderColor = CyberEmerald.copy(alpha = 0.5f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = CyberEmerald,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "تشفير عتادي آمن (Hardware-backed AES-256-GCM)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "المفاتيح التشفيرية محفوظة في شريحة أمان الجهاز (Android KeyStore) ولا يمكن استخراجها.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Add new secret container
            item {
                CyberCard {
                    Text(
                        text = "إضافة بيانات أو ملف مشفر جديد",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newSecretName,
                        onValueChange = { newSecretName = it },
                        label = { Text("اسم العنصر أو الحزمة") },
                        modifier = Modifier.fillMaxWidth().testTag("vault_name_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newSecretContent,
                        onValueChange = { newSecretContent = it },
                        label = { Text("المحتوى السري أو البيانات المراد عزلها") },
                        modifier = Modifier.fillMaxWidth().testTag("vault_content_input"),
                        minLines = 2
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (newSecretName.isNotBlank() && newSecretContent.isNotBlank()) {
                                onSaveSecret(newSecretName.trim(), newSecretContent)
                                newSecretName = ""
                                newSecretContent = ""
                            }
                        },
                        enabled = newSecretName.isNotBlank() && newSecretContent.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("save_vault_button")
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تشفير وحفظ في الخزنة", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Stored vault items
            item {
                Text(
                    text = "العناصر المشفرة المخزنة (${vaultItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (vaultItems.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد ملفات أو أسرار مشفرة مخزنة حالياً في الخزنة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(vaultItems) { item ->
                    val isDecrypted = selectedItemDecrypted?.first == item.name

                    CyberCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${item.sizeBytes} بايت | مشفر بـ AES-GCM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        if (isDecrypted) {
                                            selectedItemDecrypted = null
                                        } else {
                                            val decrypted = onReadSecret(item.name) ?: "تعذر فك التشفير"
                                            selectedItemDecrypted = item.name to decrypted
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isDecrypted) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "عرض",
                                        tint = CyberCyan
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteSecret(item.name) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف",
                                        tint = CyberRed
                                    )
                                }
                            }
                        }

                        if (isDecrypted) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = selectedItemDecrypted?.second ?: "",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CyberEmerald
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
