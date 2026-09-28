package com.add.pepers

import android.app.Activity
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.util.Locale

internal fun shareAssistantLedgerPdf(
    context: Context,
    database: Database,
    shop: ShopRecord,
    assistant: AssistantRecord,
    userName: String,
    userPhone: String,
    userEmail: String
) {
    val activity = context as? Activity
    if (activity == null) {
        Toast.makeText(context, "تعذر فتح تقرير PDF من هذا السياق", Toast.LENGTH_LONG).show()
        return
    }

    val bundles = database.getMonths(shop.id).mapNotNull { database.loadMonthBundle(it.id) }
    val withdrawals = database.getAssistantWithdrawals(assistant.id)
    val earned = bundles.sumOf { database.calculateAssistantEarned(assistant, it) }
    val expenses = bundles.sumOf { database.calculateAssistantExpense(assistant.id, it) }
    val withdrawn = withdrawals.sumOf { it.amount }
    val balance = earned + expenses - withdrawn

    fun esc(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    fun money(value: Int): String = String.format(Locale.US, "%,d", value)

    val html = buildString {
        append("<!doctype html><html dir='rtl' lang='ar'><head><meta charset='utf-8'>")
        append("""
            <style>
            @page { size: A4; margin: 14mm 12mm 16mm 12mm; }
            * { box-sizing:border-box; }
            body { font-family: Arial,Tahoma,sans-serif; direction:rtl; color:#27232d; font-size:10px; margin:0; background:#fff; }
            .brand { border-bottom:2px solid #6f4aa0; padding-bottom:10px; margin-bottom:12px; }
            .brand h1 { margin:0; color:#6f4aa0; font-size:22px; }
            .brand .sub { margin-top:4px; color:#77707f; font-size:10px; }
            .profile { border:1px solid #ded8e5; border-radius:12px; padding:10px 12px; margin-bottom:10px; background:#faf8fc; }
            .profile-grid { width:100%; border-collapse:collapse; }
            .profile-grid td { border:0; padding:4px 5px; vertical-align:top; }
            .label { color:#77707f; font-size:9px; }
            .value { color:#27232d; font-size:11px; font-weight:bold; }
            .summary { width:100%; border-collapse:separate; border-spacing:6px; margin:0 -6px 10px; }
            .summary td { width:25%; border:1px solid #ded8e5; border-radius:10px; padding:8px; text-align:center; background:#fff; }
            .summary .label { display:block; margin-bottom:4px; }
            .summary .number { font-size:13px; font-weight:bold; color:#6f4aa0; }
            .summary .negative { color:#c84646; }
            .summary .positive { color:#2e8b57; }
            h2 { color:#6f4aa0; font-size:13px; margin:15px 0 6px; padding-bottom:5px; border-bottom:1px solid #e2dce8; }
            table.data { width:100%; border-collapse:collapse; margin-bottom:8px; }
            table.data th { background:#eee8f5; color:#4d3b60; font-weight:bold; }
            table.data th, table.data td { border:1px solid #d9d2df; padding:6px 5px; text-align:right; vertical-align:middle; }
            table.data tr:nth-child(even) td { background:#fbfafc; }
            .total td { font-weight:bold; background:#f1ebf7 !important; }
            .status-work { color:#2e8b57; font-weight:bold; }
            .status-absent { color:#c84646; font-weight:bold; }
            .status-no-work { color:#77707f; font-weight:bold; }
            .empty { color:#77707f; text-align:center; padding:10px; border:1px dashed #d9d2df; border-radius:8px; }
            .footer { margin-top:18px; padding-top:8px; border-top:1px solid #ddd6e2; color:#77707f; font-size:8px; text-align:center; }
            .signatures { width:100%; margin-top:28px; border-collapse:collapse; }
            .signatures td { width:50%; text-align:center; padding:18px 8px 4px; border:0; color:#77707f; }
            .line { border-top:1px solid #999; display:block; margin:0 25px 5px; }
            </style>
        """.trimIndent())
        append("</head><body>")

        append("<div class='brand'><h1>كشف حساب المساعد</h1><div class='sub'>سجل العمل والمستحقات والسحبيات — Pepers</div></div>")
        append("<div class='profile'><table class='profile-grid'>")
        append("<tr><td><span class='label'>المحل</span><br><span class='value'>${esc(shop.name)}</span></td>")
        append("<td><span class='label'>الخياط</span><br><span class='value'>${esc(userName)}</span></td></tr>")
        append("<tr><td><span class='label'>المساعد</span><br><span class='value'>${esc(assistant.name)}</span></td>")
        append("<td><span class='label'>المهمة</span><br><span class='value'>${esc(assistant.task.ifBlank { "غير محددة" })}</span></td></tr>")
        append("<tr><td><span class='label'>بداية العمل</span><br><span class='value'>${esc(assistant.startDate)}</span></td>")
        append("<td><span class='label'>نهاية العمل</span><br><span class='value'>${esc(assistant.endDate ?: "مستمر")}</span></td></tr>")
        append("</table></div>")

        append("<table class='summary'><tr>")
        append("<td><span class='label'>مستحقات القطع</span><span class='number'>${money(earned)} ريال</span></td>")
        append("<td><span class='label'>المصروف / البدلات</span><span class='number'>${money(expenses)} ريال</span></td>")
        append("<td><span class='label'>السحبيات</span><span class='number negative'>${money(withdrawn)} ريال</span></td>")
        append("<td><span class='label'>الرصيد المستحق</span><span class='number positive'>${money(balance)} ريال</span></td>")
        append("</tr></table>")

        append("<h2>السجل اليومي</h2>")
        append("<table class='data'><tr><th>التاريخ</th><th>الحالة</th><th>القطع</th><th>مستحقات القطع</th><th>مصروف</th><th>ملاحظة</th></tr>")
        var dailyRows = 0
        bundles.forEach { bundle ->
            bundle.days.forEach { day ->
                if (day.date < assistant.startDate || (assistant.endDate != null && day.date > assistant.endDate!!)) return@forEach
                val daily = database.getAssistantDailyRecord(assistant.id, day.id)
                val status = when (daily?.status) {
                    AssistantDailyStatus.ABSENT -> "غياب"
                    AssistantDailyStatus.NO_WORK -> "لا عمل"
                    else -> "عمل"
                }
                val statusClass = when (daily?.status) {
                    AssistantDailyStatus.ABSENT -> "status-absent"
                    AssistantDailyStatus.NO_WORK -> "status-no-work"
                    else -> "status-work"
                }
                val dayEarned = database.calculateAssistantDayEarned(assistant, bundle, day)
                val dayExpense = daily?.expense ?: 0
                val dayPieces = daily?.piecesCount ?: if (dayEarned > 0) day.quantities.values.sum() else 0
                if (dayEarned > 0 || dayExpense > 0 || daily != null) {
                    dailyRows++
                    append("<tr><td>${esc(day.date)}</td><td class='$statusClass'>$status</td><td>${money(dayPieces)}</td><td>${money(dayEarned)}</td><td>${money(dayExpense)}</td><td>${esc(daily?.notes.orEmpty())}</td></tr>")
                }
            }
        }
        if (dailyRows == 0) append("<tr><td colspan='6' class='empty'>لا توجد سجلات يومية حتى الآن</td></tr>")
        append("</table>")

        append("<h2>السحبيات الخارجية</h2>")
        append("<table class='data'><tr><th>التاريخ</th><th>المبلغ</th><th>البيان</th></tr>")
        if (withdrawals.isEmpty()) {
            append("<tr><td colspan='3' class='empty'>لا توجد سحبيات مسجلة</td></tr>")
        } else {
            withdrawals.sortedBy { it.date }.forEach { w ->
                append("<tr><td>${esc(w.date)}</td><td>${money(w.amount)} ريال</td><td>${esc(w.note)}</td></tr>")
            }
            append("<tr class='total'><td>الإجمالي</td><td>${money(withdrawn)} ريال</td><td></td></tr>")
        }
        append("</table>")

        append("<table class='signatures'><tr><td><span class='line'></span>توقيع الخياط</td><td><span class='line'></span>توقيع المساعد</td></tr></table>")
        append("<div class='footer'>تم إنشاء هذا التقرير من تطبيق Pepers — ${esc(shop.name)}</div>")
        append("</body></html>")
    }

    val webView = WebView(context).apply {
        settings.javaScriptEnabled = false
        settings.defaultTextEncodingName = "UTF-8"
        webViewClient = object : WebViewClient() {}
        loadDataWithBaseURL("https://pepers.local/", html, "text/html", "UTF-8", null)
    }

    activity.window.decorView.post {
        val parent = activity.window.decorView as? android.view.ViewGroup ?: return@post
        webView.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        parent.addView(webView)
        webView.postDelayed({
            shareWebViewAsPdf(
                context = context,
                webView = webView,
                jobName = "pepers_assistant_" + assistant.name,
                phone = userPhone,
                email = userEmail
            ) {
                parent.post { runCatching { parent.removeView(webView) } }
            }
        }, 500L)
    }
}
