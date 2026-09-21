package com.add.pepers

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AssistantManagerDialog(shop: ShopRecord, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val database = remember { Database(context.applicationContext) }
    var assistants by remember(shop.id) { mutableStateOf(database.getAssistants(shop.id)) }
    var showEditor by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AssistantRecord?>(null) }

    AlertDialog(
        onDismissRequest = { database.close(); onDismiss() },
        title = { Text("مساعدو الخياط") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("المساعد لا يدخل في أي حساب قبل تاريخ بداية عمله. إذا لم يوجد مساعد اترك القائمة فارغة.", fontSize = 11.sp)
                if (assistants.isEmpty()) {
                    Text("لا يوجد مساعدين لهذا الخياط.", fontSize = 12.sp)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(assistants, key = { it.id }) { assistant ->
                            OutlinedButton(onClick = { selected = assistant; showEditor = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(assistant.name + " — " + assistant.task.ifBlank { "مهمة غير محددة" })
                            }
                        }
                    }
                }
                Button(onClick = { selected = null; showEditor = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("إضافة مساعد")
                }
            }
        },
        confirmButton = { TextButton(onClick = { database.close(); onDismiss() }) { Text("إغلاق") } }
    )

    if (showEditor) {
        AssistantEditorDialog(
            shop = shop,
            assistant = selected,
            onDismiss = { showEditor = false },
            onSaved = {
                assistants = database.getAssistants(shop.id)
                showEditor = false
            }
        )
    }
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
    var name by remember(assistant?.id) { mutableStateOf(assistant?.name.orEmpty()) }
    var task by remember(assistant?.id) { mutableStateOf(assistant?.task.orEmpty()) }
    var startDate by remember(assistant?.id) { mutableStateOf(assistant?.startDate.orEmpty()) }
    var endDate by remember(assistant?.id) { mutableStateOf(assistant?.endDate.orEmpty()) }
    var notes by remember(assistant?.id) { mutableStateOf(assistant?.notes.orEmpty()) }
    var active by remember(assistant?.id) { mutableStateOf(assistant?.active ?: true) }
    var rate by remember { mutableStateOf("") }
    var rateFrom by remember { mutableStateOf(assistant?.startDate.orEmpty()) }
    var rateTo by remember { mutableStateOf("") }
    var ratePieceId by remember { mutableStateOf<Long?>(null) }
    var dailyDate by remember { mutableStateOf(assistant?.startDate.orEmpty()) }
    var dailyStatus by remember { mutableStateOf(AssistantDailyStatus.WORKED) }
    var dailyExpense by remember { mutableStateOf("") }
    var dailyNote by remember { mutableStateOf("") }
    var withdrawal by remember { mutableStateOf("") }
    var withdrawalNote by remember { mutableStateOf("") }
    val pieces = remember(shop.id) { database.getPieces(shop.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (assistant == null) "إضافة مساعد" else "إدارة المساعد") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("اسم المساعد") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(task, { task = it }, label = { Text("المهمة") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(startDate, { startDate = it }, label = { Text("بداية العمل yyyy/MM/dd") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(endDate, { endDate = it }, label = { Text("نهاية العمل اختياري") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth()) }

                if (assistant != null) {
                    item { Spacer(Modifier.height(4.dp)); Text("حصة المساعد لكل قطعة", fontSize = 12.sp) }
                    item {
                        Column {
                            pieces.forEach { piece ->
                                OutlinedButton(
                                    onClick = { ratePieceId = piece.id },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(piece.name + if (ratePieceId == piece.id) " ✓" else "") }
                            }
                        }
                    }
                    item { OutlinedTextField(rate, { rate = it.filter(Char::isDigit) }, label = { Text("المبلغ للمساعد من القطعة") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(rateFrom, { rateFrom = it }, label = { Text("ساري من yyyy/MM/dd") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(rateTo, { rateTo = it }, label = { Text("ساري إلى اختياري") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item {
                        Button(onClick = {
                            val pieceId = ratePieceId
                            val amount = rate.toIntOrNull()
                            if (pieceId == null || amount == null || rateFrom.isBlank()) {
                                Toast.makeText(context, "اختر قطعة وسعر وتاريخ بداية", Toast.LENGTH_SHORT).show()
                            } else {
                                database.setAssistantPieceRate(assistant.id, pieceId, amount, rateFrom, rateTo.ifBlank { null })
                                Toast.makeText(context, "تم حفظ حصة القطعة", Toast.LENGTH_SHORT).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("حفظ سعر القطعة") }
                    }
                    item { Text("سجل المساعد اليومي", fontSize = 12.sp) }
                    item { OutlinedTextField(dailyDate, { dailyDate = it }, label = { Text("التاريخ") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                AssistantDailyStatus.WORKED to "يعمل",
                                AssistantDailyStatus.ABSENT to "غياب",
                                AssistantDailyStatus.NO_WORK to "لا يوجد عمل"
                            ).forEach { pair ->
                                if (dailyStatus == pair.first) Button(onClick = { dailyStatus = pair.first }, modifier = Modifier.weight(1f)) { Text(pair.second, fontSize = 9.sp) }
                                else OutlinedButton(onClick = { dailyStatus = pair.first }, modifier = Modifier.weight(1f)) { Text(pair.second, fontSize = 9.sp) }
                            }
                        }
                    }
                    item { OutlinedTextField(dailyExpense, { dailyExpense = it.filter(Char::isDigit) }, label = { Text("مصروف/بدل اليوم") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(dailyNote, { dailyNote = it }, label = { Text("بيان المصروف والملاحظة") }, modifier = Modifier.fillMaxWidth()) }
                    item {
                        Button(onClick = {
                            val day = database.getDayRecordForAnyMonth(shop.id, dailyDate)
                            if (day == null) Toast.makeText(context, "لا يوجد سجل يوم لهذا التاريخ", Toast.LENGTH_LONG).show()
                            else {
                                database.setAssistantDailyRecord(assistant.id, day.id, dailyStatus, dailyExpense.toIntOrNull() ?: 0, dailyNote, dailyNote)
                                Toast.makeText(context, "تم حفظ سجل اليوم", Toast.LENGTH_SHORT).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("حفظ سجل اليوم") }
                    }
                    item { Text("السحبيات الخارجية / السلف", fontSize = 12.sp) }
                    item { OutlinedTextField(withdrawal, { withdrawal = it.filter(Char::isDigit) }, label = { Text("مبلغ السحب") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(withdrawalNote, { withdrawalNote = it }, label = { Text("بيان السحب") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item {
                        Button(onClick = {
                            val amount = withdrawal.toIntOrNull() ?: 0
                            if (database.addAssistantWithdrawal(assistant.id, dailyDate, amount, withdrawalNote) > 0) {
                                withdrawal = ""; withdrawalNote = ""
                                Toast.makeText(context, "تم تسجيل السحب", Toast.LENGTH_SHORT).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل السحب") }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (assistant == null) {
                    val id = database.addAssistant(shop.id, null, name, task, startDate, notes)
                    if (id > 0) onSaved()
                    else Toast.makeText(context, "أدخل الاسم وتاريخ بداية العمل", Toast.LENGTH_LONG).show()
                } else {
                    database.updateAssistant(assistant.id, name, task, startDate, endDate.ifBlank { null }, active, notes)
                    onSaved()
                }
            }) { Text(if (assistant == null) "إضافة" else "حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
