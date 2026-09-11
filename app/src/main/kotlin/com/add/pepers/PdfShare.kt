package com.add.pepers

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.roundToInt

internal fun shareWebViewAsPdf(
    context: Context,
    webView: WebView,
    jobName: String,
    phone: String,
    email: String,
    onFinished: () -> Unit
) {
    val reportsDir = File(context.cacheDir, "shared_reports").apply { mkdirs() }
    val safeName = jobName.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').ifBlank { "report" }
    val pdfFile = File(reportsDir, System.currentTimeMillis().toString() + "_" + safeName + ".pdf")

    fun fail(message: String) {
        pdfFile.delete()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        onFinished()
    }

    try {
        val metrics = context.resources.displayMetrics
        val viewWidth = metrics.widthPixels.coerceAtLeast(1)
        val contentHeight = maxOf(
            (webView.contentHeight * webView.scale).roundToInt(),
            webView.height,
            metrics.heightPixels
        ).coerceAtLeast(1)

        val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(
            viewWidth,
            android.view.View.MeasureSpec.EXACTLY
        )
        val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(
            contentHeight,
            android.view.View.MeasureSpec.EXACTLY
        )
        webView.measure(widthSpec, heightSpec)
        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)

        if (webView.measuredWidth <= 0 || webView.measuredHeight <= 0) {
            fail("تعذر قياس محتوى التقرير قبل إنشاء PDF")
            return
        }

        val originalAlpha = webView.alpha
        webView.alpha = 1f
        val document = PdfDocument()
        try {
            // أبعاد A4 بوحدة النقاط، مع تصغير محتوى WebView ليتناسب مع عرض الصفحة.
            val pageWidth = 595
            val pageHeight = 842
            val scale = pageWidth.toFloat() / webView.measuredWidth.toFloat()
            val contentHeightPerPage = pageHeight.toFloat() / scale
            val pageCount = ceil(webView.measuredHeight / contentHeightPerPage)
                .toInt()
                .coerceAtLeast(1)

            for (pageIndex in 0 until pageCount) {
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                )
                val canvas = page.canvas
                canvas.save()
                canvas.scale(scale, scale)
                canvas.translate(0f, -pageIndex * contentHeightPerPage)
                webView.draw(canvas)
                canvas.restore()
                document.finishPage(page)
            }

            FileOutputStream(pdfFile).use { output ->
                document.writeTo(output)
            }
        } finally {
            document.close()
            webView.alpha = originalAlpha
        }

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "تقرير PDF من تطبيق دفتر الحسابات" +
                    if (phone.isNotBlank()) " — رقم التواصل: " + phone else ""
            )
            if (email.isNotBlank()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "مشاركة تقرير PDF عبر الرسائل أو واتساب أو البريد")
        )
        Toast.makeText(context, "تم تجهيز ملف PDF للمشاركة", Toast.LENGTH_SHORT).show()
        onFinished()
    } catch (e: Exception) {
        fail("تعذر إنشاء ملف PDF للمشاركة: " + (e.message ?: "خطأ غير معروف"))
    }
}
