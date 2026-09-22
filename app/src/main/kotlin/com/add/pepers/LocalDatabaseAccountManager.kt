package com.add.pepers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Keeps the existing SQLite schema intact while isolating local data per
 * authenticated Supabase user.
 *
 * The legacy add_paper.db is preserved. Each authenticated user receives a
 * private SQLite database and a private copy of the profile preferences/image.
 * The existing UI can therefore continue using Database(context) and the
 * existing add_paper_user preferences without exposing another account's data.
 */
internal object LocalDatabaseAccountManager {
    private const val DATABASE_NAME = "add_paper.db"
    private const val BINDING_PREFS = "pepers_database_binding"
    private const val OWNER_USER_ID = "legacy_owner_user_id"
    private const val ACTIVE_USER_ID = "active_user_id"
    private const val USER_DB_PREFIX = "pepers_user_"
    private const val USER_PROFILE_PREFIX = "pepers_profile_"
    private const val USER_IMAGE_DIR = "profile_images"
    private const val PROFILE_PREFS = "add_paper_user"
    private const val PROFILE_IMAGE_NAME = "profile_image.jpg"

    @Synchronized
    fun activateUser(context: Context, userId: String) {
        val cleanUserId = userId.trim()
        if (cleanUserId.isBlank()) return

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(BINDING_PREFS, Context.MODE_PRIVATE)
        val activeUser = prefs.getString(ACTIVE_USER_ID, null).orEmpty()

        if (activeUser != cleanUserId) {
            val activeDb = appContext.getDatabasePath(DATABASE_NAME)
            val previousUser = activeUser.takeIf { it.isNotBlank() }
            if (previousUser != null && activeDb.exists()) {
                checkpointDatabase(activeDb)
                copyDatabase(activeDb, userDatabaseFile(appContext, previousUser))
                snapshotProfile(appContext, previousUser)
            }

            val legacyOwner = prefs.getString(OWNER_USER_ID, null).orEmpty()
            if (legacyOwner.isBlank() && activeDb.exists()) {
                val privateFile = userDatabaseFile(appContext, cleanUserId)
                checkpointDatabase(activeDb)
                copyDatabase(activeDb, privateFile)
                prefs.edit().putString(OWNER_USER_ID, cleanUserId).apply()
            }

            replaceActiveDatabase(appContext, userDatabaseFile(appContext, cleanUserId))
            prefs.edit().putString(ACTIVE_USER_ID, cleanUserId).apply()
        }

        restoreProfile(appContext, cleanUserId)
    }

    @Synchronized
    fun snapshotActiveUser(context: Context, userId: String?) {
        val cleanUserId = userId?.trim().orEmpty()
        if (cleanUserId.isBlank()) return

        val appContext = context.applicationContext
        val activeDb = appContext.getDatabasePath(DATABASE_NAME)
        if (activeDb.exists()) {
            checkpointDatabase(activeDb)
            copyDatabase(activeDb, userDatabaseFile(appContext, cleanUserId))
        }
        snapshotProfile(appContext, cleanUserId)
    }

    /**
     * Clears the shared compatibility profile after its current account has
     * been snapshotted. The per-user profile remains on the device.
     */
    @Synchronized
    fun clearActiveProfile(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        File(appContext.filesDir, PROFILE_IMAGE_NAME).delete()
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

    private fun userProfileFile(context: Context, userId: String): File =
        File(context.filesDir, "$USER_PROFILE_PREFIX${sha256(userId).take(32)}.xml")

    private fun userImageFile(context: Context, userId: String): File =
        File(File(context.filesDir, USER_IMAGE_DIR), "${sha256(userId).take(32)}.jpg")

    private fun snapshotProfile(context: Context, userId: String) {
        val appContext = context.applicationContext
        val source = File(appContext.dataDir, "shared_prefs/$PROFILE_PREFS.xml")
        val target = userProfileFile(appContext, userId)
        if (source.exists()) {
            target.parentFile?.mkdirs()
            copyFile(source, target)
        }

        val currentImage = File(appContext.filesDir, PROFILE_IMAGE_NAME)
        val privateImage = userImageFile(appContext, userId)
        if (currentImage.exists()) {
            privateImage.parentFile?.mkdirs()
            copyFile(currentImage, privateImage)
        } else {
            privateImage.delete()
        }
    }

    private fun restoreProfile(context: Context, userId: String) {
        val appContext = context.applicationContext
        val source = userProfileFile(appContext, userId)
        val target = File(appContext.dataDir, "shared_prefs/$PROFILE_PREFS.xml")
        appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        if (source.exists()) {
            target.parentFile?.mkdirs()
            copyFile(source, target)
        }

        val privateImage = userImageFile(appContext, userId)
        val currentImage = File(appContext.filesDir, PROFILE_IMAGE_NAME)
        if (privateImage.exists()) {
            copyFile(privateImage, currentImage)
        } else {
            currentImage.delete()
        }
    }

    private fun replaceActiveDatabase(context: Context, source: File) {
        val active = context.getDatabasePath(DATABASE_NAME)
        active.parentFile?.mkdirs()
        deleteDatabaseSidecars(active)
        if (source.exists()) copyDatabase(source, active) else active.delete()
    }

    private fun checkpointDatabase(file: File) {
        if (!file.exists()) return
        runCatching {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
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
            FileOutputStream(target).use { output -> input.copyTo(output) }
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
