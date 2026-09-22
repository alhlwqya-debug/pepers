package com.add.pepers

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onBackupSettings: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var reminderEnabled by remember {
        mutableStateOf(prefs.getBoolean(DAILY_REMINDER_KEY, true))
    }
    var updateState by remember { mutableStateOf("لم يتم التحقق بعد") }
    var checkingUpdate by remember { mutableStateOf(false) }
    val currentVersion = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.removePrefix("v") ?: "غير معروف"
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
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
                    if (connection.responseCode !in 200..299) {
                        error("HTTP ${connection.responseCode}")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    Regex("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                        .find(body)?.groupValues?.get(1)?.removePrefix("v")
                        ?: error("لم يتم العثور على رقم الإصدار")
                } finally {
                    connection.disconnect()
                }
            }
        }
        checkingUpdate = false
        result.onSuccess { latest ->
            updateState = if (latest != currentVersion) {
                "يتوفر إصدار أحدث: $latest"
            } else {
                "أنت تستخدم أحدث إصدار"
            }
        }.onFailure {
            updateState = "تعذر التحقق الآن. تحقق من اتصال الإنترنت."
        }
    }

    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.90f),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFFF9F6FC),
                tonalElevation = 8.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    SettingsTopBar(onDismiss)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        item { SettingsSectionTitle("الحساب") }
                        item {
                            SettingsRow(
                                icon = Icons.Filled.Person,
                                title = "ملفي الشخصي",
                                description = "الاسم ورقم الهاتف والبريد ومعلومات الملف",
                                onClick = { onUserProfile(); onDismiss() }
                            )
                        }

                        item { SettingsSectionTitle("البيانات والمزامنة") }
                        item {
                            SettingsRow(
                                icon = Icons.Filled.Refresh,
                                title = "المزامنة السحابية",
                                description = "مزامنة البيانات تلقائيًا عند توفر الشبكة",
                                onClick = {
                                    BackgroundSyncScheduler.requestNow(context)
                                    onDismiss()
                                }
                            )
                        }

                        item { SettingsSectionTitle("الأمان والبيانات المحلية") }
                        item {
                            SettingsRow(
                                icon = Icons.Filled.Lock,
                                title = "الأمان",
                                description = "قفل التطبيق والبصمة أو قفل الجهاز ورمز PIN",
                                onClick = { onSecuritySettings(); onDismiss() }
                            )
                        }
                        item {
                            SettingsRow(
                                icon = Icons.Filled.Info,
                                title = "النسخ الاحتياطي والاستعادة",
                                description = "إنشاء نسخة احتياطية أو استعادة بيانات المحل",
                                onClick = { onBackupSettings(); onDismiss() }
                            )
                        }

                        item { SettingsSectionTitle("الإشعارات والتذكير") }
                        item {
                            SettingsToggleRow(
                                icon = Icons.Filled.Notifications,
                                title = "التذكير اليومي",
                                description = "تنبيه إذا لم يتم تسجيل أي عمل لليوم، افتراضيًا الساعة 8 مساءً.",
                                checked = reminderEnabled,
                                onCheckedChange = {
                                    reminderEnabled = it
                                    prefs.edit().putBoolean(DAILY_REMINDER_KEY, it).apply()
                                    if (it) {
                                        WorkReminderScheduler.schedule(context)
                                    } else {
                                        WorkReminderScheduler.cancel(context)
                                    }
                                }
                            )
                        }

                        item { SettingsSectionTitle("المساعدة والمعلومات") }
                        item {
                            SettingsRow(
                                icon = Icons.Filled.Info,
                                title = "مساعدة ودليل الاستخدام",
                                description = "تعرف على وظائف التطبيق وطريقة الاستخدام",
                                onClick = { onHelp(); onDismiss() }
                            )
                        }
                        item {
                            SettingsRow(
                                icon = Icons.Filled.Info,
                                title = "من نحن",
                                description = "فكرة التطبيق وتطويره ومعلومات الملكية",
                                onClick = { onAbout(); onDismiss() }
                            )
                        }
                        item {
                            SettingsRow(
                                icon = Icons.Filled.Info,
                                title = "تواصل حول التطبيق",
                                description = "فتح صفحة الدعم والمشكلات في GitHub",
                                onClick = { openUrl(ISSUES_URL) }
                            )
                        }
                        item {
                            SettingsRow(
                                icon = Icons.Filled.Info,
                                title = "التراخيص والملكية",
                                description = "عرض ترخيص المشروع ومعلومات المكونات",
                                onClick = { openUrl(LICENSE_URL) }
                            )
                        }

                        item { SettingsSectionTitle("التحديثات والمشاريع") }
                        item {
                            SettingsRow(
                                icon = Icons.Filled.Refresh,
                                title = "التحقق من وجود تحديث",
                                description = updateState,
                                onClick = {
                                    if (!checkingUpdate) {
                                        checkingUpdate = true
                                        updateState = "جارٍ التحقق من آخر إصدار…"
                                    }
                                },
                                trailing = {
                                    if (checkingUpdate) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp,
                                            color = Purple
                                        )
                                    }
                                }
                            )
                        }
                        item {
                            SettingsRow(
                                icon = Icons.Filled.Info,
                                title = "المزيد من التطبيقات",
                                description = "استعراض مشاريع وتطبيقات المطور",
                                onClick = { openUrl(REPOSITORIES_URL) }
                            )
                        }

                        item {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = Color(0xFFE2DCE7))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Pepers",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Purple
                            )
                            Text(
                                "الإصدار $currentVersion",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                "فكرة وتطوير المهندس أحمد عبدالودود الدبعي",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Purple
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF9F6FC))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple)
                        ) {
                            Text("إغلاق", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss, modifier = Modifier.size(42.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = "إغلاق",
                tint = Purple,
                modifier = Modifier.size(25.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                "إعدادات التطبيق",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF29232F)
            )
            Text(
                "الحساب والبيانات والأمان والمساعدة",
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
    HorizontalDivider(color = Color(0xFFE2DCE7))
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 11.dp, bottom = 3.dp, end = 4.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Purple,
        textAlign = TextAlign.Right
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            trailing?.invoke()
            Spacer(Modifier.size(6.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF29232F),
                    textAlign = TextAlign.Right
                )
                Text(
                    description,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Right
                )
            }
            Spacer(Modifier.size(11.dp))
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(13.dp),
                color = Purple.copy(alpha = 0.10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Purple,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
            Spacer(Modifier.size(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF29232F),
                    textAlign = TextAlign.Right
                )
                Text(
                    description,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Right
                )
            }
            Spacer(Modifier.size(11.dp))
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(13.dp),
                color = Purple.copy(alpha = 0.10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Purple,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
