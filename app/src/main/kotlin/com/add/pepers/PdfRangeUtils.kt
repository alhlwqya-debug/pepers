package com.add.pepers

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class PdfReportRange {
    DAY,
    MONTH,
    ALL
}

@Composable
internal fun PdfReportRangeDialog(
    mode: RegistrationMode,
    bundle: MonthBundle,
    database: Database,
    onDismiss: () -> Unit,
    onPrint: (PdfReportRange, String?, Long, Boolean) -> Unit
) {
    var range by remember { mutableStateOf(PdfReportRange.MONTH) }
    val activeDates = remember(bundle, mode) {
        bundle.days
            .filter { day ->
                if (mode == RegistrationMode.NUMERIC) {
                    day.quantities.values.any { it > 0 } || day.expense > 0
                } else {
                    database.getIndividualEntries(bundle.month.id, day.date).isNotEmpty() || day.expense > 0
                }
            }
            .sortedBy { it.date }
            .map { it.date }
    }
    var selectedDate by remember(activeDates) { mutableStateOf(activeDates.firstOrNull()) }

    val modeTitle = if (mode == RegistrationMode.NUMERIC) "التسجيل العددي" else "التسجيل الفردي"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("طباعة التقرير", fontWeight = FontWeight.Bold)
                Text(
                    modeTitle,
                    color = Purple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("اختر نطاق التقرير المطلوب:", fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                ReportRangeOption(
                    "يوم واحد",
                    "طباعة يوم محدد فقط",
                    range == PdfReportRange.DAY
                ) { range = PdfReportRange.DAY }
                ReportRangeOption(
                    "الشهر الحالي",
                    "طباعة جميع أيام الشهر بنفس قالب التسجيل العددي الجميل",
                    range == PdfReportRange.MONTH
                ) { range = PdfReportRange.MONTH }
                ReportRangeOption(
                    "من بداية التسجيل إلى النهاية",
                    "من أول يوم يحتوي على تسجيل فعلي حتى آخر يوم مسجل لهذا المحل فقط",
                    range == PdfReportRange.ALL
                ) { range = PdfReportRange.ALL }

                if (range == PdfReportRange.DAY) {
                    Spacer(Modifier.height(8.dp))
                    Text("اختر اليوم:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (activeDates.isEmpty()) {
                        Text(
                            "لا توجد أيام تحتوي على تسجيلات بعد.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        activeDates.take(31).forEach { date ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedDate == date,
                                    onClick = { selectedDate = date }
                                )
                                Text(date, fontSize = 11.sp)
                            }
                        }
                        if (activeDates.size > 31) {
                            Text(
                                "يتم عرض أول 31 يومًا فقط في قائمة الاختيار.",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onPrint(range, selectedDate, bundle.month.id, false) },
                    enabled = range != PdfReportRange.DAY || selectedDate != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("طباعة PDF", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { onPrint(range, selectedDate, bundle.month.id, true) },
                    enabled = range != PdfReportRange.DAY || selectedDate != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("مشاركة PDF", fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun ReportRangeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 9.sp, color = Color.Gray)
        }
    }
}

internal fun printNumericReportRange(
    context: Context,
    database: Database,
    bundles: List<MonthBundle>,
    range: PdfReportRange,
    selectedDate: String?,
    selectedMonthId: Long,
    userName: String,
    userPhone: String,
    userEmail: String,
    userShop: String,
    userImagePath: String,
    sharePdf: Boolean = false
) {
    val sourceBundles = bundles
        .filter { it.month.shopId == bundles.firstOrNull()?.month?.shopId }
        .sortedWith(compareBy<MonthBundle> { it.month.year }.thenBy { it.month.month }.thenBy { it.month.id })

    if (sourceBundles.isEmpty()) {
        Toast.makeText(context, "لا توجد سجلات للطباعة", Toast.LENGTH_SHORT).show()
        return
    }

    if (!verifyShopMode(context, database, sourceBundles.first().month.shopId, RegistrationMode.NUMERIC)) {
        return
    }

    when (range) {
        PdfReportRange.DAY -> {
            val source = sourceBundles.firstOrNull { b -> b.days.any { it.date == selectedDate } }
            val day = source?.days?.firstOrNull { it.date == selectedDate }
            if (source != null && day != null) {
                generatePdf(
                    context,
                    database,
                    source.copy(days = listOf(day)),
                    userName,
                    userPhone,
                    userEmail,
                    userShop,
                    userImagePath,
                    sharePdf = sharePdf
                )
            } else {
                Toast.makeText(context, "اليوم المحدد لا يحتوي على تسجيل عددي", Toast.LENGTH_SHORT).show()
            }
        }

        PdfReportRange.MONTH -> {
            val source = sourceBundles.firstOrNull { it.month.id == selectedMonthId } ?: sourceBundles.last()
            val hasAnyRecord = source.days.any {
                it.quantities.values.any { q -> q > 0 } || it.expense > 0
            }
            if (!hasAnyRecord) {
                Toast.makeText(context, "لا توجد تسجيلات في الشهر الحالي", Toast.LENGTH_SHORT).show()
            } else {
                generatePdf(
                    context,
                    database,
                    source.copy(days = source.days.sortedBy { it.date }),
                    userName,
                    userPhone,
                    userEmail,
                    userShop,
                    userImagePath,
                    sharePdf = sharePdf
                )
            }
        }

        PdfReportRange.ALL -> {
            val activeDays = sourceBundles
                .flatMap { bundle ->
                    bundle.days.filter { it.quantities.values.any { q -> q > 0 } || it.expense > 0 }
                }
                .sortedBy { it.date }

            if (activeDays.isEmpty()) {
                Toast.makeText(context, "لا توجد تسجيلات عددية للطباعة", Toast.LENGTH_SHORT).show()
            } else {
                val first = sourceBundles.first()
                val lastDate = activeDays.last().date
                val firstDate = activeDays.first().date
                val fullMonth = first.month.copy(
                    name = "السجل الكامل — ${firstDate} إلى ${lastDate}",
                    startDate = firstDate
                )
                generatePdf(
                    context,
                    database,
                    MonthBundle(
                        month = fullMonth,
                        pieces = database.getPieces(first.month.shopId),
                        days = activeDays
                    ),
                    userName,
                    userPhone,
                    userEmail,
                    userShop,
                    userImagePath,
                    sharePdf = sharePdf
                )
            }
        }
    }
}

internal fun printIndividualReportRange(
    context: Context,
    database: Database,
    bundles: List<MonthBundle>,
    range: PdfReportRange,
    selectedDate: String?,
    selectedMonthId: Long,
    pieces: List<PieceRecord>,
    userName: String,
    userPhone: String,
    userEmail: String,
    userShop: String,
    userImagePath: String,
    sharePdf: Boolean = false
) {
    val sourceBundles = bundles
        .filter { it.month.shopId == bundles.firstOrNull()?.month?.shopId }
        .sortedWith(compareBy<MonthBundle> { it.month.year }.thenBy { it.month.month }.thenBy { it.month.id })

    if (sourceBundles.isEmpty()) {
        Toast.makeText(context, "لا توجد سجلات للطباعة", Toast.LENGTH_SHORT).show()
        return
    }

    if (!verifyShopMode(context, database, sourceBundles.first().month.shopId, RegistrationMode.INDIVIDUAL)) {
        return
    }

    when (range) {
        PdfReportRange.DAY -> {
            val source = sourceBundles.firstOrNull { b -> b.days.any { it.date == selectedDate } }
            val day = source?.days?.firstOrNull { it.date == selectedDate }
            if (source != null && day != null) {
                val hasIndividual = database.getIndividualEntries(source.month.id, day.date).isNotEmpty()
                if (hasIndividual || day.expense > 0) {
                    generateIndividualPdf(
                        context,
                        database,
                        source.copy(days = listOf(day)),
                        pieces,
                        userName,
                        userPhone,
                        userEmail,
                        userShop,
                        userImagePath
                    )
                } else {
                    Toast.makeText(context, "اليوم المحدد لا يحتوي على تسجيل فردي", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "اليوم المحدد غير موجود", Toast.LENGTH_SHORT).show()
            }
        }

        PdfReportRange.MONTH -> {
            val source = sourceBundles.firstOrNull { it.month.id == selectedMonthId } ?: sourceBundles.last()
            val activeDays = source.days
                .filter { day ->
                    database.getIndividualEntries(source.month.id, day.date).isNotEmpty() || day.expense > 0
                }
                .sortedBy { it.date }
            if (activeDays.isEmpty()) {
                Toast.makeText(context, "لا توجد تسجيلات فردية في الشهر الحالي", Toast.LENGTH_SHORT).show()
            } else {
                generateIndividualPdf(
                    context,
                    database,
                    source.copy(days = activeDays),
                    pieces,
                    userName,
                    userPhone,
                    userEmail,
                    userShop,
                    userImagePath,
                    sharePdf = sharePdf
                )
            }
        }

        PdfReportRange.ALL -> {
            val activeDays = sourceBundles
                .flatMap { bundle ->
                    bundle.days.filter { day ->
                        database.getIndividualEntries(bundle.month.id, day.date).isNotEmpty() || day.expense > 0
                    }
                }
                .sortedBy { it.date }

            if (activeDays.isEmpty()) {
                Toast.makeText(context, "لا توجد تسجيلات فردية للطباعة", Toast.LENGTH_SHORT).show()
            } else {
                val first = sourceBundles.first()
                val firstDate = activeDays.first().date
                val lastDate = activeDays.last().date
                val fullMonth = first.month.copy(
                    name = "السجل الفردي الكامل — ${firstDate} إلى ${lastDate}",
                    startDate = firstDate
                )
                generateIndividualPdf(
                    context,
                    database,
                    MonthBundle(
                        month = fullMonth,
                        pieces = pieces,
                        days = activeDays
                    ),
                    pieces,
                    userName,
                    userPhone,
                    userEmail,
                    userShop,
                    userImagePath,
                    sharePdf = sharePdf
                )
            }
        }
    }
}

private fun verifyShopMode(
    context: Context,
    database: Database,
    shopId: Long,
    expectedMode: RegistrationMode
): Boolean {
    val actualMode = database.getShops().firstOrNull { it.id == shopId }?.registrationMode
    if (actualMode == expectedMode) return true

    val expectedName = if (expectedMode == RegistrationMode.NUMERIC) "العددي" else "الفردي"
    val actualName = if (actualMode == RegistrationMode.NUMERIC) "العددي" else "الفردي"
    Toast.makeText(
        context,
        if (actualMode == null) {
            "تعذر تحديد نوع تسجيل المحل"
        } else {
            "هذا المحل مضبوط على التسجيل $actualName، ولا يمكن طباعة تقرير $expectedName منه"
        },
        Toast.LENGTH_LONG
    ).show()
    return false
}

internal fun printHtmlRange(context: Context, jobName: String, html: String) {
    try {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        webView.settings.defaultTextEncodingName = "UTF-8"
        val activity = context as? Activity
        val decorView = activity?.window?.decorView as? ViewGroup
        decorView?.addView(webView, ViewGroup.LayoutParams(1, 1))
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                try {
                    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val adapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter(jobName)
                    manager.print(
                        jobName,
                        adapter,
                        PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build()
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "تعذر إنشاء التقرير: ${e.message ?: "خطأ"}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    decorView?.postDelayed({
                        try {
                            decorView.removeView(webView)
                        } catch (_: Exception) {
                        }
                    }, 700L)
                }
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "تعذر إنشاء التقرير: ${e.message ?: "خطأ"}",
            Toast.LENGTH_LONG
        ).show()
    }
}
