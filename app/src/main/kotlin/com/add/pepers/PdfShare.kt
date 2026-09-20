package com.add.pepers

import android.content.ClipData
import android.content.Context
import android.content.Intent
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

/*
 * The share path intentionally uses the same WebView PrintDocumentAdapter
 * that PdfUtils.kt uses for the working Android print path.
 *
 * This is important: do NOT render the WebView with PdfDocument/draw().
 * Android's print adapter is responsible for the final PDF pagination and
 * layout, so the shared PDF follows the same rendering path as printing.
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

    /*
     * Keep these attributes identical to PdfUtils.kt's working print path.
     * The report HTML itself still controls its @page orientation where
     * applicable; we deliberately do not introduce a second PDF renderer.
     */
    val printAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()

    val adapter = try {
        webView.createPrintDocumentAdapter(jobName)
    } catch (e: Exception) {
        finishError(
            "تعذر تجهيز محرك PDF: ${e.message ?: "خطأ غير معروف"}"
        )
        return
    }

    val cancellationSignal = CancellationSignal()
    var finished = false

    fun fail(message: String) {
        if (finished) return
        finished = true
        cancellationSignal.cancel()
        finishError(message)
    }

    try {
        /*
         * Run the exact same PrintDocumentAdapter lifecycle Android uses
         * for the working print button, but write the adapter output to our
         * own cache file so ACTION_SEND can attach that exact PDF.
         */
        adapter.onLayout(
            null,
            printAttributes,
            cancellationSignal,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(
                    info: PrintDocumentInfo,
                    changed: Boolean
                ) {
                    if (finished || cancellationSignal.isCanceled) {
                        if (!finished) {
                            finished = true
                            onFinished()
                        }
                        return
                    }

                    val destination = try {
                        ParcelFileDescriptor.open(
                            pdfFile,
                            ParcelFileDescriptor.MODE_CREATE or
                                ParcelFileDescriptor.MODE_TRUNCATE or
                                ParcelFileDescriptor.MODE_READ_WRITE
                        )
                    } catch (e: Exception) {
                        fail(
                            "تعذر إنشاء ملف PDF: ${e.message ?: "خطأ غير معروف"}"
                        )
                        return
                    }

                    try {
                        adapter.onWrite(
                            arrayOf(PageRange.ALL_PAGES),
                            destination,
                            cancellationSignal,
                            object : PrintDocumentAdapter.WriteResultCallback() {
                                override fun onWriteFinished(
                                    pages: Array<PageRange>
                                ) {
                                    runCatching { destination.close() }

                                    if (finished || cancellationSignal.isCanceled) {
                                        if (!finished) {
                                            finished = true
                                            onFinished()
                                        }
                                        return
                                    }

                                    if (!pdfFile.exists() || pdfFile.length() < 100L) {
                                        fail("تم إنشاء ملف PDF فارغ أو غير صالح")
                                        return
                                    }

                                    finished = true
                                    shareFile()
                                }

                                override fun onWriteFailed(error: CharSequence?) {
                                    runCatching { destination.close() }
                                    fail(
                                        "تعذر إنشاء ملف PDF للمشاركة: ${error ?: "خطأ غير معروف"}"
                                    )
                                }

                                override fun onWriteCancelled() {
                                    runCatching { destination.close() }
                                    if (!finished) {
                                        finished = true
                                        onFinished()
                                    }
                                }
                            }
                        )
                    } catch (e: Exception) {
                        runCatching { destination.close() }
                        fail(
                            "تعذر كتابة ملف PDF: ${e.message ?: "خطأ غير معروف"}"
                        )
                    }
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    fail(
                        "تعذر تجهيز التقرير للطباعة/PDF: ${error ?: "خطأ غير معروف"}"
                    )
                }

                override fun onLayoutCancelled() {
                    if (!finished) {
                        finished = true
                        onFinished()
                    }
                }
            },
            null
        )
    } catch (e: Exception) {
        fail(
            "تعذر تشغيل مولد PDF: ${e.message ?: "خطأ غير معروف"}"
        )
    }
}
