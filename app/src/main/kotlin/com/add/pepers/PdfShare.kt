package com.add.pepers

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Creates and shares a WebView report as a real multi-page PDF.
 *
 * WebView's PrintDocumentAdapter is used instead of slicing WebView.draw()
 * into fixed-height PdfDocument pages. The Android print framework performs
 * the HTML pagination, avoiding arbitrary cuts through headings and rows.
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

    var finished = false

    fun finishWithError(message: String) {
        if (finished) return
        finished = true
        pdfFile.delete()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        onFinished()
    }

    fun finishSuccessfully() {
        if (finished) return

        if (!pdfFile.exists() || pdfFile.length() < 100L) {
            finishWithError("تم إنشاء ملف PDF فارغ أو غير صالح")
            return
        }

        finished = true

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
            finished = false
            finishWithError(
                "تعذر مشاركة ملف PDF: ${e.message ?: "خطأ غير معروف"}"
            )
        }
    }

    try {
        if (webView.width <= 0 || webView.height <= 0) {
            webView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                    context.resources.displayMetrics.widthPixels.coerceAtLeast(1),
                    android.view.View.MeasureSpec.EXACTLY
                ),
                android.view.View.MeasureSpec.makeMeasureSpec(
                    0,
                    android.view.View.MeasureSpec.UNSPECIFIED
                )
            )
            webView.layout(
                0,
                0,
                webView.measuredWidth.coerceAtLeast(1),
                webView.measuredHeight.coerceAtLeast(1)
            )
        }

        val adapter = webView.createPrintDocumentAdapter(jobName)

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        adapter.onLayout(
            null,
            printAttributes,
            CancellationSignal(),
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(
                    info: PrintDocumentInfo,
                    changed: Boolean
                ) {
                    if (finished) return

                    if (info.pageCount == 0) {
                        finishWithError("تعذر حساب صفحات التقرير")
                        return
                    }

                    try {
                        val destination = ParcelFileDescriptor.open(
                            pdfFile,
                            ParcelFileDescriptor.MODE_CREATE or
                                ParcelFileDescriptor.MODE_TRUNCATE or
                                ParcelFileDescriptor.MODE_WRITE_ONLY
                        )

                        adapter.onWrite(
                            arrayOf(PageRange.ALL_PAGES),
                            destination,
                            CancellationSignal(),
                            object : PrintDocumentAdapter.WriteResultCallback() {
                                override fun onWriteFinished(
                                    pages: Array<PageRange>
                                ) {
                                    try {
                                        destination.close()
                                    } catch (_: Exception) {
                                    }
                                    finishSuccessfully()
                                }

                                override fun onWriteFailed(error: CharSequence?) {
                                    try {
                                        destination.close()
                                    } catch (_: Exception) {
                                    }
                                    finishWithError(
                                        "تعذر كتابة ملف PDF: " +
                                            (error?.toString()
                                                ?: "خطأ غير معروف")
                                    )
                                }

                                override fun onWriteCancelled() {
                                    try {
                                        destination.close()
                                    } catch (_: Exception) {
                                    }
                                    finishWithError("تم إلغاء إنشاء ملف PDF")
                                }
                            }
                        )
                    } catch (e: Exception) {
                        finishWithError(
                            "تعذر فتح ملف PDF: " +
                                (e.message ?: "خطأ غير معروف")
                        )
                    }
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    finishWithError(
                        "تعذر تنسيق صفحات PDF: " +
                            (error?.toString() ?: "خطأ غير معروف")
                    )
                }

                override fun onLayoutCancelled() {
                    finishWithError("تم إلغاء تنسيق ملف PDF")
                }
            },
            Bundle()
        )
    } catch (e: Exception) {
        finishWithError(
            "تعذر إنشاء PDF للمشاركة: " +
                (e.message ?: "خطأ غير معروف")
        )
    }
}
