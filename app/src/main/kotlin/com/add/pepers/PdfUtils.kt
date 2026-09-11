package com.add.pepers

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import android.util.Base64

internal fun generatePdf(
    context: Context,
    database: Database,
    bundle: MonthBundle,
    userName: String,
    userPhone: String,
    userEmail: String,
    userShop: String,
    userImagePath: String,
    shopRegistrationNumber: String = "",
    sharePdf: Boolean = false
) {
    try {
        val totalEarned = database.calculateMonthEarned(bundle)
        val totalExpenses = database.calculateMonthExpenses(bundle)
        val net = database.calculateMonthNet(bundle)
        val totalPieces = bundle.days.sumOf { it.quantities.values.sum() }
        val pieces = database.getPieces(bundle.month.shopId)
        val resolvedShopNumber = shopRegistrationNumber.ifBlank {
            database.getShops().firstOrNull { it.id == bundle.month.shopId }?.registrationNumber.orEmpty()
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
        val averageProduction = if (productionDays > 0) totalEarned / productionDays else 0
        val averageExpense = if (expenseDays > 0) totalExpenses / expenseDays else 0
        val activityPercent = if (bundle.days.isNotEmpty()) activeDays * 100 / bundle.days.size else 0

        fun amount(value: Int): String = String.format(java.util.Locale.US, "%,d", value)
        fun signed(value: Int): String = if (value < 0) "-${amount(-value)}" else amount(value)

        val profileImageHtml = buildProfileImageHtml(userImagePath)

        val html = buildString {
            append("""
<!DOCTYPE html>
<html dir="rtl" lang="ar">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
@page { size: A4; margin: 10mm; }
* { box-sizing: border-box; }
body {
    font-family: Arial, Tahoma, sans-serif;
    direction: rtl;
    margin: 0;
    padding: 0;
    color: #263238;
    background: #ffffff;
    font-size: 10px;
}
.page { width: 100%; }
.top {
    background: linear-gradient(135deg, #6A4C93, #8361B5);
    color: white;
    border-radius: 16px;
    padding: 18px;
    margin-bottom: 12px;
}
.top-title { font-size: 24px; font-weight: bold; margin: 0 0 5px; }
.top-subtitle { font-size: 12px; opacity: .9; margin: 0; }
.profile {
    margin-top: 12px;
    background: rgba(255,255,255,.14);
    border: 1px solid rgba(255,255,255,.25);
    border-radius: 12px;
    padding: 10px;
}
.profile img { width: 58px; height: 58px; border-radius: 50%; object-fit: cover; float: right; margin-left: 10px; }
.profile-name { font-size: 14px; font-weight: bold; padding-top: 5px; }
.profile-line { margin-top: 3px; font-size: 9px; }
.clear { clear: both; }
.section-title {
    font-size: 14px;
    font-weight: bold;
    color: #6A4C93;
    margin: 14px 0 7px;
    border-right: 4px solid #6A4C93;
    padding-right: 8px;
}
.cards { width: 100%; border-spacing: 7px; border-collapse: separate; margin: 0 -7px; }
.card {
    width: 25%;
    border: 1px solid #e0e0e0;
    border-radius: 12px;
    padding: 10px 6px;
    text-align: center;
    background: #fafafa;
}
.card .label { font-size: 9px; color: #607D8B; }
.card .value { font-size: 15px; font-weight: bold; margin-top: 5px; }
.green { color: #2E7D32; }
.red { color: #C62828; }
.blue { color: #1565C0; }
.orange { color: #EF6C00; }
.purple { color: #6A4C93; }
.net { background: #EDE7F6; border: 2px solid #B39DDB; }
.info-table { width: 100%; border-collapse: collapse; margin-top: 5px; }
.info-table td { padding: 6px 8px; border-bottom: 1px solid #eeeeee; }
.info-table td:first-child { color: #607D8B; font-weight: bold; width: 55%; }
.info-table td:last-child { text-align: left; font-weight: bold; }
table.data { width: 100%; border-collapse: collapse; margin-top: 7px; page-break-inside: auto; }
table.data thead { display: table-header-group; }
table.data tr { page-break-inside: avoid; page-break-after: auto; }
table.data th, table.data td { border: 1px solid #cfd8dc; padding: 5px 3px; text-align: center; font-size: 8px; }
table.data th { background: #F1EAF8; color: #4A315F; font-weight: bold; }
table.data td.total, table.data th.total { background: #E3F2FD; font-weight: bold; }
table.data tbody tr:nth-child(even) { background: #fafafa; }
.small { font-size: 8px; color: #78909C; }
.badge { display: inline-block; padding: 4px 8px; border-radius: 20px; background: #F1EAF8; color: #6A4C93; font-weight: bold; }
.two-col { width: 100%; border-spacing: 8px; border-collapse: separate; margin: 0 -8px; }
.two-col td { width: 50%; vertical-align: top; }
.panel { border: 1px solid #e0e0e0; border-radius: 12px; padding: 10px; background: #fff; }
.footer {
    margin-top: 18px;
    padding-top: 10px;
    border-top: 1px solid #d7d7d7;
    text-align: center;
    color: #78909C;
    font-size: 8px;
}
.page-break { page-break-before: always; }
</style>
</head>
<body>
<div class="page">
""")

            append("<div class='top'>")
            append("<div class='top-title'>دفتر الحسابات</div>")
            append("<div class='top-subtitle'>تقرير مالي وإداري شامل — ${escapeHtml(bundle.month.name)}</div>")
            append("<div class='profile'>")
            if (profileImageHtml.isNotEmpty()) append(profileImageHtml)
            if (userName.isNotBlank()) append("<div class='profile-name'>${escapeHtml(userName)}</div>")
            if (userPhone.isNotBlank()) append("<div class='profile-line'>📱 ${escapeHtml(userPhone)}</div>")
            if (userEmail.isNotBlank()) append("<div class='profile-line'>📧 ${escapeHtml(userEmail)}</div>")
            if (userShop.isNotBlank()) append("<div class='profile-line'>🏪 ${escapeHtml(userShop)}</div>")
            if (resolvedShopNumber.isNotBlank()) append("<div class='profile-line'>🔢 رقم المحل: ${escapeHtml(resolvedShopNumber)}</div>")
            append("<div class='clear'></div></div>")
            append("</div>")

            append("<div class='section-title'>الملخص المالي</div>")
            append("<table class='cards'><tr>")
            append(card("إجمالي الإنتاج", amount(totalEarned), "green"))
            append(card("المصروفات", amount(totalExpenses), "red"))
            append(card("الصافي", signed(net), if (net >= 0) "blue" else "red", net))
            append(card("إجمالي القطع", amount(totalPieces), "orange"))
            append("</tr></table>")

            append("<table class='two-col'><tr>")
            append("<td><div class='panel'><div class='section-title'>بيانات الشهر</div>")
            append("<table class='info-table'>")
            append("<tr><td>المحل</td><td>${escapeHtml(userShop.ifBlank { database.getShops().firstOrNull { it.id == bundle.month.shopId }?.name.orEmpty() })}</td></tr>")
            append("<tr><td>العامل</td><td>${escapeHtml(bundle.month.workerName)}</td></tr>")
            append("<tr><td>تاريخ البداية</td><td>${escapeHtml(bundle.month.startDate)}</td></tr>")
            append("<tr><td>خصم المصروف</td><td>${if (bundle.month.deductExpense) "نعم" else "لا"}</td></tr>")
            append("</table></div></td>")
            append("<td><div class='panel'><div class='section-title'>مؤشرات العمل</div>")
            append("<table class='info-table'>")
            append("<tr><td>أيام النشاط</td><td>${activeDays} يوم</td></tr>")
            append("<tr><td>أيام الإنتاج</td><td>${productionDays} يوم</td></tr>")
            append("<tr><td>أيام المصروفات</td><td>${expenseDays} يوم</td></tr>")
            append("<tr><td>نسبة النشاط</td><td>${activityPercent}%</td></tr>")
            append("<tr><td>متوسط الإنتاج</td><td>${amount(averageProduction)} ريال</td></tr>")
            append("<tr><td>متوسط المصروف</td><td>${amount(averageExpense)} ريال</td></tr>")
            append("</table></div></td>")
            append("</tr></table>")

            append("<div class='section-title'>ملخص القطع</div>")
            if (pieces.isEmpty()) {
                append("<div class='panel' style='text-align:center;color:#78909C;'>لا توجد قطع مسجلة لهذا المحل.</div>")
            } else {
                append("<table class='data'><thead><tr><th>#</th><th>القطعة</th><th>الكمية</th><th>الإيراد</th></tr></thead><tbody>")
                pieces.forEachIndexed { index, piece ->
                    val count = database.pieceTotal(bundle, piece.id)
                    val earned = database.pieceEarned(bundle, piece.id)
                    append("<tr><td>${index + 1}</td><td>${escapeHtml(piece.name)}</td><td>${amount(count)}</td><td>${amount(earned)} ريال</td></tr>")
                }
                append("</tbody></table>")
            }

            append("<div class='section-title'>السجل اليومي</div>")
            append("<table class='data'><thead><tr><th>اليوم</th><th>التاريخ</th>")
            pieces.forEach { append("<th>${escapeHtml(it.name)}</th>") }
            append("<th>المصروف</th><th class='total'>المجموع</th></tr></thead><tbody>")
            bundle.days.forEach { day ->
                val earned = database.calculateDayEarned(day)
                val dayTotal = if (bundle.month.deductExpense) earned - day.expense else earned
                append("<tr>")
                append("<td>${escapeHtml(day.dayName)}</td>")
                append("<td>${escapeHtml(day.date)}</td>")
                pieces.forEach { piece ->
                    val qty = day.quantities[piece.id] ?: 0
                    append("<td>${if (qty == 0) "—" else amount(qty)}</td>")
                }
                append("<td>${if (day.expense == 0) "—" else amount(day.expense)}</td>")
                append("<td class='total'>${if (earned == 0) "—" else signed(dayTotal)}</td>")
                append("</tr>")
            }
            append("</tbody><tfoot><tr>")
            append("<th colspan='2'>الإجمالي</th>")
            pieces.forEach { piece -> append("<th>${amount(database.pieceTotal(bundle, piece.id))}</th>") }
            append("<th>${amount(totalExpenses)}</th><th class='total'>${signed(net)}</th>")
            append("</tr></tfoot></table>")

            append("<div class='footer'>جميع الحقوق محفوظة © 2026 — تطبيق دفتر الحسابات / Add Paper</div>")
            append("</div></body></html>")
        }

        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        webView.settings.defaultTextEncodingName = "UTF-8"
        val activity = context as? Activity
        val decorView = activity?.window?.decorView as? ViewGroup
        webView.alpha = 0f
        decorView?.addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                webView.postDelayed({
                val jobName = "دفتر_${bundle.month.name}"
                try {
                    if (sharePdf) {
                        shareWebViewAsPdf(context, webView, jobName, userPhone, userEmail) {
                            decorView?.post { try { decorView.removeView(webView) } catch (_: Exception) { } }
                        }
                    } else {
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val adapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter(jobName)
                        printManager.print(
                            jobName,
                            adapter,
                            PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build()
                        )
                        decorView?.postDelayed({
                            try { decorView.removeView(webView) } catch (_: Exception) { }
                        }, 500L)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "تعذر إنشاء ملف PDF: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                    try { decorView?.removeView(webView) } catch (_: Exception) { }
                }
                }, 180L)
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    } catch (e: Exception) {
        Toast.makeText(context, "حدث خطأ أثناء إنشاء PDF: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
    }
}


internal fun generateIndividualPdf(
    context: Context,
    database: Database,
    bundle: MonthBundle,
    pieces: List<PieceRecord>,
    userName: String,
    userPhone: String,
    userEmail: String,
    userShop: String,
    userImagePath: String,
    shopRegistrationNumber: String = "",
    sharePdf: Boolean = false
) {
    try {
        fun amount(value: Int): String = String.format(java.util.Locale.US, "%,d", value)
        fun signed(value: Int): String = if (value < 0) "-${amount(-value)}" else amount(value)

        val daysWithEntries = bundle.days.mapNotNull { day ->
            val entries = database.getIndividualEntries(day.monthId, day.date)
            if (entries.isEmpty() && day.expense <= 0) null else day to entries
        }

        val allEntries = daysWithEntries.flatMap { it.second }
        val totalEarned = allEntries.sumOf { database.calculateIndividualEntryEarned(it) }
        val totalExpenses = daysWithEntries.sumOf { it.first.expense }
        val net = if (bundle.month.deductExpense) totalEarned - totalExpenses else totalEarned
        val totalCustomers = allEntries.count { it.customerName.isNotBlank() || it.pageNumber.isNotBlank() }
        val totalPieces = allEntries.sumOf { entry -> entry.quantities.values.sum() }
        val activeDays = daysWithEntries.size
        val averageDaily = if (activeDays > 0) totalEarned / activeDays else 0
        val profileImageHtml = buildProfileImageHtml(userImagePath)
        val shopName = userShop.ifBlank {
            database.getShops().firstOrNull { it.id == bundle.month.shopId }?.name.orEmpty()
        }
        val resolvedShopNumber = shopRegistrationNumber.ifBlank {
            database.getShops().firstOrNull { it.id == bundle.month.shopId }?.registrationNumber.orEmpty()
        }

        val html = buildString {
            append("""
<!DOCTYPE html>
<html dir="rtl" lang="ar">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
@page { size: A4 landscape; margin: 8mm; }
* { box-sizing: border-box; }
body {
    font-family: Arial, Tahoma, sans-serif;
    direction: rtl;
    margin: 0;
    padding: 0;
    color: #263238;
    background: #fff;
    font-size: 9px;
}
.header {
    background: linear-gradient(135deg, #6A3E91, #8361B5);
    color: #fff;
    border-radius: 12px;
    padding: 12px 15px;
    margin-bottom: 8px;
}
.title { font-size: 20px; font-weight: bold; margin-bottom: 3px; }
.subtitle { font-size: 10px; opacity: .9; }
.profile {
    margin-top: 8px;
    padding: 7px;
    border: 1px solid rgba(255,255,255,.25);
    background: rgba(255,255,255,.12);
    border-radius: 9px;
}
.profile img { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; float: right; margin-left: 8px; }
.profile-name { font-size: 11px; font-weight: bold; }
.profile-line { font-size: 8px; margin-top: 2px; }
.clear { clear: both; }
.cards { width: 100%; border-collapse: separate; border-spacing: 5px; margin: 0 -5px 6px; }
.card { width: 20%; border: 1px solid #ddd; border-radius: 9px; padding: 7px; text-align: center; background: #fafafa; }
.card .label { font-size: 8px; color: #607D8B; }
.card .value { font-size: 14px; font-weight: bold; margin-top: 3px; }
.green { color: #2E7D32; }
.red { color: #C62828; }
.blue { color: #1565C0; }
.orange { color: #EF6C00; }
.purple { color: #6A4C93; }
.net { background: #EDE7F6; border: 2px solid #B39DDB; }
.info { width: 100%; border-collapse: collapse; margin-bottom: 8px; }
.info td { border: 1px solid #e1d8e8; padding: 5px 7px; }
.info .label { background: #F4EFF8; color: #5A3A78; font-weight: bold; width: 13%; }
.info .value { font-weight: bold; width: 20%; }
.section {
    font-size: 12px;
    font-weight: bold;
    color: #6A3E91;
    border-right: 4px solid #6A3E91;
    padding-right: 6px;
    margin: 8px 0 5px;
}
table.data { width: 100%; border-collapse: collapse; page-break-inside: auto; }
table.data thead { display: table-header-group; }
table.data tr { page-break-inside: avoid; }
table.data th, table.data td { border: 1px solid #bfc5cc; padding: 4px 3px; text-align: center; }
table.data th { background: #6A3E91; color: #fff; font-weight: bold; font-size: 8px; }
table.data td { font-size: 8px; }
table.data tr:nth-child(even) td { background: #fafafa; }
.total { background: #E8F5E9 !important; color: #1B5E20; font-weight: bold; }
.day-title { background: #FFF0B3 !important; color: #4E3B00; font-weight: bold; }
.footer { margin-top: 10px; border-top: 1px solid #ddd; padding-top: 6px; text-align: center; color: #78909C; font-size: 7px; }
</style>
</head>
<body>
<div class="header">
    <div class="title">التسجيل الفردي — تقرير PDF</div>
    <div class="subtitle">${escapeHtml(bundle.month.name)} — ${bundle.month.year}</div>
    <div class="profile">
""")
            if (profileImageHtml.isNotEmpty()) append(profileImageHtml)
            if (userName.isNotBlank()) append("<div class='profile-name'>${escapeHtml(userName)}</div>")
            if (userPhone.isNotBlank()) append("<div class='profile-line'>الهاتف: ${escapeHtml(userPhone)}</div>")
            if (userEmail.isNotBlank()) append("<div class='profile-line'>البريد: ${escapeHtml(userEmail)}</div>")
            if (shopName.isNotBlank()) append("<div class='profile-line'>المحل: ${escapeHtml(shopName)}</div>")
            if (resolvedShopNumber.isNotBlank()) append("<div class='profile-line'>رقم المحل: ${escapeHtml(resolvedShopNumber)}</div>")
            append("<div class='clear'></div></div></div>")

            append("<table class='cards'><tr>")
            append(card("إجمالي الإنتاج", "${amount(totalEarned)} ريال", "green"))
            append(card("المصروفات", "${amount(totalExpenses)} ريال", "red"))
            append(card("الصافي", "${signed(net)} ريال", if (net >= 0) "blue" else "red", net))
            append(card("الزبائن", amount(totalCustomers), "orange"))
            append(card("إجمالي القطع", amount(totalPieces), "purple"))
            append("</tr></table>")

            append("<table class='info'><tr>")
            append("<td class='label'>الأيام المسجلة</td><td class='value'>$activeDays يوم</td>")
            append("<td class='label'>متوسط الإنتاج اليومي</td><td class='value'>${amount(averageDaily)} ريال</td>")
            append("<td class='label'>خصم المصروف</td><td class='value'>${if (bundle.month.deductExpense) "نعم" else "لا"}</td>")
            append("</tr></table>")

            append("<div class='section'>تفاصيل التسجيل الفردي حسب الأيام</div>")
            if (daysWithEntries.isEmpty()) {
                append("<div style='text-align:center;padding:20px;border:1px solid #ddd;'>لا توجد تسجيلات فردية في هذا الشهر.</div>")
            } else {
                daysWithEntries.forEach { (day, entries) ->
                    val dayEarned = database.calculateIndividualDayEarned(entries)
                    val dayNet = if (bundle.month.deductExpense) dayEarned - day.expense else dayEarned
                    append("<div style='margin-top:7px;font-weight:bold;color:#5A3A78;'>${escapeHtml(day.dayName)} — ${escapeHtml(day.date)} — إجمالي اليوم: ${signed(dayNet)} ريال</div>")
                    append("<table class='data'><thead><tr>")
                    append("<th>اسم الزبون</th><th>رقم الصفحة</th>")
                    pieces.forEach { piece -> append("<th>${escapeHtml(piece.name)}<br>سعر: ${amount(piece.price)}</th>") }
                    append("<th class='total'>المجموع</th></tr></thead><tbody>")
                    if (entries.isEmpty()) {
                        append("<tr><td colspan='${pieces.size + 3}' style='color:#78909C;'>لا توجد حسابات فردية — يوجد مصروف فقط</td></tr>")
                    } else {
                        entries.forEach { entry ->
                            val entryTotal = database.calculateIndividualEntryEarned(entry)
                            append("<tr>")
                            append("<td>${escapeHtml(entry.customerName.ifBlank { "—" })}</td>")
                            append("<td>${escapeHtml(entry.pageNumber.ifBlank { "—" })}</td>")
                            pieces.forEach { piece ->
                                val qty = entry.quantities[piece.id] ?: 0
                                append("<td>${if (qty == 0) "—" else amount(qty)}</td>")
                            }
                            append("<td class='total'>${amount(entryTotal)} ريال</td>")
                            append("</tr>")
                        }
                        append("<tr>")
                        append("<th colspan='2'>إجمالي اليوم</th>")
                        pieces.forEach { piece ->
                            val count = entries.sumOf { it.quantities[piece.id] ?: 0 }
                            append("<th>${amount(count)}</th>")
                        }
                        append("<th class='total'>${signed(dayNet)} ريال</th>")
                        append("</tr>")
                    }
                    append("</tbody></table>")
                    if (day.expense > 0) {
                        append("<div style='margin-top:3px;color:#C62828;font-weight:bold;'>مصروف اليوم: ${amount(day.expense)} ريال${if (day.expenseNote.isNotBlank()) " — ${escapeHtml(day.expenseNote)}" else ""}</div>")
                    }
                }
            }

            append("<div class='footer'>تقرير التسجيل الفردي — ${escapeHtml(bundle.month.name)} — تطبيق دفتر الحسابات / Add Paper</div>")
            append("</body></html>")
        }

        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        webView.settings.defaultTextEncodingName = "UTF-8"
        val activity = context as? Activity
        val decorView = activity?.window?.decorView as? ViewGroup
        webView.alpha = 0f
        decorView?.addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                webView.postDelayed({
                val jobName = "تسجيل_فردي_${bundle.month.name}"
                try {
                    if (sharePdf) {
                        shareWebViewAsPdf(context, webView, jobName, userPhone, userEmail) {
                            decorView?.post { try { decorView.removeView(webView) } catch (_: Exception) { } }
                        }
                    } else {
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val adapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter(jobName)
                        printManager.print(
                            jobName,
                            adapter,
                            PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build()
                        )
                        decorView?.postDelayed({
                            try { decorView.removeView(webView) } catch (_: Exception) { }
                        }, 700L)
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "تعذر إنشاء تقرير التسجيل الفردي: ${e.message ?: "خطأ غير معروف"}",
                        Toast.LENGTH_LONG
                    ).show()
                    try { decorView?.removeView(webView) } catch (_: Exception) { }
                }
                }, 180L)
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "حدث خطأ أثناء إنشاء تقرير التسجيل الفردي: ${e.message ?: "خطأ غير معروف"}",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun card(label: String, value: String, css: String, rawValue: Int? = null): String {
    val extra = if (rawValue != null && rawValue < 0) " net" else ""
    return "<td class='card$extra'><div class='label'>$label</div><div class='value $css'>$value</div></td>"
}

private fun buildProfileImageHtml(path: String): String {
    if (path.isBlank()) return ""
    return try {
        val file = File(path)
        if (!file.exists()) return ""
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return ""
        val scaled = Bitmap.createScaledBitmap(bitmap, 116, 116, true)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        bitmap.recycle()
        scaled.recycle()
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        "<img src='data:image/jpeg;base64,$base64' alt='الصورة الشخصية'/>"
    } catch (_: Exception) {
        ""
    }
}

