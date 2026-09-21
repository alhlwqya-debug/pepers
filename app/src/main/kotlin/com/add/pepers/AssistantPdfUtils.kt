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

    fun esc(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    fun money(value: Int): String = String.format(Locale.US, "%,d", value)

    val html = buildString {
        append("<!doctype html><html dir='rtl' lang='ar'><head><meta charset='utf-8'>")
        append("<style>body{font-family:Arial,Tahoma,sans-serif;direction:rtl;color:#263238;font-size:11px}h1{font-size:20px}h2{font-size:14px;margin-top:18px}.box{padding:10px;border:1px solid #ddd;border-radius:8px;margin-bottom:10px}table{width:100%;border-collapse:collapse}th,td{border:1px solid #ddd;padding:6px;text-align:right}th{background:#f1eef7}.total{font-weight:bold}</style></head><body>")
        append("<h1>كشف حساب المساعد</h1>")
        append("<div class='box'><b>المحل:</b> " + esc(shop.name) + "<br><b>الخياط:</b> " + esc(userName) + "<br><b>المساعد:</b> " + esc(assistant.name) + "<br><b>المهمة:</b> " + esc(assistant.task) + "<br><b>بداية العمل:</b> " + esc(assistant.startDate) + "<br><b>نهاية العمل:</b> " + esc(assistant.endDate ?: "مستمر") + "</div>")
        append("<table><tr><th>البيان</th><th>المبلغ</th></tr>")
        append("<tr><td>مستحقات القطع</td><td>" + money(earned) + "</td></tr>")
        append("<tr><td>مصروف/بدلات</td><td>" + money(expenses) + "</td></tr>")
        append("<tr><td>السحبيات</td><td>-" + money(withdrawn) + "</td></tr>")
        append("<tr class='total'><td>الرصيد المستحق</td><td>" + money(balance) + "</td></tr></table>")
        append("<h2>السجل اليومي</h2><table><tr><th>التاريخ</th><th>الحالة</th><th>مستحقات القطع</th><th>مصروف</th><th>ملاحظة</th></tr>")
        bundles.forEach { bundle ->
            bundle.days.forEach { day ->
                if (day.date < assistant.startDate || (assistant.endDate != null && day.date > assistant.endDate!!)) return@forEach
                val daily = database.getAssistantDailyRecord(assistant.id, day.id)
                val status = when (daily?.status) {
                    AssistantDailyStatus.ABSENT -> "غياب"
                    AssistantDailyStatus.NO_WORK -> "لا يوجد عمل"
                    else -> "يعمل"
                }
                val dayEarned = database.calculateAssistantDayEarned(assistant, bundle, day)
                val dayExpense = daily?.expense ?: 0
                if (dayEarned > 0 || dayExpense > 0 || daily != null) {
                    append("<tr><td>" + esc(day.date) + "</td><td>" + status + "</td><td>" + money(dayEarned) + "</td><td>" + money(dayExpense) + "</td><td>" + esc(daily?.notes.orEmpty()) + "</td></tr>")
                }
            }
        }
        append("</table><h2>السحبيات الخارجية</h2><table><tr><th>التاريخ</th><th>المبلغ</th><th>البيان</th></tr>")
        withdrawals.sortedBy { it.date }.forEach { w ->
            append("<tr><td>" + esc(w.date) + "</td><td>" + money(w.amount) + "</td><td>" + esc(w.note) + "</td></tr>")
        }
        append("</table></body></html>")
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
                userPhone = userPhone,
                userEmail = userEmail
            ) {
                parent.post { runCatching { parent.removeView(webView) } }
            }
        }, 500L)
    }
}
