package com.add.pepers

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.add.pepers.cloud.AuthResult
import com.add.pepers.cloud.SupabaseAuthRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun AssistantDailyWorkForm(repository: SupabaseAuthRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Calendar.getInstance().time) }
    var date by remember { mutableStateOf(today) }
    var quantity by remember { mutableStateOf("") }
    var expense by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("تسجيل عمل اليوم", fontSize = 14.sp)
        Text("سيُحفظ في السجل المشترك ويظهر للخياط للموافقة. لا يُنشأ سجل ثانٍ لنفس اليوم.", fontSize = 10.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("التاريخ") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.filter(Char::isDigit) },
                label = { Text("القطع") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = expense,
            onValueChange = { expense = it.filter(Char::isDigit) },
            label = { Text("السحبية / المصروف") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("ملاحظة") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = !saving && date.isNotBlank(),
            onClick = {
                saving = true
                scope.launch {
                    val result = repository.saveAssistantDailyWork(
                        date = date.trim(),
                        quantity = quantity.toIntOrNull() ?: 0,
                        expense = expense.toIntOrNull() ?: 0,
                        note = note.trim()
                    )
                    saving = false
                    when (result) {
                        is AuthResult.SignedIn -> {
                            Toast.makeText(context, "تم إرسال سجل اليوم للخياط للموافقة", Toast.LENGTH_LONG).show()
                            quantity = ""
                            expense = ""
                            note = ""
                        }
                        is AuthResult.Failure -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(if (saving) "جارٍ الحفظ…" else "حفظ سجل اليوم")
        }
    }
}
