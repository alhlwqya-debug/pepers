package com.add.pepers

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MainLedger(
    database: Database,
    bundle: MonthBundle,
    shops: List<ShopRecord>,
    pieces: List<PieceRecord>,
    selectedShopId: Long?,
    selectedMonthId: Long?,
    months: List<MonthRecord>,
    userName: String,
    shopMenuOpen: Boolean,
    onMenu: () -> Unit,
    onShopMenu: () -> Unit,
    onSelectShop: (Long) -> Unit,
    onSelectMonth: (Long) -> Unit,
    onAddMonth: () -> Unit,
    onAddPiece: () -> Unit,
    onManagePieces: () -> Unit,
    onEditStartDate: () -> Unit,
    onClear: () -> Unit,
    onDeleteMonth: () -> Unit,
    onDeleteShop: () -> Unit,
    onRenameMonth: () -> Unit,
    onCopyMonth: () -> Unit,
    onEditQuantity: (DayRecord, PieceRecord, String) -> Unit,
    onEditExpense: (DayRecord, String) -> Unit,
    onOpenExpense: (DayRecord) -> Unit,
    onPrint: () -> Unit
) {
    val context = LocalContext.current
    val horizontal = rememberScrollState()
    var searchText by remember { mutableStateOf("") }
    var compactActions by remember { mutableStateOf(false) }

    val totalEarned = database.calculateMonthEarned(bundle)
    val totalExpenses = database.calculateMonthExpenses(bundle)
    val net = database.calculateMonthNet(bundle)
    val totalPieces = bundle.days.sumOf { it.quantities.values.sum() }
    val shopName = shops.firstOrNull { it.id == selectedShopId }?.name ?: ""

    val normalizedQuery = searchText.trim().lowercase()
    val visibleDays = if (normalizedQuery.isBlank()) {
        bundle.days
    } else {
        bundle.days.filter { day ->
            day.date.lowercase().contains(normalizedQuery) ||
                day.dayName.lowercase().contains(normalizedQuery)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(PageBg)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Purple, RoundedCornerShape(14.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Menu, contentDescription = "القائمة", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Box {
                    OutlinedButton(
                        onClick = onShopMenu,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(shopName.ifBlank { "اختر محل" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(
                        expanded = shopMenuOpen,
                        onDismissRequest = { selectedShopId?.let(onSelectShop) }
                    ) {
                        shops.forEach { shop ->
                            DropdownMenuItem(
                                text = { Text(shop.name) },
                                onClick = { onSelectShop(shop.id) }
                            )
                        }
                    }
                }
            }
            if (userName.isNotBlank()) {
                Text("👤 $userName", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(end = 4.dp))
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            months.forEach { month ->
                Button(
                    onClick = { onSelectMonth(month.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (month.id == selectedMonthId) Purple else AppPrimarySoft,
                        contentColor = if (month.id == selectedMonthId) Color.White else AppText
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(month.name, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SummaryCard("الإنتاج", totalEarned.toString(), CardWorkBg, Green, Modifier.weight(1f))
            SummaryCard("المصروف", totalExpenses.toString(), CardExpBg, Red, Modifier.weight(1f))
            SummaryCard("الصافي", net.toString(), CardNetBg, Blue, Modifier.weight(1f))
            SummaryCard("القطع", totalPieces.toString(), Color(0xFFFFF3E0), Orange, Modifier.weight(1f))
        }

        // البحث عن يوم/تاريخ للوصول السريع إلى السجل المطلوب.
        Row(
            Modifier
                .fillMaxWidth()
                .background(AppSurface, RoundedCornerShape(12.dp))
                .border(1.dp, AppBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = "بحث", tint = Purple, modifier = Modifier.size(20.dp))
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 9.dp),
                decorationBox = { inner ->
                    if (searchText.isBlank()) Text("بحث في أيام الشهر أو التاريخ...", color = AppMuted, fontSize = 11.sp)
                    inner()
                }
            )
            if (searchText.isNotBlank()) {
                IconButton(onClick = { searchText = "" }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "مسح البحث", tint = AppMuted, modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddMonth,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp)
            ) { Text("+ شهر", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onAddPiece,
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp)
            ) { Text("+ قطعة", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onPrint,
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp)
            ) { Text("PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { compactActions = !compactActions },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp)
            ) { Text(if (compactActions) "المزيد" else "إدارة", fontSize = 10.sp) }
        }

        if (compactActions) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                OutlinedButton(onClick = onManagePieces, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إدارة القطع", fontSize = 9.sp)
                }
                OutlinedButton(onClick = onRenameMonth, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) {
                    Text("تغيير الاسم", fontSize = 9.sp)
                }
                OutlinedButton(onClick = onEditStartDate, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) {
                    Text("تاريخ البداية", fontSize = 9.sp)
                }
                OutlinedButton(onClick = onCopyMonth, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) {
                    Text("نسخ الشهر", fontSize = 9.sp)
                }
                OutlinedButton(onClick = onDeleteMonth, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) {
                    Text("حذف الشهر", color = Red, fontSize = 9.sp)
                }
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) {
                    Text("مسح البيانات", color = Red, fontSize = 9.sp)
                }
            }
        }

        if (searchText.isNotBlank()) {
            Text(
                text = "النتائج: ${visibleDays.size} من ${bundle.days.size} يوم",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                color = AppMuted,
                fontSize = 10.sp
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(horizontal)
        ) {
            val tableWidth = wDay + wDate + (wPiece * pieces.size) + wExpense + wTotal
            Column(Modifier.width(tableWidth)) {
                Row(Modifier.background(Purple)) {
                    CellText("اليوم", wDay, 32.dp, Purple, bold = true, size = 10, color = Color.White)
                    CellText("التاريخ", wDate, 32.dp, Purple, bold = true, size = 10, color = Color.White)
                    pieces.forEach { CellText(it.name, wPiece, 32.dp, Purple, bold = true, size = 9, color = Color.White) }
                    CellText("المصروف", wExpense, 32.dp, Purple, bold = true, size = 10, color = Color.White)
                    CellText("المجموع", wTotal, 32.dp, Purple, bold = true, size = 10, color = Color.White)
                }

                LazyColumn(Modifier.fillMaxWidth()) {
                    items(visibleDays, key = { it.id }) { day ->
                        val earned = database.calculateDayEarned(day)
                        val dayTotal = if (bundle.month.deductExpense) earned - day.expense else earned
                        Row {
                            CellText(day.dayName, wDay, rowH, size = 10)
                            CellText(day.date, wDate, rowH, size = 10)
                            pieces.forEach { piece ->
                                val quantity = day.quantities[piece.id] ?: 0
                                CellEdit(
                                    value = if (quantity == 0) "" else quantity.toString(),
                                    onValueChange = { value -> onEditQuantity(day, piece, value) },
                                    width = wPiece,
                                    height = rowH,
                                    number = true
                                )
                            }
                            Box(
                                Modifier.width(wExpense).height(rowH).border(1.dp, Color.Black).background(Color.White).clickable { onOpenExpense(day) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (day.expense == 0) "" else day.expense.toString(), fontSize = 10.sp)
                            }
                            CellText(if (earned == 0) "" else dayTotal.toString(), wTotal, rowH, LightBlue, bold = true)
                        }
                    }
                }

                Row {
                    CellText("الإجمالي", wDay + wDate, 34.dp, Color(0xFFFFE58F), bold = true, size = 10, color = AppText)
                    pieces.forEach { piece ->
                        val count = database.pieceTotal(bundle, piece.id)
                        CellText(if (count == 0) "" else count.toString(), wPiece, 34.dp, Color(0xFFFFE58F), bold = true, color = AppText)
                    }
                    CellText(if (totalExpenses == 0) "" else totalExpenses.toString(), wExpense, 34.dp, Color(0xFFFFE58F), bold = true, color = Red)
                    CellText(net.toString(), wTotal, 34.dp, HeaderBlue, bold = true)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Color(0xFFF0F2F5)).border(1.dp, Color.LightGray).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("جميع الحقوق محفوظة © 2026", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Text(
                "تواصل: alhlwqya@gmail.com",
                fontSize = 8.sp,
                color = Color.Blue,
                modifier = Modifier.clickable {
                    try {
                        androidx.core.content.ContextCompat.startActivity(
                            context,
                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:alhlwqya@gmail.com")),
                            null
                        )
                    } catch (_: Exception) { }
                }
            )
        }
    }
}
