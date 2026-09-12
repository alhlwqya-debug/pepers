package com.add.pepers.cloud

import android.content.Context
import android.util.Patterns
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
    data object AccountCreated : AuthResult()
    data class SignedIn(val session: AuthSession) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

/**
 * Password authentication is intentionally used here. The previous OTP flow
 * is disabled until email/SMS delivery is configured correctly in Supabase.
 * Only the publishable key is embedded in the Android client.
 */
class SupabaseAuthRepository(private val context: Context) {
    private val preferences =
        context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)

    suspend fun signUpWithPassword(email: String, password: String): AuthResult =
        passwordRequest(
            endpoint = "/auth/v1/signup",
            email = email,
            password = password,
            isSignUp = true
        )

    suspend fun signInWithPassword(email: String, password: String): AuthResult =
        passwordRequest(
            endpoint = "/auth/v1/token?grant_type=password",
            email = email,
            password = password,
            isSignUp = false
        )

    private suspend fun passwordRequest(
        endpoint: String,
        email: String,
        password: String,
        isSignUp: Boolean
    ): AuthResult = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        val validationMessage = validate(normalizedEmail, password)
        if (validationMessage != null) {
            return@withContext AuthResult.Failure(validationMessage)
        }

        val payload = JSONObject().apply {
            put("email", normalizedEmail)
            put("password", password)
        }

        try {
            val response = postJson(endpoint, payload)
            if (response.code !in 200..299) {
                return@withContext AuthResult.Failure(authError(response.body, response.code, isSignUp))
            }

            val session = sessionFromResponse(response.body, normalizedEmail)
            if (session != null) {
                saveSession(session)
                AuthResult.SignedIn(session)
            } else if (isSignUp) {
                AuthResult.AccountCreated
            } else {
                AuthResult.Failure(context.getString(R.string.error_missing_session))
            }
        } catch (_: IOException) {
            AuthResult.Failure(context.getString(R.string.error_network))
        } catch (_: Exception) {
            AuthResult.Failure(context.getString(R.string.error_unknown))
        }
    }

    private fun sessionFromResponse(body: String, label: String): AuthSession? {
        val json = JSONObject(body)
        val accessToken = json.optString("access_token")
        val refreshToken = json.optString("refresh_token")
        val userId = json.optJSONObject("user")?.optString("id").orEmpty()
        if (accessToken.isBlank() || userId.isBlank()) return null
        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            label = label,
            expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
        )
    }

    private fun saveSession(session: AuthSession) {
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
            accessToken = accessToken,
            refreshToken = preferences.getString("refresh_token", "").orEmpty(),
            userId = userId,
            label = preferences.getString("label", "").orEmpty(),
            expiresAt = preferences.getLong("expires_at", 0L)
        )
    }

    fun clearSession() {
        preferences.edit().clear().apply()
    }

    private fun validate(email: String, password: String): String? {
        return when {
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                context.getString(R.string.error_email_format)
            password.length < 6 ->
                context.getString(R.string.error_password_short)
            else -> null
        }
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
        connection.outputStream.use { output ->
            output.write(body.toString().toByteArray(Charsets.UTF_8))
        }
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(responseCode, responseBody)
    }

    private fun authError(body: String, code: Int, isSignUp: Boolean): String {
        return try {
            val json = JSONObject(body)
            val errorCode = json.optString("error_code").lowercase(Locale.ROOT)
            when {
                errorCode == "invalid_credentials" ||
                    json.optString("msg").contains("invalid login credentials", ignoreCase = true) ->
                    context.getString(R.string.error_invalid_credentials)
                errorCode == "email_not_confirmed" ->
                    context.getString(R.string.error_email_not_confirmed)
                isSignUp && (errorCode == "user_already_exists" ||
                    errorCode == "email_exists" ||
                    json.optString("msg").contains("already registered", ignoreCase = true)) ->
                    context.getString(R.string.error_email_already_registered)
                json.optString("msg").isNotBlank() -> json.optString("msg")
                json.optString("message").isNotBlank() -> json.optString("message")
                else -> context.getString(R.string.error_auth_with_code, code)
            }
        } catch (_: Exception) {
            context.getString(R.string.error_unknown)
        }
    }

    private data class HttpResponse(val code: Int, val body: String)
}

private object SupabaseConfig {
    const val url = "https://qieukleyxkvwygzxfgsm.supabase.co"
    const val publishableKey = "sb_publishable_5Y18fNgiYV2tRPyJH5_Ojg_lhf-Ww47"
}
