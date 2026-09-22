package com.add.pepers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// استيراد الأيقونات
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close

// استيراد Coil لعرض الصور

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


// ================ شاشة البداية ================

@Composable
internal fun StartScreen(
    onAddShop: () -> Unit,
    onMenu: () -> Unit
) {
    Column(
        modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = "القائمة")
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            "📋 دفتر الحسابات",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Purple
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "سجل إنتاجك اليومي بسهولة",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(60.dp))

        Box(
            modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HeaderBlue)
            .padding(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "👋 مرحباً بك!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "ابدأ بإضافة محل العمل الخاص بك",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onAddShop,
                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("+ إضافة محل", fontSize = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            "جميع الحقوق محفوظة © 2026",
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

// ================ حالة عدم وجود شهر ================

@Composable
internal fun EmptyMonthState(
    shopName: String,
    userName: String,
    onMenu: () -> Unit,
    onAddMonth: () -> Unit
) {
    Column(
        modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = "القائمة")
            }
        }

        Spacer(Modifier.height(30.dp))

        Text(
            "🏪 $shopName",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Purple
        )

        if (userName.isNotBlank()) {
            Text(
                "👤 $userName",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        Spacer(Modifier.height(50.dp))

        Box(
            modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFF3E0))
            .padding(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "📅 لا يوجد سجل شهر بعد",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Orange
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "أنشئ سجل شهر جديد وستتولد الأيام تلقائياً",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onAddMonth,
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Text("+ إضافة سجل شهر", fontSize = 14.sp)
                }
            }
        }
    }
}

// ================ حوار الملف الشخصي ================

@Composable
internal fun UserProfileDialog(
    userName: String,
    userPhone: String,
    userEmail: String,
    userShop: String,
    userImagePath: String,
    onUserNameChange: (String) -> Unit,
    onUserPhoneChange: (String) -> Unit,
    onUserEmailChange: (String) -> Unit,
    onUserShopChange: (String) -> Unit,
    onSelectImage: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Purple)
                Spacer(Modifier.width(8.dp))
                Text("ملفي الشخصي", fontWeight = FontWeight.Bold, color = Purple)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // ===== صورة الملف الشخصي =====
                Box(
                    modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(AppPrimarySoft)
                    .clickable { onSelectImage() },
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
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = Purple
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onSelectImage) {
                    Text("📷 تغيير الصورة", fontSize = 11.sp, color = Blue)
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = userName,
                    onValueChange = onUserNameChange,
                    singleLine = true,
                    label = { Text("الاسم الكامل *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = userName.isBlank()
                )
                if (userName.isBlank()) {
                    Text(
                        "الاسم مطلوب",
                        fontSize = 10.sp,
                        color = Red,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = userPhone,
                    onValueChange = onUserPhoneChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    label = { Text("رقم الهاتف") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = userEmail,
                    onValueChange = onUserEmailChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    label = { Text("البريد الإلكتروني") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("احفظ رقم الهاتف أو البريد الإلكتروني لاستخدامهما عند مشاركة ملف PDF", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = userShop,
                    onValueChange = onUserShopChange,
                    singleLine = true,
                    label = { Text("اسم المحل (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (userName.isBlank()) {
                        Toast.makeText(context, "الاسم مطلوب", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (userName.isBlank()) "تخطي" else "إلغاء")
            }
        }
    )
}

// ================ حوار إدارة القطع (✅ جديد) ================

@Composable
internal fun PieceManagerDialog(
    pieces: List<PieceRecord>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onUpdate: (PieceRecord, String, Int) -> Unit,
    onDelete: (PieceRecord) -> Unit
) {
    var editingPiece by remember { mutableStateOf<PieceRecord?>(null) }
    var editingName by remember { mutableStateOf("") }
    var editingPrice by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = Purple)
                Spacer(Modifier.width(8.dp))
                Text("إدارة القطع", fontWeight = FontWeight.Bold, color = Purple)
            }
        },
        text = {
            Column {
                if (pieces.isEmpty()) {
                    Text("لا توجد قطع بعد.", color = Color.Gray)
                } else {
                    pieces.forEach { piece ->
                        Row(
                            modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(piece.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("السعر: ${piece.price} ريال", fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = {
                                    editingPiece = piece
                                    editingName = piece.name
                                    editingPrice = piece.price.toString()
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = Blue)
                            }
                            IconButton(onClick = { onDelete(piece) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Red)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) { Text("+ إضافة قطعة") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )

    // حوار التعديل
    if (editingPiece != null) {
        AlertDialog(
            onDismissRequest = { editingPiece = null },
            title = { Text("تعديل القطعة", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        singleLine = true,
                        label = { Text("اسم القطعة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingPrice,
                        onValueChange = { editingPrice = it.filter(Char::isDigit) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("السعر") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = editingName.trim()
                        val price = editingPrice.toIntOrNull() ?: 0
                        if (name.isNotEmpty()) {
                            onUpdate(editingPiece!!, name, price)
                            editingPiece = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { editingPiece = null }) { Text("إلغاء") }
            }
        )
    }
}

// ================ حوار الإحصائيات ================

@Composable
internal fun StatisticsDialog(
    bundle: MonthBundle,
    database: Database,
    userName: String,
    onDismiss: () -> Unit
) {
    val totalEarned = database.calculateMonthEarned(bundle)
    val totalExpenses = database.calculateMonthExpenses(bundle)
    val net = database.calculateMonthNet(bundle)
    val totalPieces = bundle.days.sumOf { day ->
        day.quantities.values.sum()
    }

    val activeDays = bundle.days.count { day ->
        day.quantities.values.sum() > 0 || day.expense > 0
    }

    val productionDays = bundle.days.count { day ->
        day.quantities.values.sum() > 0
    }

    val expenseDays = bundle.days.count { day ->
        day.expense > 0
    }

    val averageDailyProduction = if (productionDays > 0) {
        totalEarned / productionDays
    } else {
        0
    }

    val averageDailyExpense = if (expenseDays > 0) {
        totalExpenses / expenseDays
    } else {
        0
    }

    val averageDailyNet = if (activeDays > 0) {
        net / activeDays
    } else {
        0
    }

    val bestDay = bundle.days
        .filter { day -> database.calculateDayEarned(day) > 0 }
        .maxByOrNull { day -> database.calculateDayEarned(day) }

    val bestDayEarned = bestDay?.let { day ->
        database.calculateDayEarned(day)
    } ?: 0

    val worstExpenseDay = bundle.days
        .filter { day -> day.expense > 0 }
        .maxByOrNull { day -> day.expense }

    val highestExpense = worstExpenseDay?.expense ?: 0

    val shopName = database.getShops()
        .firstOrNull { shop -> shop.id == bundle.month.shopId }
        ?.name
        .orEmpty()

    val pieceStats = bundle.pieces.map { piece ->
        val quantity = database.pieceTotal(bundle, piece.id)
        val earned = database.pieceEarned(bundle, piece.id)
        Triple(piece, quantity, earned)
    }

    val activePieceStats = pieceStats.filter { (_, quantity, earned) ->
        quantity > 0 || earned > 0
    }

    val totalPossibleDays = bundle.days.size
    val activityPercent = if (totalPossibleDays > 0) {
        (activeDays * 100) / totalPossibleDays
    } else {
        0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppBackground,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Purple.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Purple,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "الإحصائيات",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Purple
                            )
                            Text(
                                text = bundle.month.name,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color.DarkGray
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (userName.isNotBlank()) {
                        StatisticsIdentityChip(
                            icon = Icons.Default.Person,
                            text = userName,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (shopName.isNotBlank()) {
                        StatisticsIdentityChip(
                            icon = Icons.Default.Person,
                            text = shopName,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    StatisticsNetCard(
                        net = net,
                        earned = totalEarned,
                        expenses = totalExpenses
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatisticsMetricCard(
                            title = "الإنتاج",
                            value = formatAmount(totalEarned),
                            subtitle = "إجمالي الإيراد",
                            icon = Icons.Default.Person,
                            background = CardWorkBg,
                            accent = Green,
                            modifier = Modifier.weight(1f)
                        )

                        StatisticsMetricCard(
                            title = "المصروفات",
                            value = formatAmount(totalExpenses),
                            subtitle = "إجمالي المصروف",
                            icon = Icons.Default.Person,
                            background = CardExpBg,
                            accent = Red,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatisticsMetricCard(
                            title = "القطع",
                            value = totalPieces.toString(),
                            subtitle = "إجمالي الكمية",
                            icon = Icons.Default.Person,
                            background = Color(0xFFFFF3E0),
                            accent = Orange,
                            modifier = Modifier.weight(1f)
                        )

                        StatisticsMetricCard(
                            title = "أيام العمل",
                            value = activeDays.toString(),
                            subtitle = "$activityPercent% من أيام السجل",
                            icon = Icons.Default.Person,
                            background = AppPrimarySoft,
                            accent = Purple,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    StatisticsSectionCard(
                        title = "مؤشرات الأداء",
                        icon = Icons.Default.Person
                    ) {
                        StatisticsInfoRow(
                            label = "متوسط الإنتاج اليومي",
                            value = "${formatAmount(averageDailyProduction)} ريال",
                            valueColor = Green
                        )
                        StatisticsInfoRow(
                            label = "متوسط المصروف اليومي",
                            value = "${formatAmount(averageDailyExpense)} ريال",
                            valueColor = Red
                        )
                        StatisticsInfoRow(
                            label = "متوسط الصافي اليومي",
                            value = "${formatSignedAmount(averageDailyNet)} ريال",
                            valueColor = if (averageDailyNet >= 0) Green else Red
                        )
                        StatisticsInfoRow(
                            label = "أيام الإنتاج",
                            value = "$productionDays يوم",
                            valueColor = Purple
                        )
                        StatisticsInfoRow(
                            label = "أيام المصروفات",
                            value = "$expenseDays يوم",
                            valueColor = Orange
                        )
                    }
                }

                item {
                    StatisticsSectionCard(
                        title = "أفضل وأعلى يوم",
                        icon = Icons.Default.Person
                    ) {
                        if (bestDay != null) {
                            StatisticsInfoRow(
                                label = "أفضل يوم إنتاج",
                                value = "${bestDay.date} — ${formatAmount(bestDayEarned)} ريال",
                                valueColor = Green
                            )
                        } else {
                            StatisticsEmptyRow("لا يوجد يوم إنتاج مسجل بعد")
                        }

                        if (worstExpenseDay != null) {
                            StatisticsInfoRow(
                                label = "أعلى مصروف",
                                value = "${worstExpenseDay.date} — ${formatAmount(highestExpense)} ريال",
                                valueColor = Red
                            )
                        } else {
                            StatisticsEmptyRow("لا توجد مصروفات مسجلة")
                        }
                    }
                }

                item {
                    StatisticsSectionCard(
                        title = "تفاصيل القطع",
                        icon = Icons.Default.Person
                    ) {
                        if (activePieceStats.isEmpty()) {
                            StatisticsEmptyRow("لا توجد قطع مسجلة لهذا الشهر")
                        } else {
                            activePieceStats.forEachIndexed { index, (piece, quantity, earned) ->
                                StatisticsPieceRow(
                                    name = piece.name,
                                    quantity = quantity,
                                    earned = earned,
                                    rank = index + 1
                                )
                            }
                        }
                    }
                }

                item {
                    StatisticsSectionCard(
                        title = "ملخص الشهر",
                        icon = Icons.Default.Person
                    ) {
                        StatisticsInfoRow(
                            label = "عدد أيام السجل",
                            value = "$totalPossibleDays يوم",
                            valueColor = Color.DarkGray
                        )
                        StatisticsInfoRow(
                            label = "طريقة احتساب المصروف",
                            value = if (bundle.month.deductExpense) {
                                "يُخصم من الصافي"
                            } else {
                                "لا يُخصم من الصافي"
                            },
                            valueColor = if (bundle.month.deductExpense) Red else Purple
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                Text(
                    text = "إغلاق",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun StatisticsIdentityChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.75f))
            .border(
                1.dp,
                Purple.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Purple,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray,
            maxLines = 1
        )
    }
}

@Composable
private fun StatisticsNetCard(
    net: Int,
    earned: Int,
    expenses: Int
) {
    val isPositive = net >= 0
    val netColor = if (isPositive) Green else Red
    val netBackground = if (isPositive) {
        Color(0xFFE8F5E9)
    } else {
        Color(0xFFFFEBEE)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(netBackground)
            .border(
                1.dp,
                netColor.copy(alpha = 0.30f),
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "الصافي النهائي",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray
        )

        Spacer(Modifier.height(3.dp))

        Text(
            text = formatSignedAmount(net),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = netColor
        )

        Text(
            text = "ريال",
            fontSize = 10.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(9.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatisticsMiniValue(
                label = "الإنتاج",
                value = formatAmount(earned),
                color = Green
            )
            StatisticsMiniValue(
                label = "المصروف",
                value = formatAmount(expenses),
                color = Red
            )
        }
    }
}

@Composable
private fun StatisticsMiniValue(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun StatisticsMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(background)
            .border(
                1.dp,
                accent.copy(alpha = 0.24f),
                RoundedCornerShape(15.dp)
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.height(5.dp))

        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )

        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accent,
            textAlign = TextAlign.Center
        )

        Text(
            text = subtitle,
            fontSize = 8.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatisticsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.78f))
            .border(
                1.dp,
                Purple.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
            .padding(11.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Purple,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Purple
            )
        }

        Spacer(Modifier.height(6.dp))

        content()
    }
}

@Composable
private fun StatisticsInfoRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.DarkGray
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun StatisticsEmptyRow(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = 10.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatisticsPieceRow(
    name: String,
    quantity: Int,
    earned: Int,
    rank: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Purple.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Purple
            )
        }

        Spacer(Modifier.width(7.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
            Text(
                text = "الكمية: $quantity",
                fontSize = 9.sp,
                color = Color.Gray
            )
        }

        Text(
            text = "${formatAmount(earned)} ريال",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Green,
            textAlign = TextAlign.End
        )
    }
}

private fun formatAmount(value: Int): String {
    return String.format(Locale.US, "%,d", value)
}

private fun formatSignedAmount(value: Int): String {
    return if (value < 0) {
        "-${formatAmount(-value)}"
    } else {
        formatAmount(value)
    }
}



// ================ حوار من نحن ================

@Composable
internal fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppBackground,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Purple.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Purple,
                    modifier = Modifier.size(34.dp)
                )
            }
        },
        title = {
            Text(
                text = "من نحن",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Purple,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "دفتر الحسابات",
                    color = AppText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Add Paper",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(14.dp))

                AboutInfoCard(
                    title = "نبذة عن التطبيق",
                    text = "تطبيق عملي لإدارة حسابات المحلات وسجلات العمل اليومية، مع تنظيم المحلات والأشهر والقطع والمصروفات والإحصائيات والطباعة بصيغة PDF."
                )

                Spacer(Modifier.height(8.dp))

                AboutInfoCard(
                    title = "أهم المزايا",
                    text = "• إدارة أكثر من محل بشكل مستقل\n• تسجيل القطع وأسعارها والكميات اليومية\n• تسجيل المصروفات واحتساب الصافي\n• إحصائيات شهرية مفصلة\n• تصدير سجل الشهر إلى PDF\n• تنبيه يومي عند عدم تسجيل العمل"
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "الإصدار",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "1.0",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Purple
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "جميع الحقوق محفوظة © 2026",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                Text(
                    text = "إغلاق",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun AboutInfoCard(
    title: String,
    text: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.8f))
            .border(
                1.dp,
                Purple.copy(alpha = 0.10f),
                RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = Purple,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = text,
            color = Color.DarkGray,
            fontSize = 10.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Right
        )
    }
}

// ================ دليل الاستخدام ================

@Composable
internal fun HelpDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppBackground,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Purple)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "دليل الاستخدام",
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ابدأ هنا",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "أنجز عملك بخطوات واضحة",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "اتبع المراحل بالترتيب، ثم ارجع إلى الدليل عند الحاجة.",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Right
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(455.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item { HelpIntroCard() }

                item { HelpSectionTitle("التهيئة الأولى", "جهّز التطبيق والمحل قبل بدء التسجيل") }
                item { HelpStep("1", "أنشئ ملفك والمحل", "من القائمة افتح ملفي الشخصي، ثم أضف المحل واكتب رقم المحل أو رقم التسجيل. يمكنك إدارة أكثر من محل بشكل مستقل.") }
                item { HelpStep("2", "اختر نوع التسجيل", "اختر التسجيل العددي لتسجيل الكميات اليومية، أو التسجيل الفردي للحسابات المرتبطة بأسماء الزبائن وأرقام الصفحات.") }
                item { HelpStep("3", "أضف الشهر والقطع", "أنشئ الشهر، ثم أضف أسماء القطع وأسعارها. يمكن تعديل الأسعار لاحقًا، وتبقى الحسابات السابقة محفوظة بسعرها المسجل.") }

                item { HelpSectionTitle("التسجيل اليومي", "سجّل الكميات والحسابات بسرعة وبدقة") }
                item { HelpStep("4", "ابدأ يومًا جديدًا", "اختر الشهر واليوم، ثم أضف الكميات في التسجيل العددي أو أضف زبونًا جديدًا في التسجيل الفردي. يحسب التطبيق الإجماليات تلقائيًا.") }
                item { HelpStep("5", "سجّل المصروفات", "أدخل مبلغ المصروف وملاحظته ثم اضغط حفظ. يظهر المصروف في الملخص ويُخصم من الصافي عند تفعيل خيار الخصم في إعدادات الشهر.") }
                item { HelpStep("6", "استخدم البحث والتنقل", "في التسجيل الفردي ابحث باسم الزبون أو رقم الصفحة، واستخدم أزرار الأيام للوصول إلى السجل المطلوب دون فتح الأشهر يدويًا.") }

                item { HelpSectionTitle("التقارير والحماية", "احتفظ بنتائج عملك وشاركها بأمان") }
                item { HelpStep("7", "أنشئ تقرير PDF", "افتح PDF، ثم اختر تقرير يوم أو شهر أو التقرير الكامل. التقرير العددي والفردي منفصلان، ويمكن طباعتهما أو مشاركتهما.") }
                item { HelpStep("8", "شارك التقرير", "بعد إنشاء PDF اختر واتساب أو الرسائل أو البريد من شاشة مشاركة أندرويد. يجب حفظ رقم الهاتف أو البريد في الملف الشخصي لاستخدامهما مع التقرير.") }
                item { HelpStep("9", "أنشئ نسخة احتياطية", "استخدم الحماية والنسخ الاحتياطي لإنشاء نسخة ZIP، ثم احفظها خارج التطبيق مثل Google Drive أو ذاكرة خارجية.") }
                item { HelpStep("10", "اعتمد روتينًا آمنًا", "صدّر نسخة احتياطية دورية، وراجع ملخص الإنتاج والمصروف والصافي قبل إغلاق الشهر أو حذفه.") }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = AppButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                Text("فهمت، ابدأ الآن", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    )
}

@Composable
private fun HelpIntroCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppButtonShape)
            .background(AppPrimarySoft)
            .border(1.dp, Purple.copy(alpha = 0.15f), AppButtonShape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Purple),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "ثلاث مراحل تكفي للبدء",
                color = Purple,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "التهيئة ← التسجيل اليومي ← التقارير والحماية",
                color = AppText,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Right
            )
        }
    }
}

@Composable
private fun HelpSectionTitle(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Purple)
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(title, color = Purple, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right)
            Text(subtitle, color = AppMuted, fontSize = 9.sp, textAlign = TextAlign.Right)
        }
    }
}

@Composable
private fun HelpStep(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppButtonShape)
            .background(AppSurface)
            .border(1.dp, AppBorder, AppButtonShape)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Purple),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("الخطوة $number", color = AppMuted, fontSize = 8.sp)
                Text(title, color = Purple, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Right)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                color = AppText,
                fontSize = 10.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Right
            )
        }
    }
}
