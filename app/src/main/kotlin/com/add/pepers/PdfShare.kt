package com.add.pepers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PdfPrint
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creates a real PDF using Android WebView/Chromium printing and shares that file
 * through the normal Android share sheet. The normal PrintManager path in PdfUtils.kt
 * remains unchanged.
 */
internal fun shareWebViewAsPdf(
    context: Context,
    webView: WebView,
    jobName: String,
    phone: String,
    email: String,
    onFinished: () -> Unit
) {
    try {
        val documentsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val reportDir = File(documentsDir, "Pepers/reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = jobName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
        val pdfFile = File(reportDir, "${safeName}_$stamp.pdf")

        val attributesBuilder = PrintAttributes.Builder()
            .setMediaSize(if (jobName.contains("فردي"))
                PrintAttributes.MediaSize.ISO_A4.asLandscape()
            else PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)

        val adapter = webView.createPrintDocumentAdapter(jobName)
        PdfPrint(attributesBuilder.build()).print(adapter, pdfFile, object : PdfPrint.Callback {
            override fun onSuccess(file: File) {
                try {
                    val authority = context.packageName + ".fileprovider"
                    val uri: Uri = FileProvider.getUriForFile(context, authority, file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, jobName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val chooser = Intent.createChooser(shareIntent, "مشاركة تقرير PDF").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                    Toast.makeText(context, "تم إنشاء ملف PDF وفتحت قائمة المشاركة.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "تم إنشاء PDF لكن تعذرت مشاركته: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                } finally {
                    onFinished()
                }
            }

            override fun onFailure(error: Exception) {
                Toast.makeText(context, "تعذر إنشاء ملف PDF: ${error.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                onFinished()
            }
        })
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر تجهيز ملف PDF: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
        onFinished()
    }
}