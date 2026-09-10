package com.add.pepers

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

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
        mutableStateOf(
            months.firstOrNull { it.id == selectedMonthId }?.year
                ?: years.firstOrNull()
                ?: Calendar.getInstance().get(Calendar.YEAR)
        )
    }
    var selectedDate by remember(bundle.month.id) {
        mutableStateOf(bundle.days.firstOrNull()?.date ?: bundle.month.startDate)
    }
    var entries by remember { mutableStateOf(emptyList<IndividualEntryRecord>()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var yearMenuOpen by remember { mutableStateOf(false) }
    var monthMenuOpen by remember { mutableStateOf(false) }
    var shopMenuOpen by remember { mutableStateOf(false) }
    var recordedDaysMenuOpen by remember { mutableStateOf(false) }
    var expenseDraft by remember { mutableStateOf("") }
    var expenseEditing by remember { mutableStateOf(false) }
    var customerSearch by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<IndividualSearchResult>()) }
    var searchOpen by remember { mutableStateOf(false) }
    var pendingSearchDate by remember { mutableStateOf<String?>(null) }
    var pendingSearchMonthId by remember { mutableStateOf<Long?>(null) }

    val yearMonths = months.filter { it.year == selectedYear }.sortedBy { it.month }
    val selectedMonth = yearMonths.firstOrNull { it.id == selectedMonthId }
        ?: months.firstOrNull { it.id == selectedMonthId }
        ?: yearMonths.firstOrNull()

    val recordedDays = remember(bundle.month.id, bundle.days, entries) {
        bundle.days
            .filter { day ->
                day.expense > 0 || database.getIndividualEntries(bundle.month.id, day.date).isNotEmpty()
            }
            .sortedByDescending { it.date }
    }

    fun reloadEntries() {
        entries = database.getIndividualEntries(bundle.month.id, selectedDate)
    }

    fun addNewDay() {
        val afterSelected = bundle.days.firstOrNull { dayRecord ->
            dayRecord.date > selectedDate &&
                database.getIndividualEntries(bundle.month.id, dayRecord.date).isEmpty()
        }
        val firstAvailable = bundle.days.firstOrNull { dayRecord ->
            database.getIndividualEntries(bundle.month.id, dayRecord.date).isEmpty()
        }
        val newDate = afterSelected?.date ?: firstAvailable?.date

        if (newDate != null) {
            selectedDate = newDate
            reloadEntries()
        } else {
            showDatePicker = true
        }
    }

    fun addCustomer() {
        val entryId = database.addIndividualEntry(bundle.month.id, selectedDate)
        if (entryId > 0) {
            reloadEntries()
            onDataChanged()
            Toast.makeText(context, "تمت إضافة صف زبون جديد", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "تعذر إضافة الزبون", Toast.LENGTH_SHORT).show()
        }
    }

    fun selectRecordedDay(date: String) {
        selectedDate = date
        recordedDaysMenuOpen = false
    }

    fun moveToOlderDay() {
        if (recordedDays.isEmpty()) return
        val currentIndex = recordedDays.indexOfFirst { it.date == selectedDate }
        val index = if (currentIndex < 0) 1 else currentIndex + 1
        if (index <= recordedDays.lastIndex) selectedDate = recordedDays[index].date
    }

    fun moveToNewerDay() {
        if (recordedDays.isEmpty()) return
        val currentIndex = recordedDays.indexOfFirst { it.date == selectedDate }
        val index = if (currentIndex < 0) 0 else currentIndex - 1
        if (index >= 0) selectedDate = recordedDays[index].date
    }

    LaunchedEffect(selectedMonthId, bundle.month.id) {
        selectedYear = bundle.month.year
        val today = todayString()
        selectedDate = when {
            recordedDays.any { it.date == today } -> today
            recordedDays.isNotEmpty() -> recordedDays.first().date
            else -> bundle.month.startDate
        }
        reloadEntries()
    }

    LaunchedEffect(bundle.month.id) {
        if (pendingSearchMonthId == bundle.month.id && !pendingSearchDate.isNullOrBlank()) {
            selectedDate = pendingSearchDate!!
            pendingSearchMonthId = null
            pendingSearchDate = null
        }
    }

    LaunchedEffect(selectedDate) {
        reloadEntries()
    }

    LaunchedEffect(customerSearch, selectedShopId) {
        val shopId = selectedShopId
        if (shopId == null || customerSearch.trim().length < 2) {
            searchResults = emptyList()
            searchOpen = false
        } else {
            searchResults = database.searchIndividualCustomers(shopId, customerSearch)
            searchOpen = true
        }
    }

    val day = bundle.days.firstOrNull { it.date == selectedDate }
    var currentExpense by remember(bundle.month.id, selectedDate) {
        mutableStateOf(day?.expense ?: 0)
    }
    var expenseNoteDraft by remember(bundle.month.id, selectedDate) {
        mutableStateOf(day?.expenseNote ?: "")
    }
    val expense = currentExpense

    fun saveExpense() {
        val amount = expenseDraft.filter(Char::isDigit).toIntOrNull() ?: 0
        currentExpense = amount
        database.setExpense(
            bundle.month.id,
            selectedDate,
            amount,
            expenseNoteDraft
        )
        expenseEditing = false
        onDataChanged()
    }

    LaunchedEffect(selectedDate, day?.expense, day?.expenseNote) {
        if (!expenseEditing) {
            currentExpense = day?.expense ?: 0
            expenseDraft = if (currentExpense == 0) "" else currentExpense.toString()
            expenseNoteDraft = day?.expenseNote ?: ""
        }
    }
    val earned = database.calculateIndividualDayEarned(entries)
    val net = if (bundle.month.deductExpense) earned - expense else earned
    val totalCustomers = entries.count { it.customerName.isNotBlank() || it.pageNumber.isNotBlank() }
    val totalQuantities = entries.sumOf { entry -> entry.quantities.values.sum() }
    val shopName = shops.firstOrNull { it.id == selectedShopId }?.name ?: "اختر محل"

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        // الرأس: عنوان واضح + المحل + القائمة.
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF6A3E91), RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Menu, contentDescription = "القائمة", tint = Color.White)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("التسجيل الفردي", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (userName.isNotBlank()) {
                    Text(userName, color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
                }
            }
            Box {
                OutlinedButton(
                    onClick = { shopMenuOpen = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text(shopName, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                DropdownMenu(expanded = shopMenuOpen, onDismissRequest = { shopMenuOpen = false }) {
                    shops.forEach { shop ->
                        DropdownMenuItem(
                            text = { Text(shop.name) },
                            onClick = {
                                shopMenuOpen = false
                                onSelectShop(shop.id)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // التسلسل: السنة ثم الشهر ثم الأيام المسجلة.
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE1D4EE), RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(7.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("السنة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Purple)
                Spacer(Modifier.width(5.dp))
                Box {
                    OutlinedButton(
                        onClick = { yearMenuOpen = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(31.dp)
                    ) { Text(selectedYear.toString(), fontSize = 10.sp, color = Purple, fontWeight = FontWeight.Bold) }
                    DropdownMenu(expanded = yearMenuOpen, onDismissRequest = { yearMenuOpen = false }) {
                        years.forEach { year ->
                            DropdownMenuItem(
                                text = { Text(year.toString()) },
                                onClick = {
                                    selectedYear = year
                                    yearMenuOpen = false
                                    yearMonths.firstOrNull()?.let { onSelectMonth(it.id) }
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("الشهر", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Purple)
                Spacer(Modifier.width(5.dp))
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { monthMenuOpen = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(31.dp).fillMaxWidth()
                    ) { Text(selectedMonth?.name ?: "اختر شهر", fontSize = 10.sp, color = Purple, fontWeight = FontWeight.Bold) }
                    DropdownMenu(expanded = monthMenuOpen, onDismissRequest = { monthMenuOpen = false }) {
                        yearMonths.forEach { month ->
                            DropdownMenuItem(text = { Text(month.name) }, onClick = {
                                monthMenuOpen = false
                                onSelectMonth(month.id)
                            })
                        }
                    }
                }
            }

            Spacer(Modifier.height(5.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("الأيام المسجلة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Purple)
                Spacer(Modifier.width(5.dp))
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { recordedDaysMenuOpen = true },
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                        modifier = Modifier.fillMaxWidth().height(31.dp)
                    ) {
                        Text("ⓘ", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (recordedDays.isEmpty()) "لا توجد أيام مسجلة" else "$selectedDate — ${recordedDays.size} يوم",
                            fontSize = 9.sp
                        )
                    }
                    DropdownMenu(expanded = recordedDaysMenuOpen, onDismissRequest = { recordedDaysMenuOpen = false }) {
                        if (recordedDays.isEmpty()) {
                            DropdownMenuItem(text = { Text("لا توجد أيام مسجلة بعد") }, onClick = { recordedDaysMenuOpen = false })
                        } else {
                            recordedDays.forEach { recordedDay ->
                                DropdownMenuItem(
                                    text = { Text(recordedDay.date) },
                                    onClick = { selectRecordedDay(recordedDay.date) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                recordedDays.forEach { recordedDay ->
                    val selected = recordedDay.date == selectedDate
                    Button(
                        onClick = { selectRecordedDay(recordedDay.date) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Purple else Color(0xFFEDE5F6),
                            contentColor = if (selected) Color.White else Color(0xFF4A315F)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(27.dp)
                    ) { Text(recordedDay.date, fontSize = 8.sp) }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // البحث الموحد في التسجيل الفردي: الاسم أو رقم الصفحة مع تطبيع عربي متسامح مع الهمزات والنقاط.
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Purple.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Purple, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = customerSearch,
                onValueChange = { customerSearch = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = TextStyle(fontSize = 10.sp, color = Color.DarkGray),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (customerSearch.isBlank()) Text("بحث باسم الزبون أو رقم الصفحة…", fontSize = 10.sp, color = Color.Gray)
                    inner()
                }
            )
            if (customerSearch.isNotBlank()) {
                IconButton(
                    onClick = { customerSearch = ""; searchResults = emptyList(); searchOpen = false },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "مسح", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (searchOpen && customerSearch.trim().length >= 2) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE0D6E8), RoundedCornerShape(10.dp))
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                if (searchResults.isEmpty()) {
                    Text("لا توجد نتائج مطابقة", modifier = Modifier.fillMaxWidth().padding(10.dp), textAlign = TextAlign.Center, fontSize = 10.sp, color = Color.Gray)
                } else {
                    searchResults.take(8).forEach { result ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (result.monthId == bundle.month.id) {
                                        selectedDate = result.date
                                    } else {
                                        pendingSearchMonthId = result.monthId
                                        pendingSearchDate = result.date
                                        onSelectSearchResult(result.monthId, result.date)
                                    }
                                    searchOpen = false
                                }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Purple, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text(result.customerName.ifBlank { "زبون بدون اسم" }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F2A50))
                                Text("صفحة ${result.pageNumber.ifBlank { "—" }} • ${result.date} • ${result.monthName}", fontSize = 8.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }

        // أزرار التحكم الرئيسية: يوم جديد، القطع والأسعار، PDF، والتنقل بين الأيام.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { addNewDay() },
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                modifier = Modifier.weight(1.25f).height(38.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(3.dp))
                Text("إضافة يوم جديد", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onManagePieces,
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                modifier = Modifier.weight(1.2f).height(38.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(3.dp))
                Text("تعديل القطع والأسعار", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onPrintPdf,
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.weight(0.72f).height(38.dp)
            ) {
                Text("ⓘ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(2.dp))
                Text("PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(5.dp))

        Button(
            onClick = { addCustomer() },
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text("إضافة زبون جديد", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(5.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(
                onClick = { moveToOlderDay() },
                enabled = recordedDays.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.weight(1f).height(31.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
                Text("اليوم السابق", fontSize = 9.sp)
            }
            OutlinedButton(
                onClick = { showDatePicker = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.weight(1f).height(31.dp)
            ) {
                Text("ⓘ", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Text(selectedDate, fontSize = 9.sp)
            }
            OutlinedButton(
                onClick = { moveToNewerDay() },
                enabled = recordedDays.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.weight(1f).height(31.dp)
            ) {
                Text("اليوم التالي", fontSize = 9.sp)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
            }
        }

        Spacer(Modifier.height(5.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SummaryCard("الإنتاج", earned.toString(), CardWorkBg, Green, Modifier.weight(1f))
            SummaryCard("المصروف", expense.toString(), CardExpBg, Red, Modifier.weight(1f))
            SummaryCard("الصافي", net.toString(), CardNetBg, Blue, Modifier.weight(1f))
            SummaryCard("الزبائن", totalCustomers.toString(), Color(0xFFFFF3E0), Orange, Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(
                onClick = onAddMonth,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.weight(1f).height(29.dp)
            ) { Text("+ شهر", fontSize = 9.sp) }
            Button(
                onClick = onAddPiece,
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.weight(1f).height(29.dp)
            ) { Text("+ قطعة", fontSize = 9.sp) }
            OutlinedButton(
                onClick = onDeleteMonth,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.weight(1.2f).height(29.dp)
            ) { Text("حذف الشهر", color = Red, fontSize = 9.sp) }
        }

        Spacer(Modifier.height(5.dp))

        // جدول التسجيل الفردي.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(horizontal)
                .border(1.dp, Color(0xFFB9A9C8), RoundedCornerShape(7.dp))
                .background(Color.White, RoundedCornerShape(7.dp))
        ) {
            val tableWidth = CustomerWidth + PageWidth + (IndividualPieceWidth * pieces.size) + IndividualTotalWidth + IndividualActionWidth
            Column(Modifier.width(tableWidth)) {
                Row(Modifier.background(Purple)) {
                    CellText("اسم الزبون", CustomerWidth, 42.dp, Purple, bold = true, size = 10, color = Color.White)
                    CellText("رقم الصفحة", PageWidth, 42.dp, Purple, bold = true, size = 10, color = Color.White)
                    pieces.forEachIndexed { index, piece ->
                        CellText("${index + 1}. ${piece.name}", IndividualPieceWidth, 42.dp, Purple, bold = true, size = 8, color = Color.White)
                    }
                    CellText("المجموع", IndividualTotalWidth, 42.dp, Purple, bold = true, size = 10, color = Color.White)
                    CellText("حذف", IndividualActionWidth, 42.dp, Purple, bold = true, size = 9, color = Color.White)
                }

                LazyColumn(Modifier.fillMaxWidth()) {
                    if (entries.isEmpty()) {
                        item {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .height(92.dp)
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("لا يوجد زبائن في هذا اليوم", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Purple)
                                Spacer(Modifier.height(4.dp))
                                Text("اضغط «إضافة زبون جديد» لإضافة حساب", fontSize = 9.sp, color = Color.Gray)
                            }
                        }
                    }
                    items(entries, key = { it.id }) { entry ->
                        val entryTotal = database.calculateIndividualEntryEarned(entry)
                        Row {
                            CellEdit(
                                entry.customerName,
                                { database.updateIndividualEntry(entry.id, it, entry.pageNumber); reloadEntries() },
                                CustomerWidth,
                                rowH
                            )
                            CellEdit(
                                entry.pageNumber,
                                { database.updateIndividualEntry(entry.id, entry.customerName, it); reloadEntries() },
                                PageWidth,
                                rowH,
                                number = true
                            )
                            pieces.forEach { piece ->
                                val quantity = entry.quantities[piece.id] ?: 0
                                CellEdit(
                                    if (quantity == 0) "" else quantity.toString(),
                                    {
                                        database.setIndividualQuantity(
                                            entry.id,
                                            piece.id,
                                            it.filter(Char::isDigit).toIntOrNull() ?: 0
                                        )
                                        reloadEntries()
                                    },
                                    IndividualPieceWidth,
                                    rowH,
                                    number = true
                                )
                            }
                            CellText(
                                if (entryTotal == 0) "" else entryTotal.toString(),
                                IndividualTotalWidth,
                                rowH,
                                LightBlue,
                                bold = true
                            )
                            Box(
                                Modifier
                                    .width(IndividualActionWidth)
                                    .height(rowH)
                                    .border(1.dp, Color(0xFFCCCCCC))
                                    .background(Color.White)
                                    .clickable {
                                        database.deleteIndividualEntry(entry.id)
                                        reloadEntries()
                                        onDataChanged()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Red, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }

                Row {
                    CellText(
                        "المصروف",
                        CustomerWidth + PageWidth + (IndividualPieceWidth * pieces.size),
                        38.dp,
                        Color(0xFFFFE58F),
                        bold = true,
                        size = 10
                    )
                    Box(
                        modifier = Modifier
                            .width(IndividualTotalWidth)
                            .height(38.dp)
                            .border(1.dp, Color.Black)
                            .background(Color(0xFFFFEBEE))
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = expenseDraft,
                            onValueChange = { value ->
                                expenseDraft = value.filter(Char::isDigit)
                                expenseEditing = true
                                currentExpense = expenseDraft.toIntOrNull() ?: 0
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { saveExpense() }),
                            textStyle = TextStyle(
                                fontSize = 11.sp,
                                color = Red,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { state ->
                                    if (!state.isFocused) saveExpense()
                                }
                        )
                    }
                    CellText(net.toString(), IndividualActionWidth, 38.dp, HeaderBlue, bold = true, size = 8)
                }

                Row {
                    CellText("إجمالي اليوم", CustomerWidth + PageWidth, 38.dp, Color(0xFFFFE58F), bold = true, size = 10)
                    pieces.forEach { piece ->
                        val count = entries.fold(0) { total, entry -> total + (entry.quantities[piece.id] ?: 0) }
                        CellText(if (count == 0) "" else count.toString(), IndividualPieceWidth, 38.dp, Color(0xFFFFE58F), bold = true)
                    }
                    CellText(earned.toString(), IndividualTotalWidth, 38.dp, HeaderBlue, bold = true)
                    CellText("", IndividualActionWidth, 38.dp, Color(0xFFFFE58F))
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE0D6E8), RoundedCornerShape(9.dp))
                .background(Color.White, RoundedCornerShape(9.dp))
                .padding(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("ملاحظة المصروف", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Purple)
                BasicTextField(
                    value = expenseNoteDraft,
                    onValueChange = { value ->
                        expenseNoteDraft = value
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Start
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                )
            }
            Button(
                onClick = { saveExpense() },
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
                Text("حفظ", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF0F2F5), RoundedCornerShape(7.dp))
                .border(1.dp, Color.LightGray, RoundedCornerShape(7.dp))
                .padding(5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("تسجيل فردي", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("السجل مستقل عن التسجيل العددي", fontSize = 7.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${totalQuantities} قطعة", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Green)
                Text("${recordedDays.size} يوم مسجل", fontSize = 7.sp, color = Color.Gray)
            }
        }
    }

    if (showDatePicker) {
        val millis = remember(selectedDate) {
            try {
                SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).parse(selectedDate)?.time
            } catch (_: Exception) {
                null
            }
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val calendar = Calendar.getInstance().apply { timeInMillis = it }
                        val date = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(calendar.time)
                        if (bundle.days.any { d -> d.date == date }) {
                            saveExpense()
                            selectedDate = date
                            reloadEntries()
                        } else {
                            Toast.makeText(context, "اختر تاريخاً داخل أيام الشهر المحدد", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showDatePicker = false
                }) { Text("موافق") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") } }
        ) { DatePicker(state) }
    }
}
