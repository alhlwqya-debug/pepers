package com.add.pepers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

internal object LocalDatabaseAccountManager {
    private const val DATABASE_NAME = "add_paper.db"
    private const val BINDING_PREFS = "pepers_database_binding"
    private const val ACTIVE_USER_ID = "active_user_id"
    private const val LEGACY_OWNER_USER_ID = "legacy_owner_user_id"
    private const val LEGACY_MIGRATED = "legacy_database_migrated_v2"
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
        val binding = appContext.getSharedPreferences(BINDING_PREFS, Context.MODE_PRIVATE)

        val activeUser = binding.getString(ACTIVE_USER_ID, null)
            ?.trim()?.takeIf { it.isNotBlank() }
        val legacyOwner = binding.getString(LEGACY_OWNER_USER_ID, null)
            ?.trim()?.takeIf { it.isNotBlank() }

        if (activeUser != cleanUserId) {
            if (!activeUser.isNullOrBlank()) snapshotActiveUser(appContext, activeUser)

            val target = userDatabaseFile(appContext, cleanUserId)

            if (!binding.getBoolean(LEGACY_MIGRATED, false) && legacyOwner.isNullOrBlank()) {
                val activeDb = appContext.getDatabasePath(DATABASE_NAME)

                if (activeDb.exists() && hasUsefulDatabaseData(activeDb)) {
                    checkpointDatabase(activeDb)
                    copyDatabase(activeDb, target)
                    binding.edit()
                        .putString(LEGACY_OWNER_USER_ID, cleanUserId)
                        .putBoolean(LEGACY_MIGRATED, true)
                        .apply()
                } else {
                    deleteDatabaseFiles(target)
                    createEmptyDatabase(appContext, target)
                    binding.edit()
                        .putString(LEGACY_OWNER_USER_ID, cleanUserId)
                        .putBoolean(LEGACY_MIGRATED, true)
                        .apply()
                }
            } else if (target.exists()) {
                restoreDatabase(appContext, target)
            } else {
                deleteDatabaseFiles(appContext.getDatabasePath(DATABASE_NAME))
                createEmptyDatabase(appContext, target)
            }

            binding.edit().putString(ACTIVE_USER_ID, cleanUserId).apply()
        }

        restoreProfile(appContext, cleanUserId)
        ensureDatabaseOwnerMarker(appContext, cleanUserId)
    }

    @Synchronized
    fun snapshotActiveUser(context: Context, userId: String?) {
        val cleanUserId = userId?.trim().orEmpty()
        if (cleanUserId.isBlank()) return

        val appContext = context.applicationContext
        val activeUser = activeUserId(appContext)
        if (!activeUser.isNullOrBlank() && activeUser != cleanUserId) return

        val activeDb = appContext.getDatabasePath(DATABASE_NAME)
        if (activeDb.exists()) {
            checkpointDatabase(activeDb)
            val target = userDatabaseFile(appContext, cleanUserId)
            copyDatabase(activeDb, target)
            writeOwnerMarker(appContext, cleanUserId)
        }

        snapshotProfile(appContext, cleanUserId)
    }

    @Synchronized
    fun clearActiveProfile(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        File(appContext.filesDir, PROFILE_IMAGE_NAME).delete()
    }

    fun activeUserId(context: Context): String? =
        context.applicationContext.getSharedPreferences(BINDING_PREFS, Context.MODE_PRIVATE)
            .getString(ACTIVE_USER_ID, null)?.trim()?.takeIf { it.isNotBlank() }

    fun userDatabasePath(context: Context, userId: String): String =
        userDatabaseFile(context.applicationContext, userId).absolutePath

    @Synchronized
    fun ensureBoundUser(context: Context, userId: String): Boolean {
        val cleanUserId = userId.trim()
        if (cleanUserId.isBlank()) return false
        return runCatching {
            val active = activeUserId(context)
            if (active != cleanUserId) activateUser(context, cleanUserId)
            activeUserId(context) == cleanUserId && ownerMatches(context, cleanUserId)
        }.getOrDefault(false)
    }

    private fun ownerMatches(context: Context, userId: String): Boolean =
        readOwnerMarker(context, userId) == userId

    private fun userDatabaseFile(context: Context, userId: String): File {
        val safeId = sha256(userId).take(32)
        return File(
            context.getDatabasePath(DATABASE_NAME).parentFile,
            "$USER_DB_PREFIX$safeId.db"
        )
    }

    private fun ownerMarkerFile(context: Context, userId: String): File =
        File(
            context.filesDir,
            "$USER_DB_PREFIX${sha256(userId).take(32)}.owner"
        )

    private fun writeOwnerMarker(context: Context, userId: String) {
        val marker = ownerMarkerFile(context, userId)
        marker.parentFile?.mkdirs()
        marker.writeText(userId, Charsets.UTF_8)
    }

    private fun readOwnerMarker(context: Context, userId: String): String? {
        val marker = ownerMarkerFile(context, userId)
        if (!marker.exists()) return null
        return runCatching { marker.readText(Charsets.UTF_8).trim() }
            .getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun ensureDatabaseOwnerMarker(context: Context, userId: String) {
        if (readOwnerMarker(context, userId) == null) writeOwnerMarker(context, userId)
    }

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
        } else privateImage.delete()
    }

    private fun restoreProfile(context: Context, userId: String) {
        val appContext = context.applicationContext
        val source = userProfileFile(appContext, userId)
        val target = File(appContext.dataDir, "shared_prefs/$PROFILE_PREFS.xml")

        appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()

        if (source.exists()) {
            target.parentFile?.mkdirs()
            copyFile(source, target)
        }

        val privateImage = userImageFile(appContext, userId)
        val currentImage = File(appContext.filesDir, PROFILE_IMAGE_NAME)
        if (privateImage.exists()) copyFile(privateImage, currentImage)
        else currentImage.delete()
    }

    private fun restoreDatabase(context: Context, source: File) {
        val active = context.getDatabasePath(DATABASE_NAME)
        active.parentFile?.mkdirs()
        deleteDatabaseFiles(active)

        if (source.exists()) copyDatabase(source, active)
        else {
            createEmptyDatabase(context, source)
            copyDatabase(source, active)
        }
    }

    private fun createEmptyDatabase(context: Context, target: File) {
        target.parentFile?.mkdirs()
        val database = Database(context)
        database.close()

        val active = context.getDatabasePath(DATABASE_NAME)
        if (!active.exists()) throw IllegalStateException("تعذر إنشاء قاعدة البيانات المحلية")

        checkpointDatabase(active)
        copyDatabase(active, target)
    }

    private fun checkpointDatabase(file: File) {
        if (!file.exists()) return
        runCatching {
            SQLiteDatabase.openDatabase(
                file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            ).use { db -> db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { } }
        }
    }

    private fun copyDatabase(source: File, target: File) {
        if (!source.exists() || source.absoluteFile == target.absoluteFile) return
        target.parentFile?.mkdirs()
        deleteDatabaseFiles(target)
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

    private fun deleteDatabaseFiles(file: File) {
        file.delete()
        File(file.absolutePath + "-wal").delete()
        File(file.absolutePath + "-shm").delete()
    }

    private fun hasUsefulDatabaseData(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return runCatching {
            SQLiteDatabase.openDatabase(
                file.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM shops", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getLong(0) > 0L
                }
            }
        }.getOrDefault(false)
    }

    private fun userProfileFile(context: Context, userId: String): File =
        File(context.filesDir, "$USER_PROFILE_PREFIX${sha256(userId).take(32)}.xml")

    private fun userImageFile(context: Context, userId: String): File =
        File(File(context.filesDir, USER_IMAGE_DIR), "${sha256(userId).take(32)}.jpg")

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

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
