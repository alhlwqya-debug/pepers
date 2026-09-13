package com.add.pepers

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.add.pepers.cloud.AuthResult
import com.add.pepers.cloud.AuthSession
import com.add.pepers.cloud.SupabaseAuthRepository
import kotlinx.coroutines.launch

private val AuthAppBackground = androidx.compose.ui.graphics.Color(0xFFFFF8FC)

class MainActivity : FragmentActivity() {
    private val appLockRequested = mutableStateOf(false)
    private val googleResult = mutableStateOf<AuthResult?>(null)
    private val googleLoading = mutableStateOf(false)
    private lateinit var authRepository: SupabaseAuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authRepository = SupabaseAuthRepository(applicationContext)
        appLockRequested.value = hasAppPin(applicationContext)
        BackgroundSyncScheduler.ensure(applicationContext)
        WorkReminderScheduler.schedule(applicationContext)

        setContent {
            PepersTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = AuthAppBackground) {
                        var showMainApp by remember { mutableStateOf(false) }
                        when {
                            appLockRequested.value -> AppLockScreen(
                                context = applicationContext,
                                onUnlocked = { appLockRequested.value = false },
                                onUseDeviceLock = { authenticateWithDeviceLock() }
                            )
                            showMainApp -> WorkLogSheet()
                            else -> PasswordAuthApp(
                                repository = authRepository,
                                googleResult = googleResult.value,
                                googleLoading = googleLoading.value,
                                onGoogleSignIn = { launchGoogleSignIn() },
                                onGoogleResultConsumed = { googleResult.value = null },
                                onEnterApp = {
                                    ensureLocalProfile(authRepository.savedSession())
                                    requestNotificationPermissionIfNeeded()
                                    BackgroundSyncScheduler.requestNow(applicationContext)
                                    showMainApp = true
                                }
                            )
                        }
                    }
                }
            }
        }
        handleOAuthIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && hasAppPin(applicationContext)) {
            appLockRequested.value = true
        }
    }

    private fun launchGoogleSignIn() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authRepository.googleAuthUrl())))
        }.onFailure {
            Toast.makeText(this, getString(R.string.error_google_sign_in), Toast.LENGTH_LONG).show()
        }
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val callback = intent?.data ?: return
        if (callback.scheme != "pepers" || callback.host != "auth") return
        intent.data = null
        googleLoading.value = true
        lifecycleScope.launch {
            googleResult.value = authRepository.finishGoogleSignIn(callback)
            googleLoading.value = false
        }
    }

    private fun ensureLocalProfile(session: AuthSession?) {
        if (session == null) return
        val prefs = getSharedPreferences("add_paper_user", MODE_PRIVATE)
        val currentName = prefs.getString("user_name", "").orEmpty().trim()
        if (currentName.isBlank()) {
            val label = session.label.trim()
            if (label.isNotBlank()) {
                prefs.edit().putString("user_name", label.substringBefore('@')).apply()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
    }

    private fun authenticateWithDeviceLock() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                appLockRequested.value = false
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_title))
            .setSubtitle(getString(R.string.app_lock_subtitle))
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}

@Composable
private fun PasswordAuthApp(
    repository: SupabaseAuthRepository,
    googleResult: AuthResult?,
    googleLoading: Boolean,
    onGoogleSignIn: () -> Unit,
    onGoogleResultConsumed: () -> Unit,
    onEnterApp: () -> Unit
) {
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf(repository.savedSession()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (session != null) onEnterApp()
    }

    LaunchedEffect(googleResult) {
        when (val result = googleResult) {
            is AuthResult.SignedIn -> {
                session = result.session
                onGoogleResultConsumed()
                onEnterApp()
            }
            is AuthResult.Failure -> {
                error = result.message
                onGoogleResultConsumed()
            }
            null -> Unit
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (createAccount) "إنشاء حساب جديد" else "تسجيل الدخول",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text("الحساب يحفظ بياناتك ويتيح مزامنتها بين أجهزتك")
        Spacer(Modifier.height(20.dp))

        if (createAccount) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("الاسم") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; error = null },
                label = { Text("رقم الهاتف") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("البريد الإلكتروني") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("كلمة المرور") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (createAccount) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null },
                label = { Text("تأكيد كلمة المرور") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !loading,
            onClick = {
                if (createAccount && password != confirmPassword) {
                    error = context.getString(R.string.error_password_mismatch)
                    return@Button
                }
                loading = true
                scope.launch {
                    val result = if (createAccount) {
                        repository.signUpWithPassword(name, phone, email, password)
                    } else {
                        repository.signInWithPassword(email, password)
                    }
                    loading = false
                    when (result) {
                        is AuthResult.SignedIn -> {
                            session = result.session
                            onEnterApp()
                        }
                        is AuthResult.Failure -> error = result.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
            else Text(if (createAccount) "إنشاء الحساب" else "تسجيل الدخول")
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            enabled = !loading && !googleLoading,
            onClick = onGoogleSignIn,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (googleLoading) "جارٍ تسجيل الدخول..." else "المتابعة باستخدام Google")
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { createAccount = !createAccount; error = null }) {
            Text(if (createAccount) "لدي حساب بالفعل" else "إنشاء حساب جديد")
        }
    }
}
