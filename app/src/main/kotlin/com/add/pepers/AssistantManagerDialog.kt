package com.add.pepers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun AssistantManagerDialog(shop: ShopRecord, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val database = remember { Database(context.applicationContext) }
    var assistants by remember(shop.id) { mutableStateOf(database.getAssistants(shop.id)) }
    var selected by remember { mutableStateOf<AssistantRecord?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showWithdrawals by remember { mutableStateOf<AssistantRecord?>(null) }
    val prefs = remember { context.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE) }

    fun reload() { assistants = database.getAssistants(shop.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("المساعدون", fontWeight = FontWeight.Bold)
                Text("إدارة العمل والحساب والسحبيات", fontSize = 10.sp, color = AppMuted)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurfaceAlt),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("طريقة العمل", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Purple)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "الإنتاج يُسجل مرة واحدة في سجل الخياط. أجر المساعد يُحسب تلقائيًا من إنتاجه وسعر القطعة. المصروف/البدل والسحبيات منفصلان، وأي سجل يدخله المساعد من حسابه يبقى بانتظار اعتماد الخياط.",
                            fontSize = 10.sp,
                            color = AppMuted,
                            lineHeight = 16.sp
                        )
                    }
                }

                if (assistants.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("لا يوجد مساعدون لهذا المحل", fontWeight = FontWeight.SemiBold, color = AppText)
                            Spacer(Modifier.height(4.dp))
                            Text("إضافة المساعد اختيارية ويمكن تنفيذها لاحقًا.", fontSize = 10.sp, color = AppMuted, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 430.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(assistants, key = { it.id }) { assistant ->
                            val bundles = remember(assistant.id, assistants) {
                                database.getMonths(shop.id).mapNotNull { database.loadMonthBundle(it.id) }
                            }
                            val earned = remember(assistant.id, bundles) { bundles.sumOf { database.calculateAssistantEarned(assistant, it) } }
                            val expenses = remember(assistant.id, bundles) { bundles.sumOf { database.calculateAssistantExpense(assistant.id, it) } }
                            val withdrawals = remember(assistant.id) { database.getAssistantWithdrawals(assistant.id) }
                            val withdrawn = withdrawals.sumOf { it.amount }
                            val balance = earned + expenses - withdrawn

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(assistant.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppText)
                                            Text(assistant.task.ifBlank { "مهمة غير محددة" }, fontSize = 10.sp, color = AppMuted)
                                        }
                                        Box(
                                            Modifier.background(
                                                if (assistant.active) Green.copy(alpha = .10f) else Red.copy(alpha = .10f),
                                                RoundedCornerShape(8.dp)
                                            ).padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Text(if (assistant.active) "يعمل" else "متوقف", fontSize = 9.sp, color = if (assistant.active) Green else Red, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        AssistantSummaryCell("المستحق", "${earned} ريال", Modifier.weight(1f), Green)
                                        AssistantSummaryCell("السحبيات", "${withdrawn} ريال", Modifier.weight(1f), Red)
                                        AssistantSummaryCell("الرصيد", "${balance} ريال", Modifier.weight(1f), Purple)
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Text("السعر الافتراضي: ${assistant.defaultRate} ريال/قطعة • معرف الربط: ${assistant.linkCode}", fontSize = 9.sp, color = AppMuted)

                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        OutlinedButton(
                                            onClick = { selected = assistant; showEditor = true },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 5.dp)
                                        ) { Text("تعديل", fontSize = 9.sp) }
                                        OutlinedButton(
                                            onClick = { showWithdrawals = assistant },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 5.dp)
                                        ) { Text("السحبيات", fontSize = 9.sp) }
                                        OutlinedButton(
                                            onClick = {
                                                shareAssistantLedgerPdf(
                                                    context = context,
                                                    database = database,
                                                    shop = shop,
                                                    assistant = assistant,
                                                    userName = prefs.getString("user_name", "").orEmpty(),
                                                    userPhone = prefs.getString("user_phone", "").orEmpty(),
                                                    userEmail = prefs.getString("user_email", "").orEmpty()
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 5.dp)
                                        ) { Text("كشف PDF", fontSize = 9.sp) }
                                    }

                                    if (assistant.phone.isNotBlank()) {
                                        Spacer(Modifier.height(5.dp))
                                        OutlinedButton(
                                            onClick = {
                                                val message = "مرحباً ${assistant.name}، معرف ربط حسابك في Pepers هو: ${assistant.linkCode}"
                                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                    data = Uri.parse("smsto:${Uri.encode(assistant.phone)}")
                                                    putExtra("sms_body", message)
                                                }
                                                runCatching { context.startActivity(intent) }.onFailure {
                                                    Toast.makeText(context, "تعذر فتح تطبيق الرسائل", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) { Text("إرسال معرف الربط للمساعد", fontSize = 9.sp) }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(onClick = { selected = null; showEditor = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("إضافة مساعد", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )

    if (showEditor) {
        AssistantEditorDialog(shop, selected, { showEditor = false }) { reload(); showEditor = false }
    }

    if (showWithdrawals != null) {
        AssistantWithdrawalsDialog(
            database = database,
            assistant = showWithdrawals!!,
            onDismiss = { showWithdrawals = null }
        )
    }
}

@Composable
private fun AssistantSummaryCell(label: String, value: String, modifier: Modifier, valueColor: Color) {
    Column(
        modifier = modifier
            .background(AppSurfaceAlt, RoundedCornerShape(9.dp))
            .border(1.dp, AppBorder, RoundedCornerShape(9.dp))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 8.sp, color = AppMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 10.sp, color = valueColor, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AssistantWithdrawalsDialog(
    database: Database,
    assistant: AssistantRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var refresh by remember(assistant.id) { mutableStateOf(0) }
    var date by remember(assistant.id) { mutableStateOf(SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Calendar.getInstance().time)) }
    var amount by remember(assistant.id) { mutableStateOf("") }
    var note by remember(assistant.id) { mutableStateOf("") }
    val withdrawals = remember(assistant.id, refresh) { database.getAssistantWithdrawals(assistant.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سحبيات ${assistant.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("السحبية تخصم من الرصيد النهائي ولا تدخل ضمن إنتاج اليوم.", fontSize = 10.sp, color = AppMuted)
                Text("إجمالي السحبيات: ${withdrawals.sumOf { it.amount }} ريال", fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
                OutlinedTextField(date, { date = it }, label = { Text("التاريخ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("المبلغ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        val value = amount.toIntOrNull() ?: 0
                        if (value <= 0 || date.isBlank()) {
                            Toast.makeText(context, "أدخل التاريخ والمبلغ", Toast.LENGTH_SHORT).show()
                        } else {
                            val id = database.addAssistantWithdrawal(assistant.id, date.trim(), value, note.trim())
                            if (id > 0) {
                                amount = ""
                                note = ""
                                refresh++
                                Toast.makeText(context, "تم حفظ السحبية", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "تعذر حفظ السحبية", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("إضافة سحبية") }
                if (withdrawals.isEmpty()) {
                    Text("لا توجد سحبيات مسجلة.", fontSize = 10.sp, color = AppMuted)
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(withdrawals, key = { it.id }) { item ->
                            Text("${item.date} — ${item.amount} ريال${if (item.note.isBlank()) "" else " — ${item.note}"}", fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
private fun AssistantEditorDialog(
    shop: ShopRecord,
    assistant: AssistantRecord?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { Database(context.applicationContext) }
    val today = remember { SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Calendar.getInstance().time) }
    var name by remember(assistant?.id) { mutableStateOf(assistant?.name.orEmpty()) }
    var phone by remember(assistant?.id) { mutableStateOf(assistant?.phone.orEmpty()) }
    var task by remember(assistant?.id) { mutableStateOf(assistant?.task.orEmpty()) }
    var rate by remember(assistant?.id) { mutableStateOf(assistant?.defaultRate?.takeIf { it > 0 }?.toString().orEmpty()) }
    var startDate by remember(assistant?.id) { mutableStateOf(assistant?.startDate ?: today) }
    var endDate by remember(assistant?.id) { mutableStateOf(assistant?.endDate.orEmpty()) }
    var notes by remember(assistant?.id) { mutableStateOf(assistant?.notes.orEmpty()) }
    var active by remember(assistant?.id) { mutableStateOf(assistant?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (assistant == null) "إضافة مساعد" else "تعديل المساعد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم المساعد") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' } }, label = { Text("رقم الهاتف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(task, { task = it }, label = { Text("نوع المهمة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rate, { rate = it.filter(Char::isDigit) }, label = { Text("السعر المتفق عليه للقطعة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(startDate, { startDate = it }, label = { Text("بداية العمل (افتراضي اليوم)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (assistant != null) {
                    OutlinedTextField(endDate, { endDate = it }, label = { Text("تاريخ الإيقاف (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (active) Button(onClick = { active = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("إيقاف حساب المساعد") }
                    else Button(onClick = { active = true }, modifier = Modifier.fillMaxWidth()) { Text("إعادة تفعيل الحساب") }
                    Text("الإيقاف يمنع تسجيل أيام جديدة للمساعد، مع بقاء السجل التاريخي.", fontSize = 9.sp)
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات اختيارية") }, modifier = Modifier.fillMaxWidth())
                if (assistant != null) Text("معرف الربط: ${assistant.linkCode}", fontSize = 10.sp, color = Purple)
                else Text("سيتم إنشاء معرف ربط فريد بعد الحفظ.", fontSize = 9.sp, color = AppMuted)
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleanName = name.trim()
                val amount = rate.toIntOrNull() ?: 0
                if (cleanName.isBlank() || startDate.isBlank() || amount <= 0) {
                    Toast.makeText(context, "أدخل الاسم وبداية العمل وسعر القطعة", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (assistant == null) {
                    val id = database.addAssistant(shop.id, shop.defaultWorkerId, cleanName, task, startDate, notes, phone, amount)
                    if (id > 0) { Toast.makeText(context, "تم إنشاء المساعد ومعرف الربط", Toast.LENGTH_LONG).show(); onSaved() }
                    else Toast.makeText(context, "تعذر إنشاء المساعد", Toast.LENGTH_LONG).show()
                } else {
                    database.updateAssistant(assistant.id, cleanName, task, startDate, endDate.ifBlank { null }, active, notes, phone, amount)
                    onSaved()
                }
            }) { Text(if (assistant == null) "إنشاء المساعد" else "حفظ التعديل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
