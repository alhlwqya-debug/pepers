package com.add.pepers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection

@Composable
internal fun RegistrationSettingsDialog(
    currentMode: RegistrationMode,
    onDismiss: () -> Unit,
    onSave: (RegistrationMode) -> Unit,
    onSecurity: () -> Unit
) {
    var selectedMode by remember(currentMode) { mutableStateOf(currentMode) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column(Modifier.fillMaxWidth()) {
                    Text("طريقة التسجيل", Modifier.fillMaxWidth(), textAlign = TextAlign.Right, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.size(4.dp))
                    Text("اختر طريقة إدخال البيانات التي تناسب طريقة عمل المحل.", Modifier.fillMaxWidth(), textAlign = TextAlign.Right, color = Color.Gray, fontSize = 12.sp)
                }
            },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RegistrationModeCard(
                        title = "تسجيل عددي",
                        description = "لتسجيل الأيام والكميات والمصروفات والمجموع بطريقة سريعة ومباشرة.",
                        badge = "١٢٣",
                        selected = selectedMode == RegistrationMode.NUMERIC,
                        onClick = { selectedMode = RegistrationMode.NUMERIC }
                    )
                    RegistrationModeCard(
                        title = "تسجيل فردي",
                        description = "لتسجيل أسماء الزبائن وأرقام الصفحات والقطع والتفاصيل لكل عملية.",
                        badge = "👤",
                        selected = selectedMode == RegistrationMode.INDIVIDUAL,
                        onClick = { selectedMode = RegistrationMode.INDIVIDUAL }
                    )
                    Text("يمكن تغيير الطريقة لاحقًا من هذه النافذة دون حذف بيانات المحل.", Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = TextAlign.Right, color = Color.Gray, fontSize = 10.sp)
                }
            },
            confirmButton = { Button(onClick = { onSave(selectedMode) }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("حفظ") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    TextButton(onClick = { onDismiss(); onSecurity() }) { Text("الأمان والنسخ الاحتياطي") }
                }
            }
        )
    }
}

@Composable
private fun RegistrationModeCard(title: String, description: String, badge: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Purple.copy(alpha = 0.10f) else Color(0xFFF8F7FA)),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Purple) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1DDE7))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (selected) Purple else Color.Unspecified)
                Spacer(Modifier.size(3.dp))
                Text(description, fontSize = 11.sp, color = Color.Gray, lineHeight = 16.sp)
            }
            Spacer(Modifier.size(10.dp))
            Text(badge, fontSize = if (badge == "👤") 23.sp else 18.sp, fontWeight = FontWeight.Bold, color = Purple)
            if (selected) { Spacer(Modifier.size(7.dp)); Text("✓", color = Purple, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
