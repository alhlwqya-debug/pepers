package com.add.pepers

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
    val adapter = webView.createPrintDocumentAdapter(jobName)
    val attributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()

    fun fail(message: String) {
        pdfFile.delete()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        onFinished()
    }

    try {
        adapter.onLayout(
            null,
            attributes,
            CancellationSignal(),
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    try {
                        val descriptor = ParcelFileDescriptor.open(
                            pdfFile,
                            ParcelFileDescriptor.MODE_CREATE or
                                ParcelFileDescriptor.MODE_TRUNCATE or
                                ParcelFileDescriptor.MODE_WRITE_ONLY
                        )
                        adapter.onWrite(
                            arrayOf(PageRange.ALL_PAGES),
                            descriptor,
                            CancellationSignal(),
                            object : PrintDocumentAdapter.WriteResultCallback() {
                                private fun closeDescriptor() {
                                    try { descriptor.close() } catch (_: Exception) { }
                                }

                                override fun onWriteFinished(pages: Array<PageRange>) {
                                    closeDescriptor()
                                    try {
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
                                    } catch (e: Exception) {
                                        fail("تعذر فتح خيارات المشاركة: " + (e.message ?: "خطأ غير معروف"))
                                        return
                                    }
                                    Toast.makeText(context, "تم تجهيز ملف PDF للمشاركة", Toast.LENGTH_SHORT).show()
                                    onFinished()
                                }

                                override fun onWriteFailed(error: CharSequence?) {
                                    closeDescriptor()
                                    fail("تعذر إنشاء ملف PDF للمشاركة: " + (error ?: "خطأ غير معروف"))
                                }

                                override fun onWriteCancelled() {
                                    closeDescriptor()
                                    fail("تم إلغاء إنشاء ملف PDF")
                                }
                            }
                        )
                    } catch (e: Exception) {
                        fail("تعذر تجهيز ملف PDF: " + (e.message ?: "خطأ غير معروف"))
                    }
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    fail("تعذر تنسيق ملف PDF: " + (error ?: "خطأ غير معروف"))
                }

                override fun onLayoutCancelled() {
                    fail("تم إلغاء تجهيز ملف PDF")
                }
            },
            Bundle()
        )
    } catch (e: Exception) {
        fail("تعذر إنشاء ملف PDF للمشاركة: " + (e.message ?: "خطأ غير معروف"))
    }
}
