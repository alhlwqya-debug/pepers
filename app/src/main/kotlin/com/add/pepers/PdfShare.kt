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

/**
 * Creates a PDF from the already-rendered report WebView and shares it.
 *
 * Pagination uses one coordinate system: WebView/view pixels.
 * DOM/CSS coordinates are converted with WebView.scale before page
 * boundaries are calculated. This prevents rows/days from disappearing.
 */
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
        "${System.currentTimeMillis()}_${safeName}.pdf"
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
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    buildString {
                        append("تقرير PDF من تطبيق دفتر الحسابات")
                        if (phone.isNotBlank()) append(" — رقم التواصل: $phone")
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

    val landscape = jobName.contains("فردي")
    val pageWidth = if (landscape) 842 else 595
    val pageHeight = if (landscape) 595 else 842

    /**
     * breakPoints are VIEW pixels, not CSS pixels.
     *
     * WebView.contentHeight and DOM positions are CSS pixels, while
     * WebView.draw() uses view pixels. Both are converted with the exact
     * current WebView scale before pagination.
     */
    fun createPdf(breakPoints: List<Float>, contentHeightViewPx: Int) {
        try {
            val cssPageWidth = if (landscape) 1123 else 794
            val drawScale = pageWidth.toFloat() / cssPageWidth.toFloat()
            val pageContentHeightViewPx = pageHeight.toFloat() / drawScale
            val height = contentHeightViewPx.toFloat().coerceAtLeast(1f)

            val breaks = breakPoints
                .map { it.coerceIn(0f, height) }
                .filter { it > 1f && it < height - 1f }
                .distinct()
                .sorted()

            val pageStarts = mutableListOf<Float>()
            var start = 0f

            while (start < height - 1f) {
                pageStarts += start

                val target = minOf(
                    height,
                    start + pageContentHeightViewPx
                )

                if (target >= height - 1f) break

                /*
                 * Finish each page on a real DOM boundary whenever possible.
                 * Table rows are collected as complete units, so a day row
                 * is never intentionally cut between two PDF pages.
                 */
                val nextBoundary = breaks.firstOrNull {
                    it > target + 0.5f
                }

                val next = nextBoundary ?: target
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
                        scale(drawScale, drawScale)
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

    /**
     * Read actual rendered DOM boundaries.
     * Table rows are the primary boundaries; major report blocks are
     * secondary boundaries. All returned CSS coordinates are converted
     * to WebView view pixels before pagination.
     */
    fun collectBreaks(contentHeightCssPx: Int, webViewScale: Float) {
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

                document.querySelectorAll(
                    'table.data tbody tr, table.data tfoot tr'
                ).forEach(function(el) {
                    add(bottomOf(el));
                });

                document.querySelectorAll(
                    '.section-title, .section, .top, .header, .profile, ' +
                    '.panel, .cards, .two-col'
                ).forEach(function(el) {
                    add(topOf(el));
                    add(bottomOf(el));
                });

                return points
                    .filter(function(v) {
                        return isFinite(v) && v >= 0;
                    })
                    .sort(function(a, b) { return a - b; });
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            try {
                val json = JSONArray(raw)
                val viewBreaks = ArrayList<Float>(json.length())

                for (i in 0 until json.length()) {
                    val cssValue = json.optDouble(i, Double.NaN)
                    if (!cssValue.isNaN() && cssValue.isFinite()) {
                        viewBreaks += cssValue.toFloat() * webViewScale
                    }
                }

                val contentHeightViewPx = ceil(
                    contentHeightCssPx.toFloat() * webViewScale
                ).toInt().coerceAtLeast(1)

                createPdf(viewBreaks, contentHeightViewPx)
            } catch (_: Exception) {
                val contentHeightViewPx = ceil(
                    contentHeightCssPx.toFloat() * webViewScale
                ).toInt().coerceAtLeast(1)

                createPdf(emptyList(), contentHeightViewPx)
            }
        }
    }

    fun waitUntilReady(attempt: Int = 0) {
        if (attempt < 20 && webView.contentHeight <= 0) {
            webView.postDelayed(
                { waitUntilReady(attempt + 1) },
                200L
            )
            return
        }

        webView.postDelayed({
            val cssPageWidth = if (landscape) 1123 else 794

            webView.measure(
                View.MeasureSpec.makeMeasureSpec(
                    cssPageWidth,
                    View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(
                    0,
                    View.MeasureSpec.UNSPECIFIED
                )
            )

            val measuredWidth = webView.measuredWidth.coerceAtLeast(1)
            val cssHeight = webView.contentHeight.coerceAtLeast(1)

            webView.layout(
                0,
                0,
                measuredWidth,
                cssHeight
            )
            webView.requestLayout()
            webView.invalidate()

            webView.postDelayed({
                collectBreaks(cssHeight, 1f)
            }, 150L)
        }, 200L)
    }

    webView.post {
        waitUntilReady()
    }
}
