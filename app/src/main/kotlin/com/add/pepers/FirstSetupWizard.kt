package com.add.pepers

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun FirstSetupWizard(
    database: Database,
    userName: String,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val now = remember { Calendar.getInstance() }
    var shopName by remember { mutableStateOf("") }
    var registrationNumber by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(RegistrationMode.NUMERIC) }
    var year by remember { mutableStateOf(now.get(Calendar.YEAR).toString()) }
    var month by remember { mutableStateOf((now.get(Calendar.MONTH) + 1).toString()) }
    var startDate by remember { mutableStateOf(String.format(Locale.ENGLISH, "%04d/%02d/%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))) }
    var addAssistant by remember { mutableStateOf(false) }
    var assistantName by remember { mutableStateOf("") }
    var assistantTask by remember { mutableStateOf("") }
    var assistantStartDate by remember { mutableStateOf(startDate) }
    var selectedDays by remember { mutableStateOf(setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.SATURDAY)) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun pickDate(current: String, onSelected: (String) -> Unit) {
        val parsed = runCatching { SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).parse(current) }.getOrNull()
        val calendar = Calendar.getInstance().apply { if (parsed != null) time = parsed }
        DatePickerDialog(context, { _, y, m, d ->
            onSelected(String.format(Locale.ENGLISH, "%04d/%02d/%02d", y, m + 1, d))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("ابدأ إعداد حسابك") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أنشئ المحل والشهر وأيام العمل. ويمكنك إنشاء أول مساعد وتاريخ بدايته الآن.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(shopName, { shopName = it; error = null }, singleLine = true, label = { Text("اسم المحل") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(registrationNumber, { registrationNumber = it; error = null }, singleLine = true, label = { Text("رقم المحل / التسجيل") }, modifier = Modifier.fillMaxWidth())
                Text("نوع التسجيل")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(8f))) {
                    OutlinedButton(onClick = { mode = RegistrationMode.NUMERIC }, modifier = Modifier.weight(1f)) { Text(if (mode == RegistrationMode.NUMERIC) "✓ عددي" else "عددي") }
                    OutlinedButton(onClick = { mode = RegistrationMode.INDIVIDUAL }, modifier = Modifier.weight(1f)) { Text(if (mode == RegistrationMode.INDIVIDUAL) "✓ فردي" else "فردي") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(8f))) {
                    OutlinedTextField(year, { year = it.filter(Char::isDigit) }, singleLine = true, label = { Text("السنة") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(month, { month = it.filter(Char::isDigit) }, singleLine = true, label = { Text("الشهر 1-12") }, modifier = Modifier.weight(1f))
                }
                OutlinedButton(onClick = { pickDate(startDate) { startDate = it; assistantStartDate = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text("تاريخ بداية العمل: $startDate")
                }
                Text("أيام العمل في هذا الشهر")
                listOf(
                    Calendar.SUNDAY to "الأحد", Calendar.MONDAY to "الإثنين",
                    Calendar.TUESDAY to "الثلاثاء", Calendar.WEDNESDAY to "الأربعاء",
                    Calendar.THURSDAY to "الخميس", Calendar.FRIDAY to "الجمعة",
                    Calendar.SATURDAY to "السبت"
                ).forEach { (day, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(day in selectedDays, { checked -> selectedDays = if (checked) selectedDays + day else selectedDays - day })
                        Text(label)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(addAssistant, { addAssistant = it })
                    Text("إنشاء مساعد الآن")
                }
                if (addAssistant) {
                    OutlinedTextField(assistantName, { assistantName = it; error = null }, singleLine = true, label = { Text("اسم المساعد") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(assistantTask, { assistantTask = it }, singleLine = true, label = { Text("المهمة / التخصص") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { pickDate(assistantStartDate) { assistantStartDate = it } }, modifier = Modifier.fillMaxWidth()) {
                        Text("بداية المساعد: $assistantStartDate")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (saving) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
        },
        confirmButton = {
            Button(enabled = !saving, onClick = {
                val cleanShop = shopName.trim()
                val reg = registrationNumber.trim()
                val y = year.toIntOrNull()
                val m = month.toIntOrNull()
                if (cleanShop.isBlank()) error = "اكتب اسم المحل"
                else if (reg.isBlank()) error = "أدخل رقم المحل أو رقم التسجيل"
                else if (y == null || y !in 1900..2500 || m == null || m !in 1..12) error = "تحقق من السنة ورقم الشهر"
                else if (selectedDays.isEmpty()) error = "اختر يوم عمل واحداً على الأقل"
                else if (addAssistant && assistantName.trim().isBlank()) error = "اكتب اسم المساعد أو أوقف خيار إنشاء المساعد"
                else {
                    saving = true
                    runCatching {
                        val shopId = database.addShop(cleanShop, mode, reg)
                        check(shopId > 0L) { "تعذر إنشاء المحل" }
                        val monthId = database.addMonth(shopId, y, m, y.toString() + " - " + monthName(m), userName.ifBlank { "عامل" }, startDate, true, null, selectedDays)
                        check(monthId > 0L) { "تعذر إنشاء الشهر" }
                        if (addAssistant) {
                            val assistantId = database.addAssistant(shopId, null, assistantName, assistantTask, assistantStartDate)
                            check(assistantId > 0L) { "تعذر إنشاء المساعد" }
                        }
                        onFinished()
                    }.onFailure {
                        saving = false
                        error = it.message ?: "تعذر إكمال الإعداد"
                    }
                }
            }) { Text("إنشاء والبدء") }
        },
        dismissButton = null
    )
}
