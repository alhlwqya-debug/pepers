package com.add.pepers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val PREFS = "add_paper_user"
private const val DAILY_REMINDER_KEY = "daily_work_reminder_enabled"
private const val LATEST_RELEASE_API = "https://api.github.com/repos/alhlwqya-debug/pepers/releases/latest"
private const val REPOSITORIES_URL = "https://github.com/alhlwqya-debug?tab=repositories"
private const val ISSUES_URL = "https://github.com/alhlwqya-debug/pepers/issues"
private const val LICENSE_URL = "https://github.com/alhlwqya-debug/pepers/blob/main/LICENSE"

@Composable
internal fun AppSettingsDialog(
    onDismiss: () -> Unit,
    onUserProfile: () -> Unit,
    onSecuritySettings: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }
    var reminderEnabled by remember { mutableStateOf(prefs.getBoolean(DAILY_REMINDER_KEY, true)) }
    var updateState by remember { mutableStateOf("لم يتم التحقق بعد") }
    var checkingUpdate by remember { mutableStateOf(false) }

    // Read the installed version through PackageManager instead of BuildConfig.
    // This also works reliably in lightweight IDEs such as CodeAssist where
    // generated BuildConfig sources may not be indexed during compilation.
    val currentVersion = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.removePrefix("v") ?: "غير معروف"
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    LaunchedEffect(checkingUpdate) {
        if (!checkingUpdate) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Pepers-App")
                }
                try {
                    if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val tag = Regex("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.get(1)
                        ?: error("لم يتم العثور على رقم الإصدار")
                    tag.removePrefix("v")
                } finally {
                    connection.disconnect()
                }
            }
        }
        checkingUpdate = false
        result.onSuccess { latest ->
            updateState = if (latest != currentVersion) {
                "يتوفر إصدار أحدث: $latest (الإصدار الحالي $currentVersion)"
            } else {
                "أنت تستخدم أحدث إصدار ($currentVersion)"
            }
        }.onFailure {
            updateState = "تعذر التحقق الآن. تحقق من اتصال الإنترنت."
        }
    }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column(Modifier.fillMaxWidth()) {
                    Text("إعدادات التطبيق", Modifier.fillMaxWidth(), textAlign = TextAlign.Right, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.size(4.dp))
                    Text("إدارة الحساب والبيانات والتنبيهات والأمان والمساعدة ومعلومات التطبيق.", Modifier.fillMaxWidth(), textAlign = TextAlign.Right, color = Color.Gray, fontSize = 12.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsGroupTitle("الحساب")
                    SettingsAction("👤", "ملفي الشخصي", "الاسم ورقم الهاتف والبريد ومعلومات الملف") { onUserProfile(); onDismiss() }

                    SettingsGroupTitle("البيانات والمزامنة")
                    SettingsAction("☁", "المزامنة السحابية", "تعمل تلقائيًا عند توفر الشبكة وفي الخلفية") { BackgroundSyncScheduler.requestNow(context); onDismiss() }
                    SettingsAction("🔐", "الأمان والنسخ الاحتياطي", "قفل التطبيق والنسخ والاستعادة") { onSecuritySettings(); onDismiss() }

                    SettingsGroupTitle("الإشعارات والتذكير")
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7FA))) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("التذكير اليومي", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("تنبيه إذا لم يتم تسجيل أي عمل لليوم (افتراضيًا الساعة 8 مساءً).", color = Color.Gray, fontSize = 10.sp)
                            }
                            Switch(checked = reminderEnabled, onCheckedChange = {
                                reminderEnabled = it
                                prefs.edit().putBoolean(DAILY_REMINDER_KEY, it).apply()
                                if (it) WorkReminderScheduler.schedule(context) else WorkReminderScheduler.cancel(context)
                            })
                        }
                    }

                    SettingsGroupTitle("المساعدة والمعلومات")
                    SettingsAction("❓", "مساعدة ودليل الاستخدام", "تعرف على وظائف التطبيق وطريقة الاستخدام") { onHelp(); onDismiss() }
                    SettingsAction("💬", "تواصل حول التطبيق", "فتح صفحة الدعم والمشكلات في GitHub") { openUrl(ISSUES_URL) }
                    SettingsAction("ℹ", "من نحن", "فكرة التطبيق وتطويره ومعلومات الملكية") { onAbout(); onDismiss() }
                    SettingsAction("📜", "التراخيص والملكية", "عرض ترخيص المشروع ومعلومات المكونات") { openUrl(LICENSE_URL) }

                    SettingsGroupTitle("التحديثات والمشاريع")
                    SettingsAction("↻", "التحقق من وجود تحديث", updateState) { if (!checkingUpdate) { checkingUpdate = true; updateState = "جارٍ التحقق من آخر إصدار…" } }
                    if (checkingUpdate) CircularProgressIndicator(modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
                    SettingsAction("▦", "المزيد من التطبيقات", "استعراض مشاريع وتطبيقات المطور") { openUrl(REPOSITORIES_URL) }

                    HorizontalDivider(Modifier.padding(top = 4.dp))
                    Text("Pepers — الإصدار $currentVersion", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp, color = Color.Gray)
                    Text("فكرة وتطوير المهندس أحمد عبدالودود الدبعي", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Purple)
                    Text("الاسم والهوية والتصميم والمساهمات الأصلية تخضع للحقوق والتراخيص المبينة في المشروع، بينما تبقى مكونات الطرف الثالث خاضعة لتراخيصها الخاصة.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 9.sp, color = Color.Gray, lineHeight = 14.sp)
                }
            },
            confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("إغلاق") } }
        )
    }
}

@Composable
private fun SettingsGroupTitle(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Purple, textAlign = TextAlign.Right)
}

@Composable
private fun SettingsAction(icon: String, title: String, description: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7FA))) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 20.sp, modifier = Modifier.size(28.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.size(9.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Right)
                Text(description, fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Right)
            }
        }
    }
}
