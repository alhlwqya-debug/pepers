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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    onNavigateToProfile: () -> Unit,
    onStatistics: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onAssistants: () -> Unit,
    onDeleteShop: () -> Unit
) {
    val context = LocalContext.current
    val currentShop = shops.firstOrNull { it.id == selectedShopId }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showShopSettings by remember { mutableStateOf(false) }
    var showAppSettings by remember { mutableStateOf(false) }
    var showSecuritySettings by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var appPinEnabled by remember { mutableStateOf(hasAppPin(context)) }

    val backupCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val ok = exportBackup(context, uri)
            android.widget.Toast.makeText(
                context,
                if (ok) "تم إنشاء النسخة الاحتياطية بنجاح" else "تعذر إنشاء النسخة الاحتياطية",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val ok = restoreBackup(context, uri)
            android.widget.Toast.makeText(
                context,
                if (ok) "تمت الاستعادة. يفضل إعادة فتح التطبيق." else "تعذر استعادة النسخة الاحتياطية",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier.fillMaxHeight().background(AppSurface)
        ) {
            DrawerHeader(onClose = onClose)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ProfileCard(
                        userName = userName,
                        userImagePath = userImagePath,
                        currentShop = currentShop,
                        onClick = onNavigateToProfile
                    )
                }

                item { DrawerSectionTitle("المحل الحالي") }

                item {
                    CurrentShopCard(
                        shop = currentShop,
                        hasAnyShop = shops.isNotEmpty(),
                        onChoose = { currentShop?.let { onSelectShop(it.id) } },
                        onAddShop = onAddShop
                    )
                }

                if (shops.isNotEmpty()) {
                    item { DrawerSectionTitle("محلاتي") }
                    items(shops, key = { it.id }) { shop ->
                        ShopItem(
                            shop = shop,
                            selected = shop.id == selectedShopId,
                            onClick = { onSelectShop(shop.id) }
                        )
                    }
                }

                item {
                    DrawerPrimaryButton(
                        text = "إضافة محل جديد",
                        icon = Icons.Default.Add,
                        onClick = onAddShop
                    )
                }

                item { DrawerSectionTitle("إدارة المحل") }

                item {
                    DrawerTwoColumnRow(
                        left = {
                            DrawerMenuCard(
                                title = "إعدادات المحل",
                                subtitle = "بيانات وطريقة التسجيل",
                                icon = Icons.Default.Edit,
                                enabled = currentShop != null,
                                onClick = { if (currentShop != null) showShopSettings = true }
                            )
                        },
                        right = {
                            DrawerMenuCard(
                                title = "مساعدو العمال",
                                subtitle = "إدارة المساعدين",
                                icon = Icons.Default.Person,
                                enabled = currentShop != null,
                                onClick = onAssistants
                            )
                        }
                    )
                }

                item { DrawerSectionTitle("الوصول السريع") }

                item {
                    DrawerTwoColumnRow(
                        left = {
                            DrawerMenuCard(
                                title = "الإحصائيات",
                                subtitle = "ملخص وأرقام",
                                icon = Icons.Default.Menu,
                                onClick = onStatistics
                            )
                        },
                        right = {
                            DrawerMenuCard(
                                title = "إعدادات التطبيق",
                                subtitle = "التخصيص والأمان",
                                icon = Icons.Default.Edit,
                                onClick = { showAppSettings = true }
                            )
                        }
                    )
                }

                item { DrawerSectionTitle("المساعدة") }

                item {
                    DrawerTwoColumnRow(
                        left = {
                            DrawerMenuCard(
                                title = "دليل الاستخدام",
                                subtitle = "كيف تستخدم التطبيق؟",
                                icon = Icons.Default.Person,
                                onClick = onHelp
                            )
                        },
                        right = {
                            DrawerMenuCard(
                                title = "من نحن",
                                subtitle = "معلومات عن التطبيق",
                                icon = Icons.Default.Person,
                                onClick = onAbout
                            )
                        }
                    )
                }

                item {
                    HorizontalDivider(
                        color = AppBorder,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                item { DrawerSectionTitle("الحساب") }

                item {
                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Red),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Close, null, tint = Red, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "تسجيل الخروج وتبديل الحساب",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Right,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                item {
                    Text(
                        "جميع الحقوق محفوظة © 2026",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        fontSize = 9.sp,
                        color = AppMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showAppSettings) {
        AppSettingsDialog(
            onDismiss = { showAppSettings = false },
            onUserProfile = { showAppSettings = false; onNavigateToProfile() },
            onSecuritySettings = { showAppSettings = false; showSecuritySettings = true },
            onBackupSettings = { showAppSettings = false; showSecuritySettings = true },
            onAbout = { showAppSettings = false; onAbout() },
            onHelp = { showAppSettings = false; onHelp() }
        )
    }

    if (showSecuritySettings) {
        SecuritySettingsDialog(
            context = context,
            hasPin = appPinEnabled,
            onDismiss = { showSecuritySettings = false },
            onSetPin = { showSetPinDialog = true },
            onRemovePin = {
                clearAppPin(context)
                appPinEnabled = false
            },
            onBackup = {
                backupCreateLauncher.launch("pepers_backup_${System.currentTimeMillis()}.zip")
            },
            onRestore = {
                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            }
        )
    }

    if (showSetPinDialog) {
        SetPinDialog(
            onDismiss = { showSetPinDialog = false },
            onSaved = { pin ->
                setAppPin(context, pin)
                appPinEnabled = true
                showSetPinDialog = false
            }
        )
    }

    if (showShopSettings) {
        DrawerShopSettingsDialog(
            shop = currentShop,
            onDismiss = { showShopSettings = false },
            onRegistrationSettings = {
                showShopSettings = false
                onSettings()
            },
            onDelete = {
                showShopSettings = false
                onDeleteShop()
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("تسجيل الخروج", fontWeight = FontWeight.Bold) },
            text = {
                Text("سيتم حفظ بيانات هذا الحساب على الجهاز ثم تسجيل الخروج. عند تسجيل الدخول بحساب آخر سيتم تحميل بياناته الخاصة فقط.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onClose()
                        runCatching {
                            SupabaseAuthRepository(context.applicationContext).clearSession()
                            (context as? Activity)?.recreate()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("تسجيل الخروج") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun DrawerHeader(onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("القائمة الرئيسية", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Purple)
                Text("كل أدواتك في مكان واحد", fontSize = 10.sp, color = AppMuted)
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(AppSurfaceAlt)
            ) {
                Icon(Icons.Default.Close, "إغلاق القائمة", tint = Purple)
            }
        }
        HorizontalDivider(color = AppBorder)
    }
}

@Composable
private fun ProfileCard(
    userName: String,
    userImagePath: String,
    currentShop: ShopRecord?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppPrimarySoft)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(AppSurface),
                contentAlignment = Alignment.Center
            ) {
                if (userImagePath.isNotBlank()) {
                    LocalProfileImage(
                        path = userImagePath,
                        contentDescription = "الصورة الشخصية",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Person, null, tint = Purple, modifier = Modifier.size(29.dp))
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (userName.isBlank()) "مستخدم" else userName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Purple,
                    maxLines = 1
                )
                Text(
                    currentShop?.name ?: "لا يوجد محل محدد",
                    fontSize = 11.sp,
                    color = AppMuted,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                Text("عرض وتعديل الملف الشخصي", fontSize = 9.sp, color = Purple)
            }
        }
    }
}

@Composable
private fun CurrentShopCard(
    shop: ShopRecord?,
    hasAnyShop: Boolean,
    onChoose: () -> Unit,
    onAddShop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurfaceAlt)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AppPrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Menu, null, tint = Purple, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("المحل الحالي", fontSize = 10.sp, color = AppMuted)
                Text(
                    shop?.name ?: if (hasAnyShop) "اختر محلًا من القائمة" else "لم تتم إضافة أي محل",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppText,
                    maxLines = 1
                )
            }
            if (!hasAnyShop) {
                TextButton(onClick = onAddShop) { Text("إضافة") }
            } else if (shop != null) {
                TextButton(onClick = onChoose) { Text("الحالي") }
            }
        }
    }
}

@Composable
private fun ShopItem(shop: ShopRecord, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) AppPrimarySoft else AppSurface)
            .border(1.dp, if (selected) Purple else AppBorder, RoundedCornerShape(13.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Menu, null, tint = if (selected) Purple else AppMuted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            shop.name,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Purple else AppText,
            maxLines = 1
        )
        if (selected) Text("الحالي", fontSize = 9.sp, color = Purple, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DrawerSectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 1.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = AppMuted,
        textAlign = TextAlign.Right
    )
}

@Composable
private fun DrawerPrimaryButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Purple)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AppSurface, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(9.dp))
            Text(text, Modifier.weight(1f), color = AppSurface, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Right)
        }
    }
}

@Composable
private fun DrawerTwoColumnRow(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}

@Composable
private fun DrawerMenuCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) AppSurface else AppSurfaceAlt)
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = if (enabled) Purple else AppMuted, modifier = Modifier.size(21.dp))
            Column {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (enabled) AppText else AppMuted, maxLines = 1)
                Text(subtitle, fontSize = 8.sp, color = AppMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DrawerShopSettingsDialog(
    shop: ShopRecord?,
    onDismiss: () -> Unit,
    onRegistrationSettings: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات المحل", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Card(
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AppPrimarySoft)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("المحل", fontSize = 10.sp, color = AppMuted)
                        Text(shop?.name ?: "لا يوجد محل محدد", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppText)
                        if (shop != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (shop.registrationMode == RegistrationMode.INDIVIDUAL) "التسجيل الفردي" else "التسجيل العددي",
                                fontSize = 10.sp,
                                color = Purple
                            )
                        }
                    }
                }
                if (shop != null) {
                    OutlinedButton(onClick = onRegistrationSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("إعدادات التسجيل")
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                    ) { Text("حذف هذا المحل") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )

    if (confirmDelete && shop != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف المحل", fontWeight = FontWeight.Bold) },
            text = { Text("سيتم حذف المحل من البيانات المحلية. تأكد من أنك تريد تنفيذ هذا الإجراء.") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } }
        )
    }
}
