package com.add.pepers

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONArray
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

    fun createPdf(safeBreaks: List<Float> = emptyList()) {
        try {
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

            val scale = pageWidth.toFloat() / webView.measuredWidth.toFloat().coerceAtLeast(1f)
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

            val contentHeightPerPage = pageHeight.toFloat() / scale

            // WebView.draw() does not honor CSS page-break rules. Use DOM
            // boundaries collected before drawing so section titles and table
            // rows are not cut between PDF pages.
            val normalizedBreaks = safeBreaks
                .map { it.coerceIn(0f, measuredHeight.toFloat()) }
                .filter { it > 1f && it < measuredHeight - 1f }
                .distinct()
                .sorted()

            val pageStarts = mutableListOf<Float>()
            var currentStart = 0f
            while (currentStart < measuredHeight - 1f) {
                val targetEnd = (currentStart + contentHeightPerPage)
                    .coerceAtMost(measuredHeight.toFloat())

                val candidates = normalizedBreaks.filter {
                    it > currentStart + 24f && it <= targetEnd + 1f
                }

                val chosenEnd = if (targetEnd >= measuredHeight - 1f) {
                    measuredHeight.toFloat()
                } else {
                    candidates.lastOrNull() ?: targetEnd
                }

                pageStarts.add(currentStart)
                if (chosenEnd <= currentStart + 1f) break
                currentStart = chosenEnd
            }

            if (pageStarts.isEmpty()) pageStarts.add(0f)

            val originalAlpha = webView.alpha
            webView.alpha = 1f

            try {
                val document = PdfDocument()
                try {
                    pageStarts.forEachIndexed { pageIndex, startY ->
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
                        canvas.translate(0f, -startY)
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

    fun collectSafeBreaks(callback: (List<Float>) -> Unit) {
        // CSS page-break-inside is not enough for PdfDocument + WebView.draw().
        // Read the actual rendered DOM and collect safe page boundaries.
        val script = """
            (function() {
                var points = [0];
                function add(v) {
                    if (typeof v !== 'number' || !isFinite(v)) return;
                    points.push(Math.max(0, v + window.scrollY));
                }
                function top(el) {
                    var r = el.getBoundingClientRect();
                    return r.top + window.scrollY;
                }
                function bottom(el) {
                    var r = el.getBoundingClientRect();
                    return r.bottom + window.scrollY;
                }

                document.querySelectorAll('.section-title, .section').forEach(function(el) {
                    add(top(el));
                    var next = el.nextElementSibling;
                    if (next) add(bottom(next));
                });

                document.querySelectorAll('.top, .header, .profile, .panel, .cards, .two-col, .footer')
                    .forEach(function(el) {
                        add(top(el));
                        add(bottom(el));
                    });

                document.querySelectorAll('table.data tr').forEach(function(row) {
                    add(bottom(row));
                });

                add(document.body.scrollHeight);
                return points.sort(function(a, b) { return a - b; });
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            try {
                val array = JSONArray(raw)
                val scale = webView.scale.coerceAtLeast(0.1f)
                val breaks = buildList {
                    for (i in 0 until array.length()) {
                        add(array.optDouble(i, -1.0).toFloat() * scale)
                    }
                }
                callback(breaks)
            } catch (_: Exception) {
                callback(emptyList())
            }
        }
    }

    fun waitForWebView(attempt: Int = 0) {
        val contentHeight = webView.contentHeight

        if (attempt < 10 && contentHeight <= 0) {
            webView.postDelayed({ waitForWebView(attempt + 1) }, 200L)
            return
        }

        webView.postDelayed({
            collectSafeBreaks { breaks ->
                createPdf(breaks)
            }
        }, 100L)
    }

    webView.post {
        waitForWebView()
    }
}
