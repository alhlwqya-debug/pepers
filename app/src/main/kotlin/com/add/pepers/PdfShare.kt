package com.add.pepers

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast

/**
 * Uses Android's real WebView/Chromium Print Framework.
 * No manual PdfDocument, Canvas drawing, screenshots or custom pagination.
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
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: throw IllegalStateException("خدمة الطباعة غير متوفرة على هذا الجهاز")

        val mediaSize = if (jobName.contains("فردي")) {
            PrintAttributes.MediaSize.ISO_A4.asLandscape()
        } else {
            PrintAttributes.MediaSize.ISO_A4
        }

        // Use the exact Chromium adapter used by the normal Print button.
        val adapter: PrintDocumentAdapter =
            webView.createPrintDocumentAdapter(jobName)

        printManager.print(
            jobName,
            adapter,
            PrintAttributes.Builder()
                .setMediaSize(mediaSize)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()
        )

        Toast.makeText(
            context,
            "تم فتح الطباعة الحقيقية للتقرير الكامل. اختر «حفظ كملف PDF» أو الطابعة المطلوبة.",
            Toast.LENGTH_LONG
        ).show()
        onFinished()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "تعذر فتح الطباعة/PDF: " + (e.message ?: "خطأ غير معروف"),
            Toast.LENGTH_LONG
        ).show()
        onFinished()
    }
}
