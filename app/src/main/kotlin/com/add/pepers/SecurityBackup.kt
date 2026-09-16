package com.add.pepers

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

private const val SECURITY_PREFS = "app_security"
private const val PIN_HASH = "pin_hash"
private const val BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"
private const val BACKUP_PREFS = "backup_meta"
private const val LAST_BACKUP_AT = "last_backup_at"
private const val PIN_KEY_ALIAS = "pepers_app_pin"

internal fun hasAppPin(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE)
    return prefs.getString(PIN_HASH, null).orEmpty().isNotBlank() || prefs.getBoolean(BIOMETRIC_LOCK_ENABLED, false)
}

internal fun isBiometricLockEnabled(context: Context): Boolean = context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE).getBoolean(BIOMETRIC_LOCK_ENABLED, false)

internal fun setBiometricLockEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE).edit().putBoolean(BIOMETRIC_LOCK_ENABLED, enabled).apply()
}

internal fun isDeviceLockAvailable(context: Context): Boolean = try {
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
} catch (_: Exception) { false }

private fun legacyHashPin(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun keystoreKey(): SecretKey? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
    val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(PIN_KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
    generator.init(KeyGenParameterSpec.Builder(PIN_KEY_ALIAS, KeyProperties.PURPOSE_SIGN).setDigests(KeyProperties.DIGEST_SHA256).build())
    return generator.generateKey()
}

private fun hashPin(pin: String): String = try {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(keystoreKey() ?: return legacyHashPin(pin))
    Base64.encodeToString(mac.doFinal(pin.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
} catch (_: Exception) { legacyHashPin(pin) }

internal fun setAppPin(context: Context, pin: String) { context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE).edit().putString(PIN_HASH, hashPin(pin)).apply() }
internal fun clearAppPin(context: Context) { context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE).edit().remove(PIN_HASH).apply() }

internal fun verifyAppPin(context: Context, pin: String): Boolean {
    val stored = context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE).getString(PIN_HASH, "").orEmpty()
    if (stored == hashPin(pin)) return true
    if (stored == legacyHashPin(pin)) { setAppPin(context, pin); return true }
    return false
}

@Composable
internal fun AppLockScreen(context: Context, onUnlocked: () -> Unit, onUseDeviceLock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val biometricEnabled = isBiometricLockEnabled(context)
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.size(12.dp))
            Box(Modifier.size(76.dp).background(Purple.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Security, null, tint = Purple, modifier = Modifier.size(42.dp)) }
            Spacer(Modifier.size(10.dp)); Text("Pepers", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Purple); Text("التطبيق مقفل", fontSize = 14.sp, color = Color.Gray)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (biometricEnabled) "استخدم البصمة أو قفل الجهاز، أو أدخل رمز PIN." else "أدخل رمز PIN لفتح التطبيق.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(Modifier.size(18.dp)); Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) { repeat(6) { index -> PinDot(index < pin.length) } }
            Spacer(Modifier.size(14.dp))
            OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6); error = "" }, label = { Text("رمز PIN") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            if (error.isNotBlank()) { Spacer(Modifier.size(6.dp)); Text(error, color = Color(0xFFC62828), fontSize = 11.sp) }
            Spacer(Modifier.size(12.dp)); Button(onClick = { if (verifyAppPin(context, pin)) onUnlocked() else error = "رمز PIN غير صحيح" }, enabled = pin.length >= 4, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("فتح التطبيق") }
            if (biometricEnabled) {
                Spacer(Modifier.size(18.dp)); Button(onClick = onUseDeviceLock, modifier = Modifier.size(116.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Purple.copy(alpha = 0.12f), contentColor = Purple)) { Icon(Icons.Default.Fingerprint, "البصمة", modifier = Modifier.size(48.dp)) }
                Spacer(Modifier.size(7.dp)); Text("استخدام البصمة أو قفل الجهاز", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("بياناتك محمية على هذا الجهاز", fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
private fun PinDot(filled: Boolean) { Box(Modifier.size(13.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(if (filled) 13.dp else 9.dp).background(if (filled) Purple else Color(0xFFD2CDD7), CircleShape)) } }

@Composable
internal fun SetPinDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var first by remember { mutableStateOf("") }; var second by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تعيين أو تغيير رمز PIN", fontWeight = FontWeight.Bold) }, text = {
        Column {
            Text("استخدم رمزًا من 4 إلى 6 أرقام.", fontSize = 11.sp, color = Color.Gray); Spacer(Modifier.size(8.dp))
            OutlinedTextField(first, { first = it.filter(Char::isDigit).take(6) }, label = { Text("الرمز الجديد") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.size(7.dp))
            OutlinedTextField(second, { second = it.filter(Char::isDigit).take(6) }, label = { Text("تأكيد الرمز") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            if (error.isNotBlank()) { Spacer(Modifier.size(4.dp)); Text(error, color = Color(0xFFC62828), fontSize = 10.sp) }
        }
    }, confirmButton = { Button(onClick = { when { first.length !in 4..6 -> error = "الرمز يجب أن يكون من 4 إلى 6 أرقام"; first != second -> error = "الرمزان غير متطابقين"; else -> onSaved(first) } }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
internal fun SecuritySettingsDialog(context: Context, hasPin: Boolean, onDismiss: () -> Unit, onSetPin: () -> Unit, onRemovePin: () -> Unit, onBackup: () -> Unit, onRestore: () -> Unit) {
    var biometricEnabled by remember { mutableStateOf(isBiometricLockEnabled(context)) }; var protectionEnabled by remember { mutableStateOf(hasPin || biometricEnabled) }; var showBackupDialog by remember { mutableStateOf(false) }; val biometricAvailable = remember { isDeviceLockAvailable(context) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("أمان التطبيق", fontWeight = FontWeight.Bold) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SecuritySwitchRow("قفل التطبيق", "اطلب حماية عند فتح Pepers.", protectionEnabled) { enabled -> protectionEnabled = enabled; if (!enabled) { clearAppPin(context); setBiometricLockEnabled(context, false); biometricEnabled = false } else if (!hasPin && !biometricEnabled) onSetPin() }
            SecuritySwitchRow("البصمة أو قفل الجهاز", if (biometricAvailable) "استخدم مصادقة الجهاز عند توفرها." else "مصادقة الجهاز غير متاحة على هذا الجهاز.", biometricEnabled, biometricAvailable) { enabled -> setBiometricLockEnabled(context, enabled); biometricEnabled = enabled; protectionEnabled = enabled || hasPin }
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5FA))) {
                Column(Modifier.padding(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lock, null, tint = Purple, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text("رمز PIN", fontWeight = FontWeight.Bold) }; Spacer(Modifier.size(4.dp)); Text("غيّر الرمز أو ألغِه من هنا.", fontSize = 10.sp, color = Color.Gray); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { TextButton(onClick = onSetPin) { Text(if (hasPin) "تغيير الرمز" else "تعيين الرمز") }; if (hasPin) TextButton(onClick = onRemovePin) { Text("إلغاء الرمز", color = Color(0xFFC62828)) } } }
            }
            Card(Modifier.fillMaxWidth().clickable { showBackupDialog = true }, RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8F4))) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Backup, null, tint = Green, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text("النسخ الاحتياطي والاستعادة", fontWeight = FontWeight.Bold); Text("حفظ نسخة من بيانات المحل واستعادتها عند الحاجة.", fontSize = 10.sp, color = Color.Gray) } } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } })
    if (showBackupDialog) BackupRestoreDialog(context, { showBackupDialog = false }, onBackup, onRestore)
}

@Composable
private fun SecuritySwitchRow(title: String, description: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Spacer(Modifier.size(2.dp)); Text(description, fontSize = 10.sp, color = Color.Gray) }; Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange) } }

@Composable
private fun BackupRestoreDialog(context: Context, onDismiss: () -> Unit, onBackup: () -> Unit, onRestore: () -> Unit) {
    val lastBackup = remember { context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).getLong(LAST_BACKUP_AT, 0L) }
    val formatted = if (lastBackup > 0L) SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.getDefault()).format(Date(lastBackup)) else "لم يتم إنشاء نسخة احتياطية بعد"
    AlertDialog(onDismissRequest = onDismiss, title = { Text("النسخ الاحتياطي والاستعادة", fontWeight = FontWeight.Bold) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5FA))) { Column(Modifier.padding(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Backup, null, tint = Green, modifier = Modifier.size(25.dp)); Spacer(Modifier.width(8.dp)); Text("نسخة بياناتك", fontWeight = FontWeight.Bold) }; Spacer(Modifier.size(6.dp)); Text("آخر نسخة: $formatted", fontSize = 11.sp, color = Color.Gray); Spacer(Modifier.size(4.dp)); Text("احفظ النسخة في مكان آمن أو انقلها إلى تخزين سحابي حتى تتمكن من استعادة بياناتك عند الحاجة.", fontSize = 10.sp, color = Color.Gray) } }
            Button(onClick = onBackup, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Icon(Icons.Default.Backup, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(7.dp)); Text("إنشاء نسخة احتياطية") }
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple)) { Icon(Icons.Default.Restore, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(7.dp)); Text("استعادة نسخة احتياطية") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } })
}

internal fun exportBackup(context: Context, uri: Uri): Boolean = try {
    val db = context.getDatabasePath("add_paper.db"); if (!db.exists()) return false
    Database(context).apply { optimizeDatabase(); writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }; close() }
    context.contentResolver.openOutputStream(uri)?.use { output -> ZipOutputStream(output).use { zip -> addZipFile(zip, db, "database/add_paper.db"); addOptionalZipFile(zip, File(context.dataDir, "shared_prefs/add_paper_user.xml"), "shared_prefs/add_paper_user.xml"); addOptionalZipFile(zip, File(context.filesDir, "profile_image.jpg"), "files/profile_image.jpg"); zip.putNextEntry(ZipEntry("backup_info.txt")); zip.write("Pepers backup\n${Date()}\n".toByteArray(Charsets.UTF_8)); zip.closeEntry() } } ?: return false
    context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_BACKUP_AT, System.currentTimeMillis()).apply(); true
} catch (_: Exception) { false }

private fun addZipFile(zip: ZipOutputStream, file: File, entryName: String) { zip.putNextEntry(ZipEntry(entryName)); FileInputStream(file).use { it.copyTo(zip) }; zip.closeEntry() }
private fun addOptionalZipFile(zip: ZipOutputStream, file: File, entryName: String) { if (file.exists()) addZipFile(zip, file, entryName) }

internal fun restoreBackup(context: Context, uri: Uri): Boolean = try {
    val tempDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply { mkdirs() }
    context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { zip -> var entry = zip.nextEntry; while (entry != null) { val target = File(tempDir, entry.name); if (!target.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) throw SecurityException("Invalid backup"); if (entry.isDirectory) target.mkdirs() else { target.parentFile?.mkdirs(); FileOutputStream(target).use { output -> zip.copyTo(output) } }; zip.closeEntry(); entry = zip.nextEntry } } } ?: return false
    val dbSource = File(tempDir, "database/add_paper.db"); if (!dbSource.exists()) return false
    val dbTarget = context.getDatabasePath("add_paper.db"); dbTarget.parentFile?.mkdirs(); copyFile(dbSource, dbTarget)
    File(context.dataDir, "databases/add_paper.db-wal").delete(); File(context.dataDir, "databases/add_paper.db-shm").delete()
    val prefsSource = File(tempDir, "shared_prefs/add_paper_user.xml"); if (prefsSource.exists()) copyFile(prefsSource, File(context.dataDir, "shared_prefs/add_paper_user.xml"))
    val imageSource = File(tempDir, "files/profile_image.jpg"); if (imageSource.exists()) copyFile(imageSource, File(context.filesDir, "profile_image.jpg"))
    tempDir.deleteRecursively(); true
} catch (_: Exception) { false }

private fun copyFile(source: File, target: File) { target.parentFile?.mkdirs(); FileInputStream(source).use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } } }
