package com.add.pepers

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.add.pepers.cloud.SupabaseAuthRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun DrawerContent(
    modifier: Modifier,
    shops: List<ShopRecord>,
    selectedShopId: Long?,
    userName: String,
    userImagePath: String,
    onClose: () -> Unit,
    onSelectShop: (Long) -> Unit,
    onAddShop: () -> Unit,
    onUserProfile: () -> Unit,
    onStatistics: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onDeleteShop: () -> Unit
) {
    val context = LocalContext.current
    val currentShop = shops.firstOrNull { it.id == selectedShopId }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showShopSettings by remember { mutableStateOf(false) }
    var showAppSettings by remember { mutableStateOf(false) }
    var showSecuritySettings by remember { mutableStateOf(false) }
    var showBackupSettings by remember { mutableStateOf(false) }
    var appPinEnabled by remember { mutableStateOf(hasAppPin(context)) }
    var showSetPinDialog by remember { mutableStateOf(false) }

    val backupCreateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val ok = exportBackup(context, uri)
            android.widget.Toast.makeText(context, if (ok) "تم إنشاء النسخة الاحتياطية بنجاح" else "تعذر إنشاء النسخة الاحتياطية", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = restoreBackup(context, uri)
            android.widget.Toast.makeText(context, if (ok) "تمت الاستعادة. يفضل إعادة فتح التطبيق." else "تعذر استعادة النسخة الاحتياطية", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier.fillMaxHeight().background(AppSurface).verticalScroll(rememberScrollState()).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Menu, "القائمة", tint = Purple, modifier = Modifier.size(25.dp))
                    Text("القائمة الرئيسية", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Purple)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp).clip(CircleShape).background(AppSurfaceAlt)) { Icon(Icons.Default.Close, "إغلاق", tint = Purple) }
            }
            HorizontalDivider(color = AppBorder, modifier = Modifier.padding(bottom = 10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppPrimarySoft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(AppSurface), contentAlignment = Alignment.Center) {
                    if (userImagePath.isNotBlank()) LocalProfileImage(path = userImagePath, contentDescription = "الصورة الشخصية", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.Person, null, tint = Purple, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(if (userName.isBlank()) "مستخدم غير مسجل" else userName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Purple, maxLines = 1)
                    Text(currentShop?.name ?: "لم يتم اختيار محل", fontSize = 12.sp, color = AppMuted, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp)); DrawerSectionTitle("المحلات"); Spacer(Modifier.height(7.dp))
            if (shops.isEmpty()) EmptyDrawerState("لا توجد محلات مضافة") else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shops, key = { it.id }) { shop ->
                    val selected = shop.id == selectedShopId
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(if (selected) AppPrimarySoft else AppSurfaceAlt).border(1.dp, if (selected) Purple else AppBorder, RoundedCornerShape(13.dp)).clickable { onSelectShop(shop.id) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        DrawerGlyph("🏪", if (selected) Purple else AppMuted); Spacer(Modifier.width(9.dp)); Text(shop.name, Modifier.weight(1f), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) Purple else AppText, maxLines = 1); if (selected) Text("الحالي", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Purple)
                    }
                }
            }
            Spacer(Modifier.height(7.dp)); DrawerActionButton(text = "إضافة محل جديد", icon = "＋", tint = Purple, onClick = onAddShop, outlined = false)
            Spacer(Modifier.height(7.dp)); DrawerActionButton(text = "إعدادات المحل", icon = "⚙", tint = AppText, onClick = { showShopSettings = true }, outlined = true)
            Spacer(Modifier.height(13.dp)); DrawerSectionTitle("الوصول السريع"); Spacer(Modifier.height(6.dp))
            DrawerActionButton(text = "ملفي الشخصي", icon = "👤", tint = Purple, onClick = onUserProfile, outlined = false)
            Spacer(Modifier.height(6.dp)); DrawerActionButton(text = "إعدادات التطبيق", icon = "⚙", tint = AppText, onClick = { showAppSettings = true }, outlined = true)
            Spacer(Modifier.height(6.dp)); DrawerActionButton(text = "الإحصائيات", icon = "▥", tint = AppText, onClick = onStatistics, outlined = true)
            Spacer(Modifier.height(13.dp)); DrawerSectionTitle("المساعدة والمعلومات"); Spacer(Modifier.height(6.dp))
            DrawerActionButton(text = "دليل الاستخدام", icon = "؟", tint = AppText, onClick = onHelp, outlined = true)
            Spacer(Modifier.height(6.dp)); DrawerActionButton(text = "من نحن", icon = "ⓘ", tint = AppText, onClick = onAbout, outlined = true)
            Spacer(Modifier.height(13.dp)); HorizontalDivider(color = AppBorder, modifier = Modifier.padding(bottom = 10.dp)); DrawerSectionTitle("الحساب"); Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { showSignOutDialog = true }, modifier = Modifier.fillMaxWidth().height(44.dp), shape = AppButtonShape, border = androidx.compose.foundation.BorderStroke(1.dp, Red), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { DrawerGlyph("↪", Red); Spacer(Modifier.width(8.dp)); Text("تسجيل الخروج وتبديل الحساب", color = Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Right) }
            }
            Spacer(Modifier.height(12.dp)); Text("جميع الحقوق محفوظة © 2026", fontSize = 9.sp, color = AppMuted, textAlign = TextAlign.Center)
        }
    }

    if (showAppSettings) {
        AppSettingsDialog(
            onDismiss = { showAppSettings = false },
            onUserProfile = { showAppSettings = false; onUserProfile() },
            onSecuritySettings = { showAppSettings = false; showSecuritySettings = true },
            onBackupSettings = { showAppSettings = false; showBackupSettings = true },
            onAbout = { showAppSettings = false; onAbout() },
            onHelp = { showAppSettings = false; onHelp() }
        )
    }
    if (showSecuritySettings) {
        SecurityOnlySettingsDialog(context, appPinEnabled, { showSecuritySettings = false }, { showSetPinDialog = true }, { clearAppPin(context); appPinEnabled = false })
    }
    if (showBackupSettings) {
        BackupOnlySettingsDialog(
            context = context,
            onDismiss = { showBackupSettings = false },
            onBackup = { backupCreateLauncher.launch("pepers_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.zip") },
            onRestore = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
        )
    }
    if (showSetPinDialog) {
        SetPinDialog(onDismiss = { showSetPinDialog = false }, onSaved = { pin ->
            setAppPin(context, pin); appPinEnabled = true; showSetPinDialog = false
            android.widget.Toast.makeText(context, "تم تفعيل قفل التطبيق", android.widget.Toast.LENGTH_SHORT).show()
        })
    }
    if (showShopSettings) {
        ShopSettingsDialog(
            shop = currentShop,
            onDismiss = { showShopSettings = false },
            onDelete = { showShopSettings = false; onDeleteShop() },
            onRegistrationModeChanged = { shopId -> showShopSettings = false; onSelectShop(shopId) }
        )
    }
    if (showSignOutDialog) AlertDialog(onDismissRequest = { showSignOutDialog = false }, title = { Text("تسجيل الخروج", fontWeight = FontWeight.Bold) }, text = { Text("سيتم حفظ بيانات هذا الحساب على الجهاز ثم تسجيل الخروج. عند تسجيل الدخول بحساب آخر سيتم تحميل بياناته الخاصة فقط.") }, confirmButton = { Button(onClick = { showSignOutDialog = false; onClose(); runCatching { SupabaseAuthRepository(context.applicationContext).clearSession(); (context as? Activity)?.recreate() } }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("تسجيل الخروج") } }, dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text("إلغاء") } })
}

@Composable private fun DrawerSectionTitle(text: String) { Text(text, Modifier.fillMaxWidth().padding(horizontal = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppMuted, textAlign = TextAlign.Right) }
@Composable private fun EmptyDrawerState(text: String) { Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(AppSurfaceAlt).border(1.dp, AppBorder, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(text, fontSize = 11.sp, color = Color.Gray) } }
@Composable private fun DrawerGlyph(icon: String, tint: Color) { Text(icon, color = tint, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.size(24.dp)) }

@Composable
private fun DrawerActionButton(text: String, icon: String, tint: Color, onClick: () -> Unit, outlined: Boolean) {
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(44.dp), shape = AppButtonShape, border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder), colors = ButtonDefaults.outlinedButtonColors(contentColor = AppText)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { DrawerGlyph(icon, tint); Spacer(Modifier.width(9.dp)); Text(text, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Right) }
        }
    } else {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(44.dp), shape = AppButtonShape, colors = ButtonDefaults.buttonColors(containerColor = tint)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { DrawerGlyph(icon, AppSurface); Spacer(Modifier.width(9.dp)); Text(text, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppSurface, textAlign = TextAlign.Right) }
        }
    }
}

@Composable
private fun ShopSettingsDialog(shop: ShopRecord?, onDismiss: () -> Unit, onDelete: () -> Unit, onRegistrationModeChanged: (Long) -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var showRegistrationSettings by remember { mutableStateOf(false) }
    var selectedMode by remember(shop) { mutableStateOf(shop?.registrationMode ?: RegistrationMode.NUMERIC) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات المحل", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ShopInfoCard("اسم المحل", shop?.name ?: "لا يوجد محل محدد")
                ShopInfoCard("طريقة التسجيل", if (selectedMode == RegistrationMode.INDIVIDUAL) "تسجيل فردي" else "تسجيل عددي")
                OutlinedButton(onClick = { showRegistrationSettings = true }, enabled = shop != null, modifier = Modifier.fillMaxWidth()) { Text("تغيير طريقة التسجيل") }
                Text("هذا الإعداد خاص بالمحل الحالي، وسيتم حفظه في بيانات المحل ليبقى بعد إغلاق التطبيق.", fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Right)
                Text("منطقة العمليات الخطرة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red)
                Text("حذف المحل سيزيل بياناته المحلية. خيار الحذف غير موجود في القائمة الرئيسية لتقليل الحذف بالخطأ.", fontSize = 10.sp, color = Color.Gray)
                OutlinedButton(onClick = { confirmDelete = true }, enabled = shop != null, modifier = Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, Red), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)) { Text("حذف المحل الحالي") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
    if (showRegistrationSettings && shop != null) {
        RegistrationSettingsDialog(
            currentMode = selectedMode,
            onDismiss = { showRegistrationSettings = false },
            onSecurity = { showRegistrationSettings = false },
            onSave = { mode ->
                Database(context.applicationContext).apply { updateShopRegistrationMode(shop.id, mode); close() }
                selectedMode = mode
                showRegistrationSettings = false
                onRegistrationModeChanged(shop.id)
            }
        )
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("تأكيد حذف المحل", fontWeight = FontWeight.Bold) }, text = { Text("هل أنت متأكد من حذف «${shop?.name ?: "المحل"}»؟ هذا الإجراء قد يحذف سجلاته المرتبطة ولا يمكن التراجع عنه.") }, confirmButton = { Button(onClick = { confirmDelete = false; onDelete() }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("حذف نهائي") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } })
}

@Composable private fun ShopInfoCard(title: String, value: String) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppSurfaceAlt).padding(11.dp)) { Text(title, fontSize = 10.sp, color = AppMuted); Spacer(Modifier.size(2.dp)); Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppText) } }
