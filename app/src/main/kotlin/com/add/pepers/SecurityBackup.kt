package com.add.pepers

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val SECURITY_PREFS = "app_security"
private const val PIN_HASH = "pin_hash"

internal fun hasAppPin(context: Context): Boolean =
    context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE).getString(PIN_HASH, null).orEmpty().isNotBlank()

private fun hashPin(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun setAppPin(context: Context, pin: String) {
    context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE).edit().putString(PIN_HASH, hashPin(pin)).apply()
}

internal fun clearAppPin(context: Context) {
    context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE).edit().remove(PIN_HASH).apply()
}

internal fun verifyAppPin(context: Context, pin: String): Boolean =
    hashPin(pin) == context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE).getString(PIN_HASH, "")

@Composable
internal fun AppLockScreen(context: Context, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("🔒 التطبيق مقفل", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("أدخل رمز الحماية للوصول إلى الحسابات والبيانات.", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8); error = "" },
            label = { Text("رمز القفل") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(error, color = Color(0xFFC62828), fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (verifyAppPin(context, pin)) onUnlocked() else error = "رمز القفل غير صحيح"
            },
            enabled = pin.length >= 4,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) { Text("فتح التطبيق") }
    }
}

@Composable
internal fun SetPinDialog(
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعيين رمز حماية") },
        text = {
            Column {
                Text("استخدم رمزًا من 4 إلى 8 أرقام. احفظه في مكان آمن.", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(first, { first = it.filter(Char::isDigit).take(8) }, label = { Text("الرمز") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(second, { second = it.filter(Char::isDigit).take(8) }, label = { Text("تأكيد الرمز") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = Color(0xFFC62828), fontSize = 10.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    first.length < 4 -> error = "الرمز يجب أن يكون 4 أرقام على الأقل"
                    first != second -> error = "الرمزان غير متطابقين"
                    else -> onSaved(first)
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
internal fun SecuritySettingsDialog(
    context: Context,
    hasPin: Boolean,
    onDismiss: () -> Unit,
    onSetPin: () -> Unit,
    onRemovePin: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الحماية والنسخ الاحتياطي", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(if (hasPin) "🔒 حماية التطبيق مفعلة" else "🔓 حماية التطبيق غير مفعلة", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("النسخة الاحتياطية تحمي بياناتك من فقدانها بسبب الأعطال أو الحذف العرضي. وللحماية من حذف التطبيق، انقل النسخة الاحتياطية إلى الهاتف أو التخزين السحابي.", fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Column {
                Button(onClick = onBackup, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("💾 إنشاء نسخة احتياطية") }
                Spacer(Modifier.height(5.dp))
                Button(onClick = onRestore, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("♻ استعادة نسخة احتياطية") }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TextButton(onClick = onSetPin) { Text("🔒 ${if (hasPin) "تغيير" else "تعيين"} القفل") }
                if (hasPin) TextButton(onClick = onRemovePin) { Text("إلغاء القفل", color = Color(0xFFC62828)) }
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    )
}

internal fun exportBackup(context: Context, uri: Uri): Boolean {
    return try {
        val db = File(context.getDatabasePath("add_paper.db").absolutePath)
        if (!db.exists()) return false
        val checkpointDb = Database(context)
        checkpointDb.optimizeDatabase()
        checkpointDb.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        checkpointDb.close()
        val prefs = File(context.dataDir, "shared_prefs/add_paper_user.xml")
        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("database/add_paper.db")); FileInputStream(db).use { it.copyTo(zip) }; zip.closeEntry()
                if (prefs.exists()) { zip.putNextEntry(ZipEntry("shared_prefs/add_paper_user.xml")); FileInputStream(prefs).use { it.copyTo(zip) }; zip.closeEntry() }
                val securityPrefs = File(context.dataDir, "shared_prefs/app_security.xml")
                if (securityPrefs.exists()) { zip.putNextEntry(ZipEntry("shared_prefs/app_security.xml")); FileInputStream(securityPrefs).use { it.copyTo(zip) }; zip.closeEntry() }
                val profileImage = File(context.filesDir, "profile_image.jpg")
                if (profileImage.exists()) { zip.putNextEntry(ZipEntry("files/profile_image.jpg")); FileInputStream(profileImage).use { it.copyTo(zip) }; zip.closeEntry() }
                zip.putNextEntry(ZipEntry("backup_info.txt")); zip.write("Add Paper backup\n${Date()}\n".toByteArray()); zip.closeEntry()
            }
        } ?: return false
        true
    } catch (_: Exception) { false }
}

internal fun restoreBackup(context: Context, uri: Uri): Boolean {
    return try {
        val tempDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply { mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val target = File(tempDir, entry.name)
                    if (!target.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) throw SecurityException("Invalid backup")
                    if (entry.isDirectory) target.mkdirs() else { target.parentFile?.mkdirs(); FileOutputStream(target).use { zip.copyTo(it) } }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
        } ?: return false
        val dbSource = File(tempDir, "database/add_paper.db")
        if (!dbSource.exists()) return false
        val dbTarget = context.getDatabasePath("add_paper.db")
        dbTarget.parentFile?.mkdirs()
        FileInputStream(dbSource).use { input -> FileOutputStream(dbTarget).use { input.copyTo(it) } }
        File(context.dataDir, "databases/add_paper.db-wal").delete()
        File(context.dataDir, "databases/add_paper.db-shm").delete()

        val restoredUserPrefs = File(tempDir, "shared_prefs/add_paper_user.xml")
        if (restoredUserPrefs.exists()) {
            val target = File(context.dataDir, "shared_prefs/add_paper_user.xml")
            target.parentFile?.mkdirs()
            FileInputStream(restoredUserPrefs).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        }
        val restoredSecurityPrefs = File(tempDir, "shared_prefs/app_security.xml")
        if (restoredSecurityPrefs.exists()) {
            val target = File(context.dataDir, "shared_prefs/app_security.xml")
            target.parentFile?.mkdirs()
            FileInputStream(restoredSecurityPrefs).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        }
        val restoredImage = File(tempDir, "files/profile_image.jpg")
        if (restoredImage.exists()) {
            val target = File(context.filesDir, "profile_image.jpg")
            FileInputStream(restoredImage).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        }
        true
    } catch (_: Exception) { false }
}

internal fun createInternalAutoBackup(context: Context) {
    try {
        val dbHelper = Database(context)
        dbHelper.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        dbHelper.close()
        val dbFile = context.getDatabasePath("add_paper.db")
        if (!dbFile.exists()) return
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val out = File(dir, "backup_$stamp.zip")
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry("database/add_paper.db")); FileInputStream(dbFile).use { it.copyTo(zip) }; zip.closeEntry()
        }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(7)?.forEach { it.delete() }
    } catch (_: Exception) { }
}
