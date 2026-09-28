package com.add.pepers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurfaceAlt),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("المساعدون", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Purple)
                        Spacer(Modifier.height(2.dp))
                        Text("سجل عمل المساعدين — ${day.date}", fontSize = 11.sp, color = AppMuted)
                    }
                    Box(
                        modifier = Modifier
                            .background(Purple.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("${assistants.size} مساعد", fontSize = 10.sp, color = Purple, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "عدد القطع يُقرأ تلقائيًا من سجل الخياط، لذلك لا يحتاج المساعد إلى إدخال عدد القطع مرة أخرى.",
                    fontSize = 10.sp,
                    color = AppMuted
                )
            }
        }

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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(assistant.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppText)
                            Text(assistant.task.ifBlank { "مساعد" }, fontSize = 10.sp, color = AppMuted)
                        }
                        Text(
                            if (record == null) "غير مسجل" else "محفوظ",
                            fontSize = 9.sp,
                            color = if (record == null) AppMuted else Green,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("حالة اليوم", fontSize = 10.sp, color = AppMuted, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistantStatusButton("عمل", status == AssistantDailyStatus.WORKED) { status = AssistantDailyStatus.WORKED }
                        AssistantStatusButton("غياب", status == AssistantDailyStatus.ABSENT) { status = AssistantDailyStatus.ABSENT }
                        AssistantStatusButton("لا عمل", status == AssistantDailyStatus.NO_WORK) { status = AssistantDailyStatus.NO_WORK }
                    }

                    Spacer(Modifier.height(10.dp))
                    AssistantTableHeader()
                    AssistantTableRow("عدد القطع", piecesCount.toString(), "المستحق", "${earned} ريال")

                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        AssistantInput(withdrawal, "السحبية / المصروف", KeyboardType.Number, Modifier.weight(1f)) {
                            withdrawal = it.filter(Char::isDigit)
                        }
                        AssistantInput(note, "ملاحظة اختيارية", KeyboardType.Text, Modifier.weight(1f)) {
                            note = it
                        }
                    }

                    Spacer(Modifier.height(9.dp))
                    Button(
                        onClick = {
                            database.setAssistantDailyEntry(assistant.id, day.id, status, withdrawal.toIntOrNull() ?: 0, note, piecesCount)
                            refresh++
                            onSaved()
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("حفظ سجل اليوم", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (!compact) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (record == null) "يمكن تعديل البيانات قبل الحفظ."
                            else "تم تحديث السجل المشترك لهذا اليوم؛ لن يتم إنشاء سجل يومي مكرر.",
                            fontSize = 9.sp,
                            color = if (record == null) AppMuted else Purple
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantStatusButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f).height(36.dp),
            shape = RoundedCornerShape(9.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) { Text(label, fontSize = 10.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f).height(36.dp),
            shape = RoundedCornerShape(9.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) { Text(label, fontSize = 10.sp, color = AppText) }
    }
}

@Composable
private fun AssistantTableHeader() {
    Row(
        Modifier.fillMaxWidth().background(AppSurfaceAlt, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Text("البيان", Modifier.weight(1f), fontSize = 9.sp, color = AppMuted, fontWeight = FontWeight.SemiBold)
        Text("القيمة", Modifier.weight(1f), fontSize = 9.sp, color = AppMuted, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        Text("البيان", Modifier.weight(1f), fontSize = 9.sp, color = AppMuted, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        Text("القيمة", Modifier.weight(1f), fontSize = 9.sp, color = AppMuted, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun AssistantTableRow(leftLabel: String, leftValue: String, rightLabel: String, rightValue: String) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, AppBorder, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 8.dp)
    ) {
        Text(leftLabel, Modifier.weight(1f), fontSize = 10.sp, color = AppMuted)
        Text(leftValue, Modifier.weight(1f), fontSize = 11.sp, color = AppText, fontWeight = FontWeight.Bold)
        Text(rightLabel, Modifier.weight(1f), fontSize = 10.sp, color = AppMuted, textAlign = TextAlign.End)
        Text(rightValue, Modifier.weight(1f), fontSize = 11.sp, color = Green, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
private fun AssistantInput(
    value: String,
    placeholder: String,
    keyboard: KeyboardType,
    modifier: Modifier,
    onValueChange: (String) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = TextStyle(fontSize = 11.sp, color = AppText),
        modifier = modifier.height(44.dp).border(1.dp, AppBorder, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 12.dp),
        decorationBox = { inner ->
            if (value.isBlank()) Text(placeholder, fontSize = 9.sp, color = AppMuted)
            inner()
        }
    )
}
