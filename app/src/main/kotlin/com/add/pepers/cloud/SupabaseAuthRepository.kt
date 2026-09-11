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

enum class AuthMethod {
    EMAIL,
    PHONE
}

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val label: String
)

sealed class AuthResult {
    data object CodeSent : AuthResult()
    data class SignedIn(val session: AuthSession) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

/**
 * Supabase Auth is deliberately called through its public REST endpoints here.
 * Only the publishable key is embedded in the Android client; never put a
 * service_role key in this file or in an Android build.
 */
class SupabaseAuthRepository(private val context: Context) {
    private val preferences =
        context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)

    suspend fun sendCode(
        identifier: String,
        method: AuthMethod,
        createAccount: Boolean
    ): AuthResult = withContext(Dispatchers.IO) {
        val normalized = normalize(identifier, method)
        val validationMessage = validate(normalized, method)
        if (validationMessage != null) {
            return@withContext AuthResult.Failure(validationMessage)
        }

        val payload = JSONObject().apply {
            if (method == AuthMethod.EMAIL) put("email", normalized)
            else put("phone", normalized)
            put("create_user", createAccount)
        }

        try {
            val response = postJson(
                endpoint = "/auth/v1/otp",
                body = payload,
                accessToken = null
            )
            if (response.code in 200..299) {
                AuthResult.CodeSent
            } else {
                AuthResult.Failure(authError(response.body, response.code, method))
            }
        } catch (_: IOException) {
            AuthResult.Failure(context.getString(R.string.error_network))
        } catch (_: Exception) {
            AuthResult.Failure(context.getString(R.string.error_unknown))
        }
    }

    suspend fun verifyCode(
        identifier: String,
        method: AuthMethod,
        code: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val normalized = normalize(identifier, method)
        if (!code.matches(Regex("\\d{6}"))) {
            return@withContext AuthResult.Failure(context.getString(R.string.error_code_format))
        }

        val payload = JSONObject().apply {
            put("type", if (method == AuthMethod.EMAIL) "email" else "sms")
            put("token", code)
            if (method == AuthMethod.EMAIL) put("email", normalized)
            else put("phone", normalized)
        }

        try {
            val response = postJson(
                endpoint = "/auth/v1/verify",
                body = payload,
                accessToken = null
            )
            if (response.code !in 200..299) {
                return@withContext AuthResult.Failure(authError(response.body, response.code, method))
            }

            val json = JSONObject(response.body)
            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token")
            val user = json.optJSONObject("user")
            val userId = user?.optString("id").orEmpty()
            if (accessToken.isBlank() || userId.isBlank()) {
                return@withContext AuthResult.Failure(context.getString(R.string.error_missing_session))
            }

            val session = AuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                userId = userId,
                label = normalized
            )
            preferences.edit()
                .putString("access_token", accessToken)
                .putString("refresh_token", refreshToken)
                .putString("user_id", userId)
                .putString("label", normalized)
                .apply()
            AuthResult.SignedIn(session)
        } catch (_: IOException) {
            AuthResult.Failure(context.getString(R.string.error_network))
        } catch (_: Exception) {
            AuthResult.Failure(context.getString(R.string.error_unknown))
        }
    }

    fun savedSession(): AuthSession? {
        val accessToken = preferences.getString("access_token", null) ?: return null
        val userId = preferences.getString("user_id", null) ?: return null
        return AuthSession(
            accessToken = accessToken,
            refreshToken = preferences.getString("refresh_token", "").orEmpty(),
            userId = userId,
            label = preferences.getString("label", "").orEmpty()
        )
    }

    fun clearSession() {
        preferences.edit().clear().apply()
    }

    private fun normalize(identifier: String, method: AuthMethod): String {
        val trimmed = identifier.trim()
        return if (method == AuthMethod.PHONE) {
            trimmed.replace(" ", "").replace("-", "")
        } else {
            trimmed.lowercase(Locale.ROOT)
        }
    }

    private fun validate(identifier: String, method: AuthMethod): String? {
        return if (method == AuthMethod.EMAIL) {
            if (Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) null
            else context.getString(R.string.error_email_format)
        } else {
            if (identifier.matches(Regex("^\\+[1-9]\\d{7,14}$"))) null
            else context.getString(R.string.error_phone_format)
        }
    }

    private fun postJson(
        endpoint: String,
        body: JSONObject,
        accessToken: String?
    ): HttpResponse {
        val connection = (URL("${SupabaseConfig.url}$endpoint").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.doOutput = true
        connection.setRequestProperty("apikey", SupabaseConfig.publishableKey)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        if (!accessToken.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
        }
        connection.outputStream.use { output ->
            output.write(body.toString().toByteArray(Charsets.UTF_8))
        }
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(responseCode, responseBody)
    }

    private fun authError(body: String, code: Int, method: AuthMethod): String {
        return try {
            val json = JSONObject(body)
            val normalizedBody = body.lowercase(Locale.ROOT)
            val errorCode = json.optString("error_code").lowercase(Locale.ROOT)
            val providerDisabled = errorCode == "provider_disabled" ||
                errorCode == "phone_provider_disabled" ||
                errorCode == "email_provider_disabled"
            when {
                normalizedBody.contains("rate limit") ||
                    normalizedBody.contains("rate_limit") ||
                    errorCode.contains("rate_limit") ->
                    context.getString(R.string.error_otp_rate_limit)
                method == AuthMethod.PHONE && (providerDisabled ||
                    normalizedBody.contains("phone provider") ||
                    normalizedBody.contains("sms provider") ||
                    normalizedBody.contains("sms service")) ->
                    context.getString(R.string.error_phone_provider_disabled)
                method == AuthMethod.EMAIL && (providerDisabled ||
                    normalizedBody.contains("email provider") ||
                    normalizedBody.contains("smtp") ||
                    normalizedBody.contains("email service")) ->
                    context.getString(R.string.error_email_provider_disabled)
                errorCode == "otp_expired" ->
                    context.getString(R.string.error_code_expired)
                errorCode == "user_not_found" ->
                    context.getString(R.string.error_user_not_found)
                json.optString("msg").isNotBlank() -> json.optString("msg")
                json.optString("message").isNotBlank() -> json.optString("message")
                else -> context.getString(R.string.error_auth_with_code, code)
            }
        } catch (_: Exception) {
            context.getString(R.string.error_auth_with_code, code)
        }
    }

    private data class HttpResponse(val code: Int, val body: String)
}

private object SupabaseConfig {
    const val url = "https://qieukleyxkvwygzxfgsm.supabase.co"
    const val publishableKey = "sb_publishable_5Y18fNgiYV2tRPyJH5_Ojg_lhf-Ww47"
}