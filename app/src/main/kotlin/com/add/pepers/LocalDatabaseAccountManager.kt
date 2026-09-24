package com.add.pepers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object LocalDatabaseAccountManager {
    private const val DATABASE_NAME = "add_paper.db"
    private const val BINDING_PREFS = "pepers_database_binding"
    private const val ACTIVE_USER_ID = "active_user_id"
    private const val PROFILE_PREFS = "add_paper_user"
    private const val PROFILE_IMAGE_NAME = "profile_image.jpg"
    private const val USER_DB_PREFIX = "pepers_user_"
    private const val USER_PROFILE_PREFIX = "pepers_profile_"
    private const val USER_IMAGE_DIR = "profile_images"
    private const val QUARANTINE_DIR = "database_quarantine"
    private const val BINDING_TABLE = "local_account_binding"

    @Synchronized
    fun activateUser(context: Context, userId: String) {
        val cleanUserId = userId.trim()
        if (cleanUserId.isBlank()) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(BINDING_PREFS, Context.MODE_PRIVATE)
        val activeUser = prefs.getString(ACTIVE_USER_ID, null).orEmpty()
        val activeDb = app.getDatabasePath(DATABASE_NAME)
        val privateDb = userDatabaseFile(app, cleanUserId)

        if (activeUser.isNotBlank() && activeUser != cleanUserId && activeDb.exists()) {
            if (isBoundToUser(activeDb, activeUser)) {
                checkpointDatabase(activeDb)
                copyDatabase(activeDb, userDatabaseFile(app, activeUser))
                bindDatabase(userDatabaseFile(app, activeUser), activeUser)
                snapshotProfile(app, activeUser)
            } else {
                quarantineDatabase(app, activeDb, "unbound_active")
            }
        }

        if (privateDb.exists() && !isBoundToUser(privateDb, cleanUserId)) {
            quarantineDatabase(app, privateDb, "unbound_user")
        }

        if (activeUser != cleanUserId || !isBoundToUser(activeDb, cleanUserId)) {
            if (activeDb.exists() && !isBoundToUser(activeDb, cleanUserId)) {
                quarantineDatabase(app, activeDb, "wrong_account")
            }
            replaceActiveDatabase(app, privateDb)
            bindDatabase(activeDb, cleanUserId)
            prefs.edit().putString(ACTIVE_USER_ID, cleanUserId).apply()
        } else {
            bindDatabase(activeDb, cleanUserId)
        }

        restoreProfile(app, cleanUserId)
    }

    @Synchronized
    fun ensureBoundUser(context: Context, userId: String): Boolean {
        val cleanUserId = userId.trim()
        if (cleanUserId.isBlank()) return false
        activateUser(context, cleanUserId)
        return isBoundToUser(
            context.applicationContext.getDatabasePath(DATABASE_NAME),
            cleanUserId
        )
    }

    @Synchronized
    fun snapshotActiveUser(context: Context, userId: String?) {
        val cleanUserId = userId?.trim().orEmpty()
        if (cleanUserId.isBlank()) return
        val app = context.applicationContext
        val activeDb = app.getDatabasePath(DATABASE_NAME)
        if (!activeDb.exists() || !isBoundToUser(activeDb, cleanUserId)) return
        checkpointDatabase(activeDb)
        copyDatabase(activeDb, userDatabaseFile(app, cleanUserId))
        bindDatabase(userDatabaseFile(app, cleanUserId), cleanUserId)
        snapshotProfile(app, cleanUserId)
    }

    fun activeUserId(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(BINDING_PREFS, Context.MODE_PRIVATE)
            .getString(ACTIVE_USER_ID, null)
            ?.takeIf { it.isNotBlank() }

    fun userDatabasePath(context: Context, userId: String): String =
        userDatabaseFile(context.applicationContext, userId).absolutePath

    @Synchronized
    fun clearActiveProfile(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        File(app.filesDir, PROFILE_IMAGE_NAME).delete()
    }

    private fun userDatabaseFile(context: Context, userId: String): File {
        val safeId = sha256(userId).take(32)
        return File(
            context.getDatabasePath(DATABASE_NAME).parentFile,
            "$USER_DB_PREFIX$safeId.db"
        )
    }

    private fun userProfileFile(context: Context, userId: String): File =
        File(context.filesDir, "$USER_PROFILE_PREFIX${sha256(userId).take(32)}.xml")

    private fun userImageFile(context: Context, userId: String): File =
        File(
            File(context.filesDir, USER_IMAGE_DIR),
            "${sha256(userId).take(32)}.jpg"
        )

    private fun bindDatabase(file: File, userId: String) {
        file.parentFile?.mkdirs()
        runCatching {
            SQLiteDatabase.openDatabase(
                file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
            ).use { db ->
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS $BINDING_TABLE (" +
                        "id INTEGER PRIMARY KEY CHECK(id = 1)," +
                        "user_id TEXT NOT NULL," +
                        "bound_at INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO $BINDING_TABLE(id,user_id,bound_at) VALUES(1,?,?)",
                    arrayOf(userId, System.currentTimeMillis())
                )
            }
        }
    }

    private fun isBoundToUser(file: File, userId: String): Boolean {
        if (!file.exists()) return false
        return runCatching {
            SQLiteDatabase.openDatabase(
                file.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                var bound = false
                db.rawQuery(
                    "SELECT user_id FROM $BINDING_TABLE WHERE id = 1 LIMIT 1", null
                ).use { cursor ->
                    if (cursor.moveToFirst()) bound = cursor.getString(0) == userId
                }
                bound
            }
        }.getOrDefault(false)
    }

    private fun snapshotProfile(context: Context, userId: String) {
        val app = context.applicationContext
        val source = File(app.dataDir, "shared_prefs/$PROFILE_PREFS.xml")
        val target = userProfileFile(app, userId)
        if (source.exists()) {
            target.parentFile?.mkdirs()
            copyFile(source, target)
        }
        val currentImage = File(app.filesDir, PROFILE_IMAGE_NAME)
        val privateImage = userImageFile(app, userId)
        if (currentImage.exists()) {
            privateImage.parentFile?.mkdirs()
            copyFile(currentImage, privateImage)
        }
    }

    private fun restoreProfile(context: Context, userId: String) {
        val app = context.applicationContext
        val source = userProfileFile(app, userId)
        val target = File(app.dataDir, "shared_prefs/$PROFILE_PREFS.xml")
        app.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        if (source.exists()) {
            target.parentFile?.mkdirs()
            copyFile(source, target)
        }
        val privateImage = userImageFile(app, userId)
        val currentImage = File(app.filesDir, PROFILE_IMAGE_NAME)
        if (privateImage.exists()) copyFile(privateImage, currentImage)
        else currentImage.delete()
    }

    private fun replaceActiveDatabase(context: Context, source: File) {
        val active = context.getDatabasePath(DATABASE_NAME)
        active.parentFile?.mkdirs()
        deleteDatabaseSidecars(active)
        if (source.exists()) copyDatabase(source, active) else active.delete()
    }

    private fun quarantineDatabase(context: Context, source: File, reason: String) {
        if (!source.exists()) return
        val root = File(context.filesDir, QUARANTINE_DIR)
        root.mkdirs()
        val stamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss_SSS", Locale.US
        ).format(Date())
        val target = File(
            root,
            "${source.nameWithoutExtension}_${reason}_${stamp}.db"
        )
        runCatching {
            checkpointDatabase(source)
            copyDatabase(source, target)
        }
        deleteDatabaseSidecars(source)
    }

    private fun checkpointDatabase(file: File) {
        if (!file.exists()) return
        runCatching {
            SQLiteDatabase.openDatabase(
                file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { }
            }
        }
    }

    private fun copyDatabase(source: File, target: File) {
        if (!source.exists()) return
        target.parentFile?.mkdirs()
        if (source.absoluteFile == target.absoluteFile) return
        deleteDatabaseSidecars(target)
        copyFile(source, target)
        copySidecar(source, target, "-wal")
        copySidecar(source, target, "-shm")
    }

    private fun copySidecar(source: File, target: File, suffix: String) {
        val sourceSidecar = File(source.absolutePath + suffix)
        val targetSidecar = File(target.absolutePath + suffix)
        if (!sourceSidecar.exists()) {
            targetSidecar.delete()
            return
        }
        copyFile(sourceSidecar, targetSidecar)
    }

    private fun copyFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun deleteDatabaseSidecars(file: File) {
        file.delete()
        File(file.absolutePath + "-wal").delete()
        File(file.absolutePath + "-shm").delete()
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
