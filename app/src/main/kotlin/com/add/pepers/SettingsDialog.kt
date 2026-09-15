package com.add.pepers

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val REPOSITORY_URL = "https://github.com/alhlwqya-debug/pepers"
private const val RELEASES_URL = "https://github.com/alhlwqya-debug/pepers/releases"
private const val LATEST_RELEASE_API = "https://api.github.com/repos/alhlwqya-debug/pepers/releases/latest"
private const val PROJECTS_URL = "https://github.com/alhlwqya-debug"

@Composable
internal fun RegistrationSettingsDialog(
    currentMode: RegistrationMode,
    onDismiss: () -> Unit,
    onSave: (RegistrationMode) -> Unit,
    onSecurity: () -> Unit
) {
    var selectedMode by remember(currentMode) { mutableStateOf(currentMode) }
    var showApps by remember { mutableStateOf(false) }
    var showUpdates by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات التطبيق", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "إعدادات المحل وطريقة التسجيل والحماية والتحديثات في مكان واحد.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(10.dp))

                Text("طريقة التسجيل", fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                Divider()
                Spacer(Modifier.height(8.dp))

                SettingsAction(
                    title = "🔒 الحماية والنسخ الاحتياطي",
                    description = "البصمة، رمز التطبيق والنسخ الاحتياطي.",
                    onClick = onSecurity
                )
                SettingsAction(
                    title = "🔄 التحقق من وجود تحديثات",
                    description = "تحقق من أحدث إصدار منشور لتطبيق Pepers.",
                    onClick = { showUpdates = true }
                )
                SettingsAction(
                    title = "📱 تطبيقاتنا ومشاريعنا",
                    description = "تصفح تطبيقاتنا ومشاريعنا من GitHub.",
                    onClick = { showApps = true }
                )
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

    if (showApps) {
        AppsAndProjectsDialog(onDismiss = { showApps = false })
    }

    if (showUpdates) {
        UpdateCheckerDialog(onDismiss = { showUpdates = false })
    }
}

@Composable
private fun SettingsAction(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5FA))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(description, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun AppsAndProjectsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تطبيقاتنا ومشاريعنا", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectCard(
                    title = "Pepers",
                    description = "تطبيق إدارة حسابات وأعمال محلات الخياطة.",
                    onClick = { openUrl(context, REPOSITORY_URL) }
                )
                ProjectCard(
                    title = "مشاريعنا على GitHub",
                    description = "الوصول إلى بقية التطبيقات والمشاريع المنشورة.",
                    onClick = { openUrl(context, PROJECTS_URL) }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
private fun ProjectCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5FA))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Purple)
            Spacer(Modifier.height(3.dp))
            Text(description, fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(5.dp))
            Text("فتح ↗", fontSize = 11.sp, color = Purple, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UpdateCheckerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateResult?>(null) }

    fun checkNow() {
        if (checking) return
        checking = true
        result = null
        scope.launch {
            result = checkForPepersUpdate(context)
            checking = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { checkNow() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحديث Pepers", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "الإصدار المثبت: ${currentVersion()}",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 3.dp)
                    Text("جارٍ التحقق من أحدث إصدار...", fontSize = 12.sp)
                } else {
                    val state = result
                    if (state == null) {
                        Text("لم يتم إجراء التحقق بعد.", fontSize = 12.sp)
                    } else {
                        Text(
                            state.message,
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        state.releaseUrl?.let { url ->
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { openUrl(context, url) },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple)
                            ) { Text("فتح صفحة التحديث") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { checkNow() }, enabled = !checking) { Text("تحقق مرة أخرى") }
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    )
}

private data class UpdateResult(
    val message: String,
    val releaseUrl: String? = null
)

private suspend fun checkForPepersUpdate(context: Context): UpdateResult = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Pepers-Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                return@withContext UpdateResult("تعذر الوصول إلى خادم التحديث الآن. تحقق من اتصال الإنترنت.")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim().removePrefix("v")
            val url = json.optString("html_url").takeIf { it.isNotBlank() } ?: RELEASES_URL
            if (tag.isBlank()) {
                UpdateResult("لم يتم العثور على إصدار منشور حالياً.")
            } else {
                val current = currentVersion().removePrefix("v")
                if (compareVersions(tag, current) > 0) {
                    UpdateResult("يوجد تحديث جديد: الإصدار $tag (المثبت $current).", url)
                } else {
                    UpdateResult("أنت تستخدم أحدث إصدار منشور حالياً: $current.")
                }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrElse {
        UpdateResult("تعذر التحقق من التحديثات. تأكد من اتصال الإنترنت وحاول مرة أخرى.")
    }
}

private fun currentVersion(): String = BuildConfig.VERSION_NAME

private fun compareVersions(first: String, second: String): Int {
    val a = first.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
    val b = second.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
    val size = maxOf(a.size, b.size)
    for (i in 0 until size) {
        val av = a.getOrElse(i) { 0 }
        val bv = b.getOrElse(i) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
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
