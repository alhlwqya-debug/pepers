package com.add.pepers

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.view.View
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
    val safeName = jobName
        .replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
        .trim('_')
        .ifBlank { "report" }
    val pdfFile = File(reportsDir, "${System.currentTimeMillis()}_${safeName}.pdf")

    fun finishWithError(message: String) {
        pdfFile.delete()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        onFinished()
    }

    fun createPdf() {
        try {
            // A software layer makes WebView.draw() reliable when rendering
            // HTML into PdfDocument. Hardware WebView rendering can otherwise
            // produce blank pages on some Android devices.
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            val metrics = context.resources.displayMetrics
            val pageWidth = 595
            val pageHeight = 842
            val viewWidth = metrics.widthPixels.coerceAtLeast(1)

            val widthSpec = View.MeasureSpec.makeMeasureSpec(
                viewWidth,
                View.MeasureSpec.EXACTLY
            )
            val heightSpec = View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )

            webView.measure(widthSpec, heightSpec)

            val cssContentHeight = (webView.contentHeight * webView.scale.coerceAtLeast(0.1f))
                .roundToInt()
            val measuredHeight = maxOf(
                webView.measuredHeight,
                cssContentHeight,
                viewWidth
            ).coerceAtLeast(1)

            webView.layout(0, 0, viewWidth, measuredHeight)
            webView.requestLayout()
            webView.invalidate()

            if (webView.measuredWidth <= 0 || webView.measuredHeight <= 0) {
                finishWithError("تعذر تجهيز محتوى التقرير قبل إنشاء PDF")
                return
            }

            val originalAlpha = webView.alpha
            webView.alpha = 1f

            try {
                val document = PdfDocument()
                try {
                    val scale = pageWidth.toFloat() / webView.measuredWidth.toFloat()
                    val contentHeightPerPage = pageHeight.toFloat() / scale
                    val pageCount = ceil(
                        webView.measuredHeight.toFloat() / contentHeightPerPage
                    ).toInt().coerceAtLeast(1)

                    for (pageIndex in 0 until pageCount) {
                        val page = document.startPage(
                            PdfDocument.PageInfo.Builder(
                                pageWidth,
                                pageHeight,
                                pageIndex + 1
                            ).create()
                        )
                        val canvas = page.canvas
                        canvas.save()
                        canvas.drawColor(android.graphics.Color.WHITE)
                        canvas.scale(scale, scale)
                        canvas.translate(0f, -pageIndex * contentHeightPerPage)
                        webView.draw(canvas)
                        canvas.restore()
                        document.finishPage(page)
                    }

                    FileOutputStream(pdfFile).use { output ->
                        document.writeTo(output)
                        output.flush()
                    }
                } finally {
                    document.close()
                }
            } finally {
                webView.alpha = originalAlpha
            }

            if (!pdfFile.exists() || pdfFile.length() < 100L) {
                finishWithError("تم إنشاء ملف PDF فارغ أو غير صالح")
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "تقرير PDF من تطبيق دفتر الحسابات" +
                        if (phone.isNotBlank()) " — رقم التواصل: $phone" else ""
                )
                if (email.isNotBlank()) {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                }
                clipData = ClipData.newRawUri("تقرير PDF", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(
                shareIntent,
                "مشاركة تقرير PDF"
            ).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(chooser)
            Toast.makeText(context, "تم تجهيز ملف PDF للمشاركة", Toast.LENGTH_SHORT).show()
            onFinished()
        } catch (e: Exception) {
            finishWithError(
                "تعذر إنشاء ملف PDF للمشاركة: " +
                    (e.message ?: "خطأ غير معروف")
            )
        }
    }

    fun waitForWebView(attempt: Int = 0) {
        val contentHeight = webView.contentHeight
        val scale = webView.scale.coerceAtLeast(0.1f)

        // Wait until WebView has a real document height. This prevents the
        // zero-height/blank-PDF race that can occur immediately after load.
        if (attempt < 10 && contentHeight <= 0) {
            webView.postDelayed({ waitForWebView(attempt + 1) }, 200L)
            return
        }

        webView.postDelayed({
            createPdf()
        }, 100L)
    }

    webView.post {
        waitForWebView()
    }
}
