package com.add.pepers.cloud

import android.content.Context
import android.net.Uri
import android.util.Patterns
import com.add.pepers.LocalDatabaseAccountManager
import com.add.pepers.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val label: String,
    val expiresAt: Long = 0L
)

sealed class AuthResult {
    data class SignedIn(val session: AuthSession) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

/** Password authentication, Supabase OAuth, and persistent session management. */
class SupabaseAuthRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)

    suspend fun signUpWithPassword(name: String, phone: String, email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val normalizedName = name.trim().replace(Regex("\\s+"), " ")
            val normalizedPhone = phone.trim().replace(" ", "")
            val normalizedEmail = email.trim().lowercase(Locale.ROOT)
            validateRegistration(normalizedName, normalizedPhone, normalizedEmail, password)?.let {
                return@withContext AuthResult.Failure(it)
            }
            val payload = JSONObject().apply {
                put("email", normalizedEmail)
                put("password", password)
                put("data", JSONObject().apply {
                    put("full_name", normalizedName)
                    put("name", normalizedName)
                    put("phone", normalizedPhone)
                })
            }
            try {
                val response = postJson("/auth/v1/signup", payload)
                if (response.code !in 200..299) {
                    return@withContext AuthResult.Failure(authError(response.body, response.code, true))
                }
                val session = sessionFromResponse(response.body, normalizedEmail)
                    ?: return@withContext AuthResult.Failure(context.getString(R.string.error_email_confirmation_disabled))
                saveProfile(normalizedName, normalizedPhone, normalizedEmail)
                saveSession(session)
                AuthResult.SignedIn(session)
            } catch (_: IOException) {
                AuthResult.Failure(context.getString(R.string.error_network))
            } catch (_: Exception) {
                AuthResult.Failure(context.getString(R.string.error_unknown))
            }
        }

    suspend fun signInWithPassword(email: String, password: String): AuthResult =
        passwordRequest("/auth/v1/token?grant_type=password", email, password)

    /**
     * Restores the local session when the app starts. If the access token is
     * still valid it is reused; otherwise the refresh token is exchanged for
     * a new access token. The local session is cleared only when it can no
     * longer be restored, never merely because the device is offline.
     */
    suspend fun restoreSession(): AuthResult? = withContext(Dispatchers.IO) {
        val stored = savedSession() ?: return@withContext null
        LocalDatabaseAccountManager.activateUser(context, stored.userId)
        val now = System.currentTimeMillis()
        val refreshWindowMs = 60_000L
        if (stored.expiresAt > now + refreshWindowMs) {
            return@withContext AuthResult.SignedIn(stored)
        }
        if (stored.refreshToken.isBlank()) {
            return@withContext AuthResult.SignedIn(stored)
        }
        try {
            val payload = JSONObject().apply {
                put("refresh_token", stored.refreshToken)
            }
            val response = postJson("/auth/v1/token?grant_type=refresh_token", payload)
            if (response.code !in 200..299) {
                if (response.code in 400..499) clearSession()
                return@withContext AuthResult.SignedIn(stored)
            }
            val refreshed = sessionFromResponse(response.body, stored.label)
                ?: return@withContext AuthResult.SignedIn(stored)
            val session = refreshed.copy(
                refreshToken = refreshed.refreshToken.ifBlank { stored.refreshToken },
                label = stored.label.ifBlank { refreshed.label }
            )
            saveSession(session)
            AuthResult.SignedIn(session)
        } catch (_: IOException) {
            AuthResult.SignedIn(stored)
        } catch (_: Exception) {
            AuthResult.SignedIn(stored)
        }
    }

    fun googleAuthUrl(): String =
        SupabaseConfig.url + "/auth/v1/authorize?provider=google&redirect_to=" +
            Uri.encode(SupabaseConfig.oauthRedirect) + "&flow_type=implicit"

    suspend fun finishGoogleSignIn(callback: Uri): AuthResult = withContext(Dispatchers.IO) {
        val params = callback.fragment.orEmpty().split("&").mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else part.substring(0, separator) to Uri.decode(part.substring(separator + 1))
        }.toMap()
        if (!params["error_description"].isNullOrBlank() || !params["error"].isNullOrBlank()) {
            return@withContext AuthResult.Failure(context.getString(R.string.error_google_sign_in))
        }
        val accessToken = params["access_token"].orEmpty()
        val refreshToken = params["refresh_token"].orEmpty()
        if (accessToken.isBlank() || refreshToken.isBlank()) {
            return@withContext AuthResult.Failure(context.getString(R.string.error_google_sign_in))
        }
        try {
            val userResponse = getJson("/auth/v1/user", accessToken)
            if (userResponse.code !in 200..299) {
                return@withContext AuthResult.Failure(authError(userResponse.body, userResponse.code, false))
            }
            val user = JSONObject(userResponse.body)
            val userId = user.optString("id")
            if (userId.isBlank()) return@withContext AuthResult.Failure(context.getString(R.string.error_google_sign_in))
            val metadata = user.optJSONObject("user_metadata")
            val email = user.optString("email")
            val name = metadata?.optString("full_name")?.takeIf { it.isNotBlank() }
                ?: metadata?.optString("name")?.takeIf { it.isNotBlank() }
                ?: email.substringBefore('@')
            val phone = metadata?.optString("phone").orEmpty()
            val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L
            val session = AuthSession(accessToken, refreshToken, userId, email, System.currentTimeMillis() + expiresIn * 1000L)
            saveProfile(name, phone, email)
            saveSession(session)
            AuthResult.SignedIn(session)
        } catch (_: IOException) {
            AuthResult.Failure(context.getString(R.string.error_network))
        } catch (_: Exception) {
            AuthResult.Failure(context.getString(R.string.error_google_sign_in))
        }
    }

    private suspend fun passwordRequest(endpoint: String, email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        validate(normalizedEmail, password)?.let { return@withContext AuthResult.Failure(it) }
        val payload = JSONObject().apply {
            put("email", normalizedEmail)
            put("password", password)
        }
        try {
            val response = postJson(endpoint, payload)
            if (response.code !in 200..299) return@withContext AuthResult.Failure(authError(response.body, response.code, false))
            val session = sessionFromResponse(response.body, normalizedEmail)
                ?: return@withContext AuthResult.Failure(context.getString(R.string.error_missing_session))
            val user = runCatching { JSONObject(response.body).optJSONObject("user") }.getOrNull()
            val metadata = user?.optJSONObject("user_metadata")
            val name = metadata?.optString("full_name")?.takeIf { it.isNotBlank() } ?: metadata?.optString("name")?.takeIf { it.isNotBlank() } ?: readProfile("user_name").orEmpty()
            val phone = metadata?.optString("phone")?.takeIf { it.isNotBlank() } ?: readProfile("user_phone").orEmpty()
            saveProfile(name, phone, normalizedEmail)
            saveSession(session)
            AuthResult.SignedIn(session)
        } catch (_: IOException) {
            AuthResult.Failure(context.getString(R.string.error_network))
        } catch (_: Exception) {
            AuthResult.Failure(context.getString(R.string.error_unknown))
        }
    }

    private fun getJson(endpoint: String, accessToken: String): HttpResponse {
        val connection = (URL(SupabaseConfig.url + endpoint).openConnection() as HttpURLConnection)
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("apikey", SupabaseConfig.publishableKey)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Accept", "application/json")
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(code, body)
    }

    private fun sessionFromResponse(body: String, label: String): AuthSession? {
        val json = JSONObject(body)
        val accessToken = json.optString("access_token")
        val refreshToken = json.optString("refresh_token")
        val userId = json.optJSONObject("user")?.optString("id").orEmpty()
        if (accessToken.isBlank() || userId.isBlank()) return null
        return AuthSession(accessToken, refreshToken, userId, label, System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
    }

    private fun saveSession(session: AuthSession) {
        LocalDatabaseAccountManager.activateUser(context, session.userId)
        preferences.edit()
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putString("user_id", session.userId)
            .putString("label", session.label)
            .putLong("expires_at", session.expiresAt)
            .apply()
    }

    fun savedSession(): AuthSession? {
        val accessToken = preferences.getString("access_token", null) ?: return null
        val userId = preferences.getString("user_id", null) ?: return null
        return AuthSession(
            accessToken,
            preferences.getString("refresh_token", "").orEmpty(),
            userId,
            preferences.getString("label", "").orEmpty(),
            preferences.getLong("expires_at", 0L)
        )
    }

    fun clearSession() {
        val currentUserId = preferences.getString("user_id", null)
        LocalDatabaseAccountManager.snapshotActiveUser(context, currentUserId)
        preferences.edit().clear().apply()
    }

    private fun saveProfile(name: String, phone: String, email: String) {
        context.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE).edit()
            .putString("user_name", name)
            .putString("user_phone", phone)
            .putString("user_email", email)
            .apply()
    }

    private fun readProfile(key: String): String? =
        context.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE).getString(key, null)

    private fun validateRegistration(name: String, phone: String, email: String, password: String): String? = when {
        name.length < 2 -> context.getString(R.string.error_name_required)
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> context.getString(R.string.error_email_format)
        phone.length !in 7..15 || phone.any { !it.isDigit() } -> "أدخل رقم جوال صحيحًا من 7 إلى 15 رقمًا."
        password.length < 6 -> context.getString(R.string.error_password_short)
        else -> null
    }

    private fun validate(email: String, password: String): String? = when {
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> context.getString(R.string.error_email_format)
        password.length < 6 -> context.getString(R.string.error_password_short)
        else -> null
    }

    private fun postJson(endpoint: String, body: JSONObject): HttpResponse {
        val connection = (URL("${SupabaseConfig.url}$endpoint").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.doOutput = true
        connection.setRequestProperty("apikey", SupabaseConfig.publishableKey)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(code, responseBody)
    }

    private fun authError(body: String, code: Int, isSignUp: Boolean): String = try {
        val json = JSONObject(body)
        val errorCode = listOf("error_code", "code", "error").asSequence()
            .map { json.optString(it).lowercase(Locale.ROOT) }.firstOrNull { it.isNotBlank() }.orEmpty()
        val message = listOf("msg", "message", "error_description").asSequence()
            .map { json.optString(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
        val lower = message.lowercase(Locale.ROOT)
        when {
            errorCode == "invalid_credentials" || errorCode == "invalid_grant" || lower.contains("invalid login credentials") -> context.getString(R.string.error_invalid_credentials)
            isSignUp && (errorCode == "user_already_exists" || errorCode == "email_exists" || lower.contains("already registered") || lower.contains("already exists")) -> context.getString(R.string.error_email_already_registered)
            message.isNotBlank() -> message
            else -> context.getString(R.string.error_auth_with_code, code)
        }
    } catch (_: Exception) {
        context.getString(R.string.error_unknown)
    }

    private data class HttpResponse(val code: Int, val body: String)
}

private object SupabaseConfig {
    const val url = "https://qieukleyxkvwygzxfgsm.supabase.co"
    const val publishableKey = "sb_publishable_5Y18fNgiYV2tRPyJH5_Ojg_lhf-Ww47"
    const val oauthRedirect = "pepers://auth/callback"
}
