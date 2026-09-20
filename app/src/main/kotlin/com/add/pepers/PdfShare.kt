package com.add.pepers

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

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

    val pdfFile = File(
        reportsDir,
        "\${System.currentTimeMillis()}_\${safeName}.pdf"
    )

    fun finishError(message: String) {
        runCatching { pdfFile.delete() }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        onFinished()
    }

    fun shareFile() {
        if (!pdfFile.exists() || pdfFile.length() < 100L) {
            finishError("تم إنشاء ملف PDF فارغ أو غير صالح")
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "\${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    buildString {
                        append("تقرير PDF من تطبيق دفتر الحسابات")
                        if (phone.isNotBlank()) append(" — رقم التواصل: \$phone")
                    }
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
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            context.startActivity(chooser)
            Toast.makeText(
                context,
                "تم تجهيز التقرير الكامل للمشاركة",
                Toast.LENGTH_SHORT
            ).show()
            onFinished()
        } catch (e: Exception) {
            finishError(
                "تعذر مشاركة ملف PDF: ${e.message ?: "خطأ غير معروف"}"
            )
        }
    }

    /*
     * PdfUtils.kt uses A4 portrait for the numeric report and A4 landscape
     * for the individual report. Keep the share orientation identical.
     */
    val landscape = jobName.contains("فردي")
    val pageWidth = if (landscape) 842 else 595
    val pageHeight = if (landscape) 595 else 842

    fun createPdf(safeBreaks: List<Float>, contentHeight: Int) {
        try {
            val viewWidth = webView.measuredWidth.coerceAtLeast(1)
            val scale = pageWidth.toFloat() / viewWidth.toFloat()
            val pageContentHeight = pageHeight.toFloat() / scale

            val breaks = safeBreaks
                .map { it.coerceIn(0f, contentHeight.toFloat()) }
                .filter { it > 16f && it < contentHeight - 16f }
                .distinct()
                .sorted()

            val pageStarts = mutableListOf<Float>()
            var start = 0f

            while (start < contentHeight - 1f) {
                pageStarts += start

                val target = minOf(
                    contentHeight.toFloat(),
                    start + pageContentHeight
                )

                if (target >= contentHeight - 1f) break

                val safeEnd = breaks.lastOrNull {
                    it > start + 24f && it <= target + 0.5f
                }

                val next = safeEnd ?: target
                if (next <= start + 1f) break
                start = next
            }

            if (pageStarts.isEmpty()) pageStarts += 0f

            val document = PdfDocument()
            try {
                pageStarts.forEachIndexed { index, startY ->
                    val page = document.startPage(
                        PdfDocument.PageInfo.Builder(
                            pageWidth,
                            pageHeight,
                            index + 1
                        ).create()
                    )

                    page.canvas.apply {
                        drawColor(Color.WHITE)
                        save()
                        scale(scale, scale)
                        translate(0f, -startY)
                        webView.draw(this)
                        restore()
                    }

                    document.finishPage(page)
                }

                FileOutputStream(pdfFile).use { output ->
                    document.writeTo(output)
                    output.flush()
                }
            } finally {
                document.close()
            }

            shareFile()
        } catch (e: Exception) {
            finishError(
                "تعذر إنشاء ملف PDF للمشاركة: ${e.message ?: "خطأ غير معروف"}"
            )
        }
    }

    fun collectSafeBreaks(contentHeight: Int) {
        val script = """
            (function() {
                var points = [0];

                function add(v) {
                    if (typeof v === 'number' && isFinite(v)) points.push(v);
                }

                function topOf(el) {
                    return el.getBoundingClientRect().top + window.scrollY;
                }

                function bottomOf(el) {
                    return el.getBoundingClientRect().bottom + window.scrollY;
                }

                document.querySelectorAll('.section-title, .section')
                    .forEach(function(el) { add(topOf(el)); });

                document.querySelectorAll(
                    '.top, .header, .profile, .panel, .cards, .two-col'
                ).forEach(function(el) {
                    add(topOf(el));
                    add(bottomOf(el));
                });

                document.querySelectorAll(
                    'table.data tbody tr, table.data tfoot tr'
                ).forEach(function(el) {
                    add(bottomOf(el));
                });

                return points
                    .filter(function(v) { return isFinite(v); })
                    .sort(function(a, b) { return a - b; });
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            try {
                val json = JSONArray(raw)
                val result = ArrayList<Float>(json.length())

                for (i in 0 until json.length()) {
                    val value = json.optDouble(i, Double.NaN)
                    if (!value.isNaN() && value.isFinite()) {
                        result += value.toFloat()
                    }
                }

                createPdf(result, contentHeight)
            } catch (_: Exception) {
                createPdf(emptyList(), contentHeight)
            }
        }
    }

    fun waitUntilReady(attempt: Int = 0) {
        if (attempt < 15 && webView.contentHeight <= 0) {
            webView.postDelayed(
                { waitUntilReady(attempt + 1) },
                200L
            )
            return
        }

        webView.postDelayed({
            val displayWidth = context.resources.displayMetrics.widthPixels
                .coerceAtLeast(1)

            webView.measure(
                View.MeasureSpec.makeMeasureSpec(
                    displayWidth,
                    View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(
                    0,
                    View.MeasureSpec.UNSPECIFIED
                )
            )

            val measuredWidth = webView.measuredWidth.coerceAtLeast(1)
            val cssHeight = webView.contentHeight.coerceAtLeast(1)

            /*
             * WebView.contentHeight is CSS pixels. Convert to the WebView
             * measured coordinate system before drawing and paginating.
             */
            val convertedHeight = ceil(
                cssHeight.toFloat() * webView.scale
            ).toInt().coerceAtLeast(1)

            webView.layout(
                0,
                0,
                measuredWidth,
                convertedHeight
            )
            webView.requestLayout()
            webView.invalidate()

            webView.postDelayed({
                collectSafeBreaks(convertedHeight)
            }, 100L)
        }, 150L)
    }

    webView.post {
        waitUntilReady()
    }
}
