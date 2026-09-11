package com.add.pepers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// استيراد الأيقونات
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person

// استيراد Coil لعرض الصور

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


// ================ الألوان ================
val Yellow = Color(0xFFFDE48B)
val LightBlue = Color(0xFFC9D4EA)
val HeaderBlue = Color(0xFFD8E1F3)
val CardWorkBg = Color(0xFFE8F5E9)
val CardExpBg = Color(0xFFFFEBEE)
val CardNetBg = Color(0xFFE3F2FD)

// ألوان الواجهة الموحدة: اللون الأساسي للحركة، والسطوح الهادئة للمحتوى.
val Purple = Color(0xFF6A4C93)
val Green = Color(0xFF2E7D32)
val Blue = Color(0xFF1565C0)
val Red = Color(0xFFC62828)
val Orange = Color(0xFFEF6C00)
val AppBackground = Color(0xFFF7F5FA)
val AppSurface = Color.White
val AppSurfaceAlt = Color(0xFFFAF9FC)
val AppPrimarySoft = Color(0xFFEDE5F6)
val AppBorder = Color(0xFFE2DCE8)
val AppText = Color(0xFF302A36)
val AppMuted = Color(0xFF746D7B)
val AppButtonShape = RoundedCornerShape(12.dp)
val PageBg = AppBackground

// ================ الأبعاد ================
internal val wDay = 72.dp
internal val wDate = 92.dp
internal val wPiece = 76.dp
internal val wExpense = 84.dp
internal val wTotal = 94.dp
internal val rowH = 42.dp

// ================ دوال مساعدة ================
internal fun monthName(month: Int): String = when (month) {
    1 -> "يناير"
    2 -> "فبراير"
    3 -> "مارس"
    4 -> "أبريل"
    5 -> "مايو"
    6 -> "يونيو"
    7 -> "يوليو"
    8 -> "أغسطس"
    9 -> "سبتمبر"
    10 -> "أكتوبر"
    11 -> "نوفمبر"
    12 -> "ديسمبر"
    else -> "شهر"
}

internal fun todayString(): String =
SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Date())

internal fun escapeHtml(value: String): String = value
.replace("&", "&amp;")
.replace("<", "&lt;")
.replace(">", "&gt;")
.replace("\"", "&quot;")
.replace("'", "&#39;")

// دالة لحفظ الصورة في التخزين الداخلي
internal fun saveImageToInternalStorage(context: Context, uri: Uri): File {
    val inputStream = context.contentResolver.openInputStream(uri)!!
    val imageFile = File(context.filesDir, "profile_image.jpg")
    val outputStream = FileOutputStream(imageFile)

    inputStream.copyTo(outputStream)
    inputStream.close()
    outputStream.close()

    return imageFile
}

// ================ المكونات الأساسية ================

@Composable
internal fun CellText(
    text: String,
    width: Dp,
    height: Dp,
    bg: Color = Color.White,
    size: Int = 11,
    bold: Boolean = false,
    color: Color = Color.Black
) {
    Box(
        modifier = Modifier
        .width(width)
        .height(height)
        .border(1.dp, Color.Black)
        .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = color
        )
    }
}

@Composable
internal fun CellEdit(
    value: String,
    onValueChange: (String) -> Unit,
    width: Dp,
    height: Dp,
    bg: Color = Color.White,
    number: Boolean = false
) {
    Box(
        modifier = Modifier
        .width(width)
        .height(height)
        .border(1.dp, Color.Black)
        .background(bg)
        .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = if (number) {
                KeyboardOptions(keyboardType = KeyboardType.Number)
            } else {
                KeyboardOptions.Default
            },
            textStyle = TextStyle(
                fontSize = 11.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun SummaryCard(
    title: String,
    value: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
        .clip(RoundedCornerShape(10.dp))
        .background(bgColor)
        .border(1.dp, AppBorder, RoundedCornerShape(10.dp))
        .padding(horizontal = 5.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppText)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Purple,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

