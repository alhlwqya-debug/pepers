package com.add.pepers.cloud

import android.content.Context
import android.net.Uri
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
    data class EmailConfirmationRequired(val email: String) : AuthResult()
    data class ConfirmationEmailSent(val email: String) : AuthResult()
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
    fun googleAuthUrl(): String =
        SupabaseConfig.url + "/auth/v1/authorize?provider=google&redirect_to=" +
            Uri.encode(SupabaseConfig.oauthRedirect) + "&flow_type=implicit"

    suspend fun finishGoogleSignIn(callback: Uri): AuthResult = withContext(Dispatchers.IO) {
        val params = callback.fragment.orEmpty().split("&")
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null else part.substring(0, separator) to
                    Uri.decode(part.substring(separator + 1))
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
            if (userId.isBlank()) {
                return@withContext AuthResult.Failure(context.getString(R.string.error_google_sign_in))
            }
            val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L
            val session = AuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                userId = userId,
                label = user.optString("email"),
                expiresAt = System.currentTimeMillis() + expiresIn * 1000L
            )
            saveSession(session)
            AuthResult.SignedIn(session)
        } catch (_: IOException) {
            AuthResult.Failure(context.getString(R.string.error_network))
        } catch (_: Exception) {
            AuthResult.Failure(context.getString(R.string.error_google_sign_in))
        }
    }

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
                if (isEmailNotConfirmed(response.body)) {
                    return@withContext AuthResult.EmailConfirmationRequired(normalizedEmail)
                }
                return@withContext AuthResult.Failure(authError(response.body, response.code, isSignUp))
            }

            val session = sessionFromResponse(response.body, normalizedEmail)
            if (session != null) {
                saveSession(session)
                AuthResult.SignedIn(session)
            } else if (isSignUp) {
                // With Confirm email enabled Supabase creates the user but deliberately
                // omits the session until the address is verified.
                AuthResult.EmailConfirmationRequired(normalizedEmail)
            } else {
                AuthResult.Failure(context.getString(R.string.error_missing_session))
            }
        } catch (_: IOException) {
            AuthResult.Failure(context.getString(R.string.error_network))
        } catch (_: Exception) {
            AuthResult.Failure(context.getString(R.string.error_unknown))
        }
    }

    suspend fun resendConfirmation(email: String): AuthResult = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            return@withContext AuthResult.Failure(context.getString(R.string.error_email_format))
        }

        val payload = JSONObject().apply {
            put("type", "signup")
            put("email", normalizedEmail)
        }

        try {
            val response = postJson("/auth/v1/resend", payload)
            if (response.code !in 200..299) {
                AuthResult.Failure(authError(response.body, response.code, false))
            } else {
                AuthResult.ConfirmationEmailSent(normalizedEmail)
            }
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
        connection.setRequestProperty("Authorization", "Bearer " + accessToken)
        connection.setRequestProperty("Accept", "application/json")
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(responseCode, responseBody)
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

    private fun isEmailNotConfirmed(body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)
        return lower.contains("email_not_confirmed") ||
            lower.contains("email not confirmed") ||
            lower.contains("confirm your email") ||
            lower.contains("email address is not confirmed")
    }

    private fun authError(body: String, code: Int, isSignUp: Boolean): String {
        return try {
            val json = JSONObject(body)
            val errorCode = listOf("error_code", "code", "error")
                .asSequence()
                .map { json.optString(it).lowercase(Locale.ROOT) }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val message = listOf("msg", "message", "error_description")
                .asSequence()
                .map { json.optString(it) }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val lowerMessage = message.lowercase(Locale.ROOT)
            when {
                isEmailNotConfirmed(body) ->
                    context.getString(R.string.error_email_not_confirmed)
                errorCode == "invalid_credentials" || errorCode == "invalid_grant" ||
                    lowerMessage.contains("invalid login credentials") ->
                    context.getString(R.string.error_invalid_credentials)
                isSignUp && (errorCode == "user_already_exists" ||
                    errorCode == "email_exists" ||
                    lowerMessage.contains("already registered") ||
                    lowerMessage.contains("already exists")) ->
                    context.getString(R.string.error_email_already_registered)
                message.isNotBlank() -> message
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
    const val oauthRedirect = "pepers://auth/callback"
}
