package com.add.pepers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun SupabaseAuthScreen(
    onAuthenticated: (SupabaseSession) -> Unit,
    onContinueOffline: () -> Unit,
) {
    var signUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("حساب الخياطين", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (signUp) "أنشئ حسابًا لمزامنة بياناتك بين أجهزتك" else "سجّل الدخول لمزامنة بياناتك بأمان",
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        OutlinedTextField(email, { email = it; error = "" }, label = { Text("البريد الإلكتروني") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(password, { password = it; error = "" }, label = { Text("كلمة المرور") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
        Button(
            onClick = {
                if (email.isBlank() || password.length < 6) { error = "أدخل بريدًا صحيحًا وكلمة مرور من 6 أحرف على الأقل"; return@Button }
                busy = true
                scope.launch {
                    try {
                        val session = if (signUp) SupabaseAuth.signUp(context, email.trim(), password) else SupabaseAuth.signIn(context, email.trim(), password)
                        if (session == null) error = "تم إنشاء الحساب. افتح رسالة تأكيد البريد ثم سجّل الدخول." else onAuthenticated(session)
                    } catch (e: Exception) { error = e.message ?: "تعذر الاتصال بـ Supabase" } finally { busy = false }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
        ) { if (busy) CircularProgressIndicator() else Text(if (signUp) "إنشاء الحساب" else "تسجيل الدخول") }
        TextButton(onClick = { signUp = !signUp; error = "" }, enabled = !busy) { Text(if (signUp) "لدي حساب بالفعل" else "إنشاء حساب جديد") }
        OutlinedButton(onClick = onContinueOffline, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("متابعة دون حساب") }
        Text("يمكنك استخدام SQLite محليًا دون إنترنت، ثم تسجيل الدخول لاحقًا للمزامنة.", modifier = Modifier.padding(top = 12.dp))
    }
}