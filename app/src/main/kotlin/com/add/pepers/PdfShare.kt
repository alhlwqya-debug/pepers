package com.add.pepers

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Creates and shares a WebView report as a PDF.
 *
 * This path intentionally uses PdfDocument + WebView.draw() instead of
 * instantiating PrintDocumentAdapter callbacks. Android's
 * LayoutResultCallback/WriteResultCallback constructors are package-private,
 * so they cannot be created by application code.
 *
 * Pagination is based on real DOM boundaries. The page end is moved to the
 * last safe boundary that fits on the page, so section headings and table
 * rows are not cut across pages.
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
        "${System.currentTimeMillis()}_$safeName.pdf"
    )

    fun finishWithError(message: String) {
        pdfFile.delete()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        onFinished()
    }

    fun shareCreatedPdf() {
        if (!pdfFile.exists() || pdfFile.length() < 100L) {
            finishWithError("تم إنشاء ملف PDF فارغ أو غير صالح")
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
            Toast.makeText(
                context,
                "تم تجهيز ملف PDF للمشاركة",
                Toast.LENGTH_SHORT
            ).show()
            onFinished()
        } catch (e: Exception) {
            finishWithError(
                "تعذر مشاركة ملف PDF: ${e.message ?: "خطأ غير معروف"}"
            )
        }
    }

    fun createPdf(safeBreaks: List<Float>) {
        try {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            val pageWidth = 595
            val pageHeight = 842
            val viewWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(1)

            webView.measure(
                View.MeasureSpec.makeMeasureSpec(
                    viewWidth,
                    View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(
                    0,
                    View.MeasureSpec.UNSPECIFIED
                )
            )

            val measuredWidth = webView.measuredWidth.coerceAtLeast(1)
            val cssContentHeight = (
                webView.contentHeight * webView.scale.coerceAtLeast(0.1f)
            ).roundToInt()

            val measuredHeight = maxOf(
                webView.measuredHeight,
                cssContentHeight,
                viewWidth
            ).coerceAtLeast(1)

            webView.layout(
                0,
                0,
                measuredWidth,
                measuredHeight
            )
            webView.requestLayout()
            webView.invalidate()

            if (webView.measuredWidth <= 0 || webView.measuredHeight <= 0) {
                finishWithError("تعذر تجهيز محتوى التقرير قبل إنشاء PDF")
                return
            }

            val scale = pageWidth.toFloat() / measuredWidth.toFloat()
            val contentHeightPerPage = pageHeight.toFloat() / scale
            val documentHeight = measuredHeight.toFloat()

            val normalizedBreaks = safeBreaks
                .map { it.coerceIn(0f, documentHeight) }
                .filter { it > 8f && it < documentHeight - 8f }
                .distinct()
                .sorted()

            /*
             * Every page starts at a safe DOM boundary. We choose the last
             * safe boundary that still fits in the current physical page.
             *
             * This is different from merely changing the NEXT page start:
             * the current page itself ends at the selected boundary, so a
             * heading such as "ملخص القطع" is never left half visible at the
             * bottom of the previous page.
             */
            val pageStarts = mutableListOf<Float>()
            var startY = 0f

            while (startY < documentHeight - 1f) {
                pageStarts += startY

                val targetEnd = (startY + contentHeightPerPage)
                    .coerceAtMost(documentHeight)

                if (targetEnd >= documentHeight - 1f) {
                    break
                }

                val candidate = normalizedBreaks
                    .lastOrNull {
                        it > startY + 24f && it <= targetEnd + 0.5f
                    }

                val nextStart = candidate ?: targetEnd

                if (nextStart <= startY + 1f) {
                    break
                }

                startY = nextStart
            }

            if (pageStarts.isEmpty()) {
                pageStarts += 0f
            }

            val originalAlpha = webView.alpha
            webView.alpha = 1f

            try {
                PdfDocument().use { document ->
                    pageStarts.forEachIndexed { pageIndex, start ->
                        val page = document.startPage(
                            PdfDocument.PageInfo.Builder(
                                pageWidth,
                                pageHeight,
                                pageIndex + 1
                            ).create()
                        )

                        page.canvas.apply {
                            save()
                            drawColor(android.graphics.Color.WHITE)
                            scale(scale, scale)
                            translate(0f, -start)
                            webView.draw(this)
                            restore()
                        }

                        document.finishPage(page)
                    }

                    FileOutputStream(pdfFile).use { output ->
                        document.writeTo(output)
                        output.flush()
                    }
                }
            } finally {
                webView.alpha = originalAlpha
            }

            shareCreatedPdf()
        } catch (e: Exception) {
            finishWithError(
                "تعذر إنشاء ملف PDF للمشاركة: " +
                    (e.message ?: "خطأ غير معروف")
            )
        }
    }

    fun collectSafeBreaks(callback: (List<Float>) -> Unit) {
        /*
         * WebView.draw() renders the whole document and does not perform
         * browser print pagination. We therefore collect actual rendered DOM
         * boundaries and use them as page cut points.
         *
         * Important boundaries:
         * - top of section headings
         * - top/bottom of panels and cards
         * - bottom of every data-table row
         *
         * A section heading is deliberately a boundary itself. If the
         * heading is too close to the bottom of a page, the previous page ends
         * before the heading and the whole section begins on the next page.
         */
        val script = """
            (function() {
                var points = [0];

                function add(value) {
                    if (typeof value !== 'number' || !isFinite(value)) return;
                    points.push(value + window.scrollY);
                }

                function topOf(el) {
                    var rect = el.getBoundingClientRect();
                    return rect.top;
                }

                function bottomOf(el) {
                    var rect = el.getBoundingClientRect();
                    return rect.bottom;
                }

                document.querySelectorAll(
                    '.section-title, .section'
                ).forEach(function(el) {
                    add(topOf(el));
                });

                document.querySelectorAll(
                    '.top, .header, .profile, .panel, .cards, .two-col, .footer'
                ).forEach(function(el) {
                    add(topOf(el));
                    add(bottomOf(el));
                });

                document.querySelectorAll(
                    'table.data tbody tr, table.data tfoot tr'
                ).forEach(function(row) {
                    add(bottomOf(row));
                });

                return points
                    .filter(function(v) { return isFinite(v); })
                    .sort(function(a, b) { return a - b; });
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            try {
                val array = JSONArray(raw)
                val scale = webView.scale.coerceAtLeast(0.1f)
                val breaks = ArrayList<Float>(array.length())

                for (i in 0 until array.length()) {
                    val value = array.optDouble(i, Double.NaN)
                    if (!value.isNaN() && value.isFinite()) {
                        breaks += (value.toFloat() * scale)
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

        if (attempt < 12 && contentHeight <= 0) {
            webView.postDelayed(
                { waitForWebView(attempt + 1) },
                200L
            )
            return
        }

        webView.postDelayed({
            collectSafeBreaks { breaks ->
                createPdf(breaks)
            }
        }, 120L)
    }

    webView.post {
        waitForWebView()
    }
}
