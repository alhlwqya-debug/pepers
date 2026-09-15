package com.add.pepers

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val CustomerWidth = 150.dp
private val PageWidth = 82.dp
private val IndividualPieceWidth = 76.dp
private val IndividualTotalWidth = 100.dp
private val IndividualActionWidth = 50.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IndividualLedger(
    database: Database,
    bundle: MonthBundle,
    pieces: List<PieceRecord>,
    months: List<MonthRecord>,
    selectedMonthId: Long?,
    selectedShopId: Long?,
    shops: List<ShopRecord>,
    userName: String,
    onMenu: () -> Unit,
    onSelectMonth: (Long) -> Unit,
    onSelectShop: (Long) -> Unit,
    onAddMonth: () -> Unit,
    onAddPiece: () -> Unit,
    onManagePieces: () -> Unit,
    onDeleteMonth: () -> Unit,
    onPrintPdf: () -> Unit,
    onDataChanged: () -> Unit,
    onSelectSearchResult: (Long, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val horizontal = rememberScrollState()
    val years = remember(months) { months.map { it.year }.distinct().sortedDescending() }
    var selectedYear by remember(months, selectedMonthId) {
        mutableStateOf(months.firstOrNull { it.id == selectedMonthId }?.year ?: years.firstOrNull() ?: Calendar.getInstance().get(Calendar.YEAR))
    }
    var selectedDate by remember(bundle.month.id) { mutableStateOf(bundle.days.firstOrNull()?.date ?: bundle.month.startDate) }
    var entries by remember { mutableStateOf(emptyList<IndividualEntryRecord>()) }
    var customerSearch by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<IndividualSearchResult>()) }
    var searchOpen by remember { mutableStateOf(false) }
    var shopMenuOpen by remember { mutableStateOf(false) }
    var yearMenuOpen by remember { mutableStateOf(false) }
    var monthMenuOpen by remember { mutableStateOf(false) }
    var dateMenuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var expenseDraft by remember { mutableStateOf("") }
    var expenseNoteDraft by remember { mutableStateOf("") }
    var expenseEditing by remember { mutableStateOf(false) }
    var pendingSearchDate by remember { mutableStateOf<String?>(null) }
    var pendingSearchMonthId by remember { mutableStateOf<Long?>(null) }

    val yearMonths = months.filter { it.year == selectedYear }.sortedBy { it.month }
    val selectedMonth = months.firstOrNull { it.id == selectedMonthId } ?: yearMonths.firstOrNull()
    val recordedDays = remember(bundle.month.id, bundle.days, entries) {
        bundle.days.filter { it.expense > 0 || database.getIndividualEntries(bundle.month.id, it.date).isNotEmpty() }.sortedByDescending { it.date }
    }

    fun reloadEntries() { entries = database.getIndividualEntries(bundle.month.id, selectedDate) }

    fun saveExpense() {
        val amount = expenseDraft.filter(Char::isDigit).toIntOrNull() ?: 0
        database.setExpense(bundle.month.id, selectedDate, amount, expenseNoteDraft)
        expenseEditing = false
        onDataChanged()
    }

    fun addCustomer() {
        if (database.addIndividualEntry(bundle.month.id, selectedDate) > 0) {
            reloadEntries()
            onDataChanged()
            Toast.makeText(context, "تمت إضافة زبون جديد", Toast.LENGTH_SHORT).show()
        }
    }

    fun addNewDay() {
        val next = bundle.days.firstOrNull { it.date > selectedDate && database.getIndividualEntries(bundle.month.id, it.date).isEmpty() }
            ?: bundle.days.firstOrNull { database.getIndividualEntries(bundle.month.id, it.date).isEmpty() }
        if (next != null) selectedDate = next.date else showDatePicker = true
    }

    LaunchedEffect(selectedMonthId, bundle.month.id) {
        selectedYear = bundle.month.year
        selectedDate = when {
            recordedDays.any { it.date == todayString() } -> todayString()
            recordedDays.isNotEmpty() -> recordedDays.first().date
            else -> bundle.month.startDate
        }
    }

    LaunchedEffect(selectedDate) {
        reloadEntries()
        val day = bundle.days.firstOrNull { it.date == selectedDate }
        if (!expenseEditing) {
            expenseDraft = if ((day?.expense ?: 0) == 0) "" else day?.expense.toString()
            expenseNoteDraft = day?.expenseNote.orEmpty()
        }
    }

    LaunchedEffect(customerSearch, selectedShopId) {
        if (selectedShopId == null || customerSearch.trim().length < 2) {
            searchResults = emptyList()
            searchOpen = false
        } else {
            searchResults = database.searchIndividualCustomers(selectedShopId, customerSearch.trim())
            searchOpen = true
        }
    }

    LaunchedEffect(bundle.month.id) {
        if (pendingSearchMonthId == bundle.month.id && pendingSearchDate != null) {
            selectedDate = pendingSearchDate!!
            pendingSearchDate = null
            pendingSearchMonthId = null
        }
    }

    val day = bundle.days.firstOrNull { it.date == selectedDate }
    val expense = day?.expense ?: 0
    val earned = database.calculateIndividualDayEarned(entries)
    val net = if (bundle.month.deductExpense) earned - expense else earned
    val totalCustomers = entries.count { it.customerName.isNotBlank() || it.pageNumber.isNotBlank() }
    val totalQuantities = entries.sumOf { it.quantities.values.sum() }
    val shopName = shops.firstOrNull { it.id == selectedShopId }?.name ?: "اختر محل"

    Column(Modifier.fillMaxSize().background(PageBg).padding(horizontal = 8.dp, vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth().background(Purple, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Menu, "القائمة", tint = Color.White) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("التسجيل الفردي", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (userName.isNotBlank()) Text(userName, color = Color.White.copy(alpha = .85f), fontSize = 9.sp)
            }
            Box {
                OutlinedButton(onClick = { shopMenuOpen = true }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text(shopName, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                DropdownMenu(expanded = shopMenuOpen, onDismissRequest = { shopMenuOpen = false }) {
                    shops.forEach { shop -> DropdownMenuItem(text = { Text(shop.name) }, onClick = { shopMenuOpen = false; onSelectShop(shop.id) }) }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Column(Modifier.fillMaxWidth().border(1.dp, AppBorder, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp)).padding(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("السنة", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Purple)
                Spacer(Modifier.width(5.dp))
                Box {
                    OutlinedButton(onClick = { yearMenuOpen = true }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(31.dp)) { Text(selectedYear.toString(), fontSize = 10.sp, color = Purple, fontWeight = FontWeight.Bold) }
                    DropdownMenu(expanded = yearMenuOpen, onDismissRequest = { yearMenuOpen = false }) {
                        years.forEach { year -> DropdownMenuItem(text = { Text(year.toString()) }, onClick = { selectedYear = year; yearMenuOpen = false; yearMonths.firstOrNull()?.let { onSelectMonth(it.id) } }) }
                    }
                }
                Spacer(Modifier.width(7.dp))
                Text("الشهر", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Purple)
                Spacer(Modifier.width(5.dp))
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { monthMenuOpen = true }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.fillMaxWidth().height(31.dp)) { Text(selectedMonth?.name ?: "اختر شهر", fontSize = 10.sp, color = Purple, fontWeight = FontWeight.Bold) }
                    DropdownMenu(expanded = monthMenuOpen, onDismissRequest = { monthMenuOpen = false }) {
                        yearMonths.forEach { month -> DropdownMenuItem(text = { Text(month.name) }, onClick = { monthMenuOpen = false; onSelectMonth(month.id) }) }
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("اليوم", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Purple)
                Spacer(Modifier.width(5.dp))
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { dateMenuOpen = true }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.fillMaxWidth().height(31.dp)) { Text(if (recordedDays.isEmpty()) selectedDate else "$selectedDate • ${recordedDays.size} مسجل", fontSize = 9.sp) }
                    DropdownMenu(expanded = dateMenuOpen, onDismissRequest = { dateMenuOpen = false }) {
                        if (recordedDays.isEmpty()) DropdownMenuItem(text = { Text("لا توجد أيام مسجلة بعد") }, onClick = { dateMenuOpen = false })
                        recordedDays.forEach { recorded -> DropdownMenuItem(text = { Text(recorded.date) }, onClick = { selectedDate = recorded.date; dateMenuOpen = false }) }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth().border(1.dp, Purple.copy(alpha = .22f), RoundedCornerShape(10.dp)).background(Color.White, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Purple, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = customerSearch,
                onValueChange = { customerSearch = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = TextStyle(fontSize = 10.sp, color = Color.DarkGray),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> if (customerSearch.isBlank()) Text("بحث باسم الزبون أو رقم الصفحة…", fontSize = 10.sp, color = Color.Gray); inner() }
            )
            if (customerSearch.isNotBlank()) IconButton(onClick = { customerSearch = ""; searchOpen = false }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "مسح", tint = Color.Gray, modifier = Modifier.size(16.dp)) }
        }

        if (searchOpen) {
            Column(Modifier.fillMaxWidth().border(1.dp, AppBorder, RoundedCornerShape(10.dp)).background(Color.White, RoundedCornerShape(10.dp)).padding(4.dp)) {
                if (searchResults.isEmpty()) Text("لا توجد نتائج مطابقة", Modifier.fillMaxWidth().padding(9.dp), textAlign = TextAlign.Center, fontSize = 10.sp, color = Color.Gray)
                else searchResults.take(6).forEach { result ->
                    Row(Modifier.fillMaxWidth().clickable {
                        if (result.monthId == bundle.month.id) selectedDate = result.date
                        else { pendingSearchMonthId = result.monthId; pendingSearchDate = result.date; onSelectSearchResult(result.monthId, result.date) }
                        searchOpen = false
                    }.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = Purple, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(result.customerName.ifBlank { "زبون بدون اسم" }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppText)
                            Text("صفحة ${result.pageNumber.ifBlank { "—" }} • ${result.date} • ${result.monthName}", fontSize = 8.sp, color = Color.Gray)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = { addNewDay() }, colors = ButtonDefaults.buttonColors(containerColor = Green), contentPadding = PaddingValues(horizontal = 7.dp), modifier = Modifier.weight(1.2f).height(36.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("يوم جديد", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            Button(onClick = { addCustomer() }, colors = ButtonDefaults.buttonColors(containerColor = Purple), contentPadding = PaddingValues(horizontal = 7.dp), modifier = Modifier.weight(1.2f).height(36.dp)) { Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("زبون جديد", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            Button(onClick = onManagePieces, colors = ButtonDefaults.buttonColors(containerColor = Orange), contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.weight(1f).height(36.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(2.dp)); Text("القطع", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            Button(onClick = onPrintPdf, colors = ButtonDefaults.buttonColors(containerColor = Blue), contentPadding = PaddingValues(horizontal = 7.dp), modifier = Modifier.weight(.7f).height(36.dp)) { Text("PDF", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { val i = recordedDays.indexOfFirst { it.date == selectedDate }; if (i >= 0 && i < recordedDays.lastIndex) selectedDate = recordedDays[i + 1].date }, enabled = recordedDays.isNotEmpty(), contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.weight(1f).height(30.dp)) { Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(2.dp)); Text("السابق", fontSize = 8.sp) }
            OutlinedButton(onClick = { showDatePicker = true }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.weight(1.2f).height(30.dp)) { Text(selectedDate, fontSize = 8.sp) }
            OutlinedButton(onClick = { val i = recordedDays.indexOfFirst { it.date == selectedDate }; if (i > 0) selectedDate = recordedDays[i - 1].date }, enabled = recordedDays.isNotEmpty(), contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.weight(1f).height(30.dp)) { Text("التالي", fontSize = 8.sp); Spacer(Modifier.width(2.dp)); Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(14.dp)) }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SummaryCard("الإنتاج", earned.toString(), CardWorkBg, Green, Modifier.weight(1f))
            SummaryCard("المصروف", expense.toString(), CardExpBg, Red, Modifier.weight(1f))
            SummaryCard("الصافي", net.toString(), CardNetBg, Blue, Modifier.weight(1f))
            SummaryCard("الزبائن", totalCustomers.toString(), Color(0xFFFFF3E0), Orange, Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = onAddMonth, colors = ButtonDefaults.buttonColors(containerColor = Green), contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.weight(1f).height(28.dp)) { Text("+ شهر", fontSize = 8.sp) }
            Button(onClick = onAddPiece, colors = ButtonDefaults.buttonColors(containerColor = Blue), contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.weight(1f).height(28.dp)) { Text("+ قطعة", fontSize = 8.sp) }
            OutlinedButton(onClick = onDeleteMonth, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.weight(1.3f).height(28.dp)) { Text("حذف الشهر", color = Red, fontSize = 8.sp) }
        }

        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().weight(1f).horizontalScroll(horizontal).border(1.dp, AppBorder, RoundedCornerShape(7.dp)).background(Color.White, RoundedCornerShape(7.dp))) {
            val tableWidth = CustomerWidth + PageWidth + (IndividualPieceWidth * pieces.size) + IndividualTotalWidth + IndividualActionWidth
            Column(Modifier.width(tableWidth)) {
                Row(Modifier.background(Purple)) {
                    CellText("اسم الزبون", CustomerWidth, 40.dp, Purple, bold = true, size = 9, color = Color.White)
                    CellText("رقم الصفحة", PageWidth, 40.dp, Purple, bold = true, size = 9, color = Color.White)
                    pieces.forEachIndexed { i, piece -> CellText("${i + 1}. ${piece.name}", IndividualPieceWidth, 40.dp, Purple, bold = true, size = 8, color = Color.White) }
                    CellText("المجموع", IndividualTotalWidth, 40.dp, Purple, bold = true, size = 9, color = Color.White)
                    CellText("حذف", IndividualActionWidth, 40.dp, Purple, bold = true, size = 8, color = Color.White)
                }
                LazyColumn(Modifier.fillMaxWidth()) {
                    if (entries.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().height(86.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("لا يوجد زبائن في هذا اليوم", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Purple)
                            Text("اضغط «زبون جديد» للبدء", fontSize = 8.sp, color = Color.Gray)
                        }
                    }
                    items(entries, key = { it.id }) { entry ->
                        val total = database.calculateIndividualEntryEarned(entry)
                        Row {
                            CellEdit(entry.customerName, { database.updateIndividualEntry(entry.id, it, entry.pageNumber); reloadEntries() }, CustomerWidth, rowH)
                            CellEdit(entry.pageNumber, { database.updateIndividualEntry(entry.id, entry.customerName, it); reloadEntries() }, PageWidth, rowH, number = true)
                            pieces.forEach { piece ->
                                val q = entry.quantities[piece.id] ?: 0
                                CellEdit(if (q == 0) "" else q.toString(), { database.setIndividualQuantity(entry.id, piece.id, it.filter(Char::isDigit).toIntOrNull() ?: 0); reloadEntries() }, IndividualPieceWidth, rowH, number = true)
                            }
                            CellText(if (total == 0) "" else total.toString(), IndividualTotalWidth, rowH, LightBlue, bold = true)
                            Box(Modifier.width(IndividualActionWidth).height(rowH).border(1.dp, Color.LightGray).background(Color.White).clickable { database.deleteIndividualEntry(entry.id); reloadEntries(); onDataChanged() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Delete, "حذف", tint = Red, modifier = Modifier.size(17.dp)) }
                        }
                    }
                }
                Row {
                    CellText("المصروف", CustomerWidth + PageWidth + (IndividualPieceWidth * pieces.size), 36.dp, Color(0xFFFFE58F), bold = true, size = 9)
                    Box(Modifier.width(IndividualTotalWidth).height(36.dp).border(1.dp, Color.Black).background(Color(0xFFFFEBEE)), contentAlignment = Alignment.Center) {
                        BasicTextField(value = expenseDraft, onValueChange = { expenseDraft = it.filter(Char::isDigit); expenseEditing = true }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { saveExpense() }), textStyle = TextStyle(fontSize = 10.sp, color = Red, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && expenseEditing) saveExpense() })
                    }
                    CellText(net.toString(), IndividualActionWidth, 36.dp, HeaderBlue, bold = true, size = 8)
                }
                Row {
                    CellText("إجمالي اليوم", CustomerWidth + PageWidth, 36.dp, Color(0xFFFFE58F), bold = true, size = 9)
                    pieces.forEach { piece ->
                        val count = entries.sumOf { it.quantities[piece.id] ?: 0 }
                        CellText(if (count == 0) "" else count.toString(), IndividualPieceWidth, 36.dp, Color(0xFFFFE58F), bold = true)
                    }
                    CellText(earned.toString(), IndividualTotalWidth, 36.dp, HeaderBlue, bold = true)
                    CellText("", IndividualActionWidth, 36.dp, Color(0xFFFFE58F))
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().border(1.dp, AppBorder, RoundedCornerShape(9.dp)).background(Color.White, RoundedCornerShape(9.dp)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ملاحظة المصروف", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Purple)
                BasicTextField(value = expenseNoteDraft, onValueChange = { expenseNoteDraft = it }, singleLine = true, textStyle = TextStyle(fontSize = 9.sp, color = Color.DarkGray), modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            }
            Button(onClick = { saveExpense() }, colors = ButtonDefaults.buttonColors(containerColor = Green), contentPadding = PaddingValues(horizontal = 9.dp), modifier = Modifier.height(32.dp)) { Text("حفظ", fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        }
    }

    if (showDatePicker) {
        val millis = remember(selectedDate) { runCatching { SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).parse(selectedDate)?.time }.getOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    val date = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Calendar.getInstance().apply { timeInMillis = it }.time)
                    if (bundle.days.any { d -> d.date == date }) selectedDate = date else Toast.makeText(context, "اختر تاريخاً داخل الشهر المحدد", Toast.LENGTH_SHORT).show()
                }
                showDatePicker = false
            }) { Text("موافق") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") } }) { DatePicker(state) }
    }
}
