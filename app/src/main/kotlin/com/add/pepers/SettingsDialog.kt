package com.add.pepers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RegistrationSettingsDialog(
    currentMode: RegistrationMode,
    onDismiss: () -> Unit,
    onSave: (RegistrationMode) -> Unit,
    onSecurity: () -> Unit
) {
    var selectedMode by remember(currentMode) { mutableStateOf(currentMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات المحل", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "نوع التسجيل مرتبط بالمحل الحالي. عند إضافة محل جديد تختار نوع حسابه بشكل مستقل.",
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                RegistrationModeOption(
                    title = "تسجيل عددي",
                    description = "جدول بالأيام والكميات والمصروف والمجموع.",
                    selected = selectedMode == RegistrationMode.NUMERIC,
                    onClick = { selectedMode = RegistrationMode.NUMERIC }
                )
                RegistrationModeOption(
                    title = "تسجيل فردي",
                    description = "سجل بأسماء الزبائن وأرقام الصفحات والكميات.",
                    selected = selectedMode == RegistrationMode.INDIVIDUAL,
                    onClick = { selectedMode = RegistrationMode.INDIVIDUAL }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onSecurity,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A4C93))
                ) { Text("🔒 الحماية والنسخ الاحتياطي") }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedMode) },
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun RegistrationModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 10.sp, color = Color.Gray)
        }
    }
}
