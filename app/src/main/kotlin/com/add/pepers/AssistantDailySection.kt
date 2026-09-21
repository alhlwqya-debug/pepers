package com.add.pepers
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AssistantDailySection(
    database: Database,
    shopId: Long,
    bundle: MonthBundle,
    day: DayRecord,
    pieces: List<PieceRecord>,
    compact: Boolean = false,
    onSaved: () -> Unit = {}
) {
    val assistants = remember(shopId) { database.getAssistants(shopId, includeInactive = false) }
    if (assistants.isEmpty()) return
    var refresh by remember(day.id, assistants) { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth().border(1.dp, AppBorder, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
        Text("👥 المساعدون — ${day.date}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Purple)
        Text("عدد قطع اليوم يؤخذ تلقائيًا من تسجيل الخياط ولا يُعاد إدخاله للمساعد.", fontSize = 9.sp, color = AppMuted)
        Spacer(Modifier.height(5.dp))
        assistants.forEach { assistant ->
            val record = remember(day.id, assistant.id, refresh) { database.getAssistantDailyRecord(assistant.id, day.id) }
            var status by remember(day.id, assistant.id, refresh) { mutableStateOf(record?.status ?: AssistantDailyStatus.WORKED) }
            var withdrawal by remember(day.id, assistant.id, refresh) { mutableStateOf(record?.expense?.toString()?.takeIf { it != "0" } ?: "") }
            var note by remember(day.id, assistant.id, refresh) { mutableStateOf(record?.expenseNote.orEmpty().ifBlank { record?.notes.orEmpty() }) }
            val earned = database.calculateAssistantDayEarned(assistant, bundle, day)
            val piecesCount = if (status == AssistantDailyStatus.WORKED) {
                if (database.getShops().firstOrNull { it.id == shopId }?.registrationMode == RegistrationMode.INDIVIDUAL)
                    database.getIndividualEntries(bundle.month.id, day.date).sumOf { it.quantities.values.sum() }
                else day.quantities.values.sum()
            } else 0
            Column(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(assistant.name, Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppText)
                    listOf(AssistantDailyStatus.WORKED to "عمل", AssistantDailyStatus.ABSENT to "غياب", AssistantDailyStatus.NO_WORK to "لا عمل").forEach { (value, label) ->
                        if (status == value) Button(onClick = { status = value }, contentPadding = PaddingValues(horizontal = 7.dp), modifier = Modifier.height(28.dp)) { Text(label, fontSize = 8.sp) }
                        else OutlinedButton(onClick = { status = value }, contentPadding = PaddingValues(horizontal = 7.dp), modifier = Modifier.height(28.dp)) { Text(label, fontSize = 8.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("القطع: ${piecesCount}", fontSize = 9.sp, color = AppMuted)
                        Text("المستحق: ${earned}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Green)
                    }
                    BasicTextField(value = withdrawal, onValueChange = { withdrawal = it.filter(Char::isDigit) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontSize = 9.sp, color = Red), modifier = Modifier.weight(1f).border(1.dp, AppBorder, RoundedCornerShape(7.dp)).padding(6.dp), decorationBox = { inner -> if (withdrawal.isBlank()) Text("السحبية / المصروف", fontSize = 8.sp, color = AppMuted); inner() })
                    BasicTextField(value = note, onValueChange = { note = it }, singleLine = true, textStyle = TextStyle(fontSize = 9.sp, color = AppText), modifier = Modifier.weight(1f).border(1.dp, AppBorder, RoundedCornerShape(7.dp)).padding(6.dp), decorationBox = { inner -> if (note.isBlank()) Text("ملاحظة", fontSize = 8.sp, color = AppMuted); inner() })
                    Button(onClick = {
                        database.setAssistantDailyEntry(assistant.id, day.id, status, withdrawal.toIntOrNull() ?: 0, note)
                        refresh++
                        onSaved()
                    }, colors = ButtonDefaults.buttonColors(containerColor = Green), contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(32.dp)) { Text("حفظ", fontSize = 8.sp) }
                }
                if (!compact) Text(if (record == null) "لم يسجل هذا اليوم بعد" else "تم تحديث السجل المشترك — لا يتم جمع تسجيل آخر لنفس اليوم", fontSize = 8.sp, color = if (record == null) AppMuted else Purple, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}
