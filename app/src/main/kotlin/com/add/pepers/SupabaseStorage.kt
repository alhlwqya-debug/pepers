package com.add.pepers

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal object SupabaseStorage {
    private const val BUCKET = "profile-images"

    fun profileObjectPath(userId: String): String = "$userId/profile.jpg"

    fun uploadProfileImage(context: Context, session: SupabaseSession): String? {
        return try {
            val localPath = context.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE)
                .getString("user_image", "").orEmpty()
            val file = File(localPath)
            if (!file.exists() || !file.isFile) return null
            val objectPath = profileObjectPath(session.userId)
            val connection = open("/storage/v1/object/$BUCKET/$objectPath", "PUT", session)
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "image/jpeg")
            connection.setRequestProperty("x-upsert", "true")
            file.inputStream().use { input -> connection.outputStream.use { output -> input.copyTo(output) } }
            val status = connection.responseCode
            connection.disconnect()
            if (status in 200..299) objectPath else null
        } catch (_: Exception) {
            null
        }
    }

    fun downloadProfileImage(context: Context, session: SupabaseSession, avatarPath: String) {
        val objectPath = avatarPath.removePrefix("$BUCKET/")
        if (objectPath.isBlank() || objectPath.startsWith("/")) return
        try {
            val connection = open("/storage/v1/object/authenticated/$BUCKET/$objectPath", "GET", session)
            if (connection.responseCode !in 200..299) { connection.disconnect(); return }
            val target = File(context.filesDir, "profile_image.jpg")
            val temporary = File(context.filesDir, "profile_image.jpg.tmp")
            connection.inputStream.use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
            connection.disconnect()
            if (temporary.length() > 0L) temporary.renameTo(target) else temporary.delete()
        } catch (_: Exception) { }
    }

    private fun open(path: String, method: String, session: SupabaseSession): HttpURLConnection {
        return (URL(SupabaseConfig.URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer " + session.accessToken)
            setRequestProperty("Accept", "application/json")
        }
    }
}
