package com.add.pepers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Keeps the existing SQLite schema intact while isolating the physical database
 * file per authenticated Supabase user.
 *
 * The application still opens add_paper.db, so existing business logic does not
 * need to know about account isolation. Before a user enters the application,
 * this manager swaps add_paper.db with that user's private database copy.
 *
 * The old add_paper.db is never deleted during the first migration. It is copied
 * to the first authenticated user's private database and permanently claimed by
 * that user through a separate binding preference.
 */
internal object LocalDatabaseAccountManager {
    private const val DATABASE_NAME = "add_paper.db"
    private const val BINDING_PREFS = "pepers_database_binding"
    private const val OWNER_USER_ID = "legacy_owner_user_id"
    private const val ACTIVE_USER_ID = "active_user_id"
    private const val USER_DB_PREFIX = "pepers_user_"

    @Synchronized
    fun activateUser(context: Context, userId: String) {
        val cleanUserId = userId.trim()
        if (cleanUserId.isBlank()) return

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(BINDING_PREFS, Context.MODE_PRIVATE)
        val activeUser = prefs.getString(ACTIVE_USER_ID, null).orEmpty()
        if (activeUser == cleanUserId) return

        val activeDb = appContext.getDatabasePath(DATABASE_NAME)
        val previousUser = activeUser.takeIf { it.isNotBlank() }
        if (previousUser != null && activeDb.exists()) {
            checkpointDatabase(activeDb)
            copyDatabase(activeDb, userDatabaseFile(appContext, previousUser))
        }

        val legacyOwner = prefs.getString(OWNER_USER_ID, null).orEmpty()
        if (!activeDb.exists() && legacyOwner.isBlank()) {
            // There is no legacy data to claim. The user's Database instance will
            // create a clean schema when it is first opened.
            prefs.edit().putString(ACTIVE_USER_ID, cleanUserId).apply()
            return
        }

        if (legacyOwner.isBlank() && activeDb.exists()) {
            val privateFile = userDatabaseFile(appContext, cleanUserId)
            checkpointDatabase(activeDb)
            copyDatabase(activeDb, privateFile)
            prefs.edit().putString(OWNER_USER_ID, cleanUserId).apply()
        }

        val privateFile = userDatabaseFile(appContext, cleanUserId)
        replaceActiveDatabase(appContext, privateFile)
        prefs.edit().putString(ACTIVE_USER_ID, cleanUserId).apply()
    }

    @Synchronized
    fun snapshotActiveUser(context: Context, userId: String?) {
        val cleanUserId = userId?.trim().orEmpty()
        if (cleanUserId.isBlank()) return

        val appContext = context.applicationContext
        val activeDb = appContext.getDatabasePath(DATABASE_NAME)
        if (!activeDb.exists()) return

        checkpointDatabase(activeDb)
        copyDatabase(activeDb, userDatabaseFile(appContext, cleanUserId))
    }

    fun activeUserId(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(BINDING_PREFS, Context.MODE_PRIVATE)
            .getString(ACTIVE_USER_ID, null)
            ?.takeIf { it.isNotBlank() }

    fun userDatabasePath(context: Context, userId: String): String =
        userDatabaseFile(context.applicationContext, userId).absolutePath

    private fun userDatabaseFile(context: Context, userId: String): File {
        val safeId = sha256(userId).take(32)
        return File(context.getDatabasePath(DATABASE_NAME).parentFile, "$USER_DB_PREFIX$safeId.db")
    }

    private fun replaceActiveDatabase(context: Context, source: File) {
        val active = context.getDatabasePath(DATABASE_NAME)
        active.parentFile?.mkdirs()

        deleteDatabaseSidecars(active)
        if (source.exists()) {
            copyDatabase(source, active)
        } else {
            // Do not manufacture an empty database here. SQLiteOpenHelper will
            // create the schema safely when Database(context) is opened.
            active.delete()
        }
    }

    private fun checkpointDatabase(file: File) {
        if (!file.exists()) return
        runCatching {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                    while (cursor.moveToNext()) Unit
                }
            }
        }
    }

    private fun copyDatabase(source: File, target: File) {
        if (!source.exists()) return
        target.parentFile?.mkdirs()
        if (source.absoluteFile == target.absoluteFile) return

        deleteDatabaseSidecars(target)
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }

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
        FileInputStream(sourceSidecar).use { input ->
            FileOutputStream(targetSidecar).use { output -> input.copyTo(output) }
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
