package com.add.pepers

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SecurityOnlySettingsDialog(
    context: Context,
    hasPin: Boolean,
    onDismiss: () -> Unit,
    onSetPin: () -> Unit,
    onRemovePin: () -> Unit
) {
    var biometricEnabled by remember { mutableStateOf(isBiometricLockEnabled(context)) }
    var protectionEnabled by remember { mutableStateOf(hasPin || biometricEnabled) }
    val biometricAvailable = remember { isDeviceLockAvailable(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("أمان التطبيق", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecuritySwitchRowNew(
                    title = "قفل التطبيق",
                    description = "اطلب حماية عند فتح Pepers.",
                    checked = protectionEnabled
                ) { enabled ->
                    protectionEnabled = enabled
                    if (!enabled) {
                        clearAppPin(context)
                        setBiometricLockEnabled(context, false)
                        biometricEnabled = false
                        onRemovePin()
                    } else if (!hasPin && !biometricEnabled) {
                        onSetPin()
                    }
                }

                SecuritySwitchRowNew(
                    title = "البصمة أو قفل الجهاز",
                    description = if (biometricAvailable) "استخدم مصادقة الجهاز عند توفرها." else "مصادقة الجهاز غير متاحة على هذا الجهاز.",
                    checked = biometricEnabled,
                    enabled = biometricAvailable
                ) { enabled ->
                    setBiometricLockEnabled(context, enabled)
                    biometricEnabled = enabled
                    protectionEnabled = enabled || hasPin
                }

                Card(
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5FA))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🔐  رمز PIN", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(4.dp))
                        Text("غيّر رمز PIN أو ألغِه من إعدادات الأمان فقط.", fontSize = 10.sp, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            TextButton(onClick = onSetPin) { Text(if (hasPin) "تغيير الرمز" else "تعيين الرمز") }
                            if (hasPin) TextButton(onClick = onRemovePin) { Text("إلغاء الرمز", color = Color(0xFFC62828)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } }
    )
}

@Composable
private fun SecuritySwitchRowNew(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(2.dp))
            Text(description, fontSize = 10.sp, color = Color.Gray)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun BackupOnlySettingsDialog(
    context: Context,
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    val lastBackup = remember {
        context.getSharedPreferences("backup_meta", Context.MODE_PRIVATE)
            .getLong("last_backup_at", 0L)
    }
    val formatted = if (lastBackup > 0L) {
        SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.getDefault()).format(Date(lastBackup))
    } else {
        "لم يتم إنشاء نسخة احتياطية بعد"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("النسخ الاحتياطي والاستعادة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5FA))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("💾  بيانات المحل", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(5.dp))
                        Text("آخر نسخة: $formatted", fontSize = 11.sp, color = Color.Gray)
                        Spacer(Modifier.size(4.dp))
                        Text("أنشئ نسخة يدوية من بياناتك أو استعد نسخة سابقة عند الحاجة.", fontSize = 10.sp, color = Color.Gray)
                    }
                }
                Button(
                    onClick = onBackup,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) { Text("💾  إنشاء نسخة احتياطية") }
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple)
                ) { Text("♻  استعادة نسخة احتياطية") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
