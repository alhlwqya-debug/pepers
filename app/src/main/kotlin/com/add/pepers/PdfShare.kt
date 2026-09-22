package com.add.pepers

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.print.PdfPrint
import android.print.PrintAttributes
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun shareWebViewAsPdf(
    context: Context,
    webView: WebView,
    jobName: String,
    phone: String,
    email: String,
    onFinished: () -> Unit
) {
    try {
        val documentsDir =
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir

        val reportDir = File(documentsDir, "Pepers/reports")
        if (!reportDir.exists() && !reportDir.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد التقارير")
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val safeName = jobName
            .trim()
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .ifBlank { "Pepers_Report" }
            .take(80)

        val pdfFile = File(reportDir, "${safeName}_$stamp.pdf")

        val attributes = PrintAttributes.Builder()
            .setMediaSize(
                if (jobName.contains("فردي")) {
                    PrintAttributes.MediaSize.ISO_A4.asLandscape()
                } else {
                    PrintAttributes.MediaSize.ISO_A4
                }
            )
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()

        val adapter = webView.createPrintDocumentAdapter(jobName)

        PdfPrint(attributes).print(
            adapter,
            pdfFile,
            object : PdfPrint.Callback {
                override fun onSuccess(file: File) {
                    try {
                        if (!file.exists() || file.length() <= 0L) {
                            throw IllegalStateException("ملف PDF الناتج فارغ")
                        }

                        val uri: Uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )

                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, jobName)
                            putExtra(Intent.EXTRA_TITLE, jobName)
                            clipData = ClipData.newRawUri("Pepers PDF", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        val chooser = Intent.createChooser(
                            send,
                            "مشاركة تقرير PDF"
                        ).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = ClipData.newRawUri("Pepers PDF", uri)
                        }

                        context.startActivity(chooser)

                        Toast.makeText(
                            context,
                            "تم إنشاء ملف PDF وفتح قائمة المشاركة.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "تم إنشاء PDF لكن تعذرت مشاركته: ${e.message ?: "خطأ غير معروف"}",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        onFinished()
                    }
                }

                override fun onFailure(error: Exception) {
                    Toast.makeText(
                        context,
                        "تعذر إنشاء ملف PDF: ${error.message ?: "خطأ غير معروف"}",
                        Toast.LENGTH_LONG
                    ).show()
                    onFinished()
                }
            }
        )
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "تعذر تجهيز ملف PDF: ${e.message ?: "خطأ غير معروف"}",
            Toast.LENGTH_LONG
        ).show()
        onFinished()
    }
}
