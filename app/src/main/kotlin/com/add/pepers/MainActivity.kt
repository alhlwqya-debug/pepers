package com.add.pepers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.add.pepers.cloud.AuthResult
import com.add.pepers.cloud.SupabaseAuthRepository
import kotlinx.coroutines.launch

private val AuthAppBackground = Color(0xFFFFF8FC)
private val PrimaryPurple = Color(0xFF6C4AB6)
private val TextPurple = Color(0xFF5B3C9C)
private val ErrorRed = Color(0xFFB3261E)

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

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleOAuthIntent(intent)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && hasAppPin(applicationContext)) {
            appLockRequested.value = true
        }
    }

    private fun launchGoogleSignIn() {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authRepository.googleAuthUrl())))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.error_google_sign_in), Toast.LENGTH_LONG).show()
        }
    }

    private fun handleOAuthIntent(intent: android.content.Intent?) {
        val callback = intent?.data ?: return
        if (callback.scheme != "pepers" || callback.host != "auth") return
        intent.data = null
        googleLoading.value = true
        lifecycleScope.launch {
            googleResult.value = authRepository.finishGoogleSignIn(callback)
            googleLoading.value = false
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
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_title))
            .setSubtitle(getString(R.string.app_lock_subtitle))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            builder.setDeviceCredentialAllowed(true)
        }
        prompt.authenticate(builder.build())
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
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingConfirmationEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf(repository.savedSession()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val emailFormatError = stringResource(R.string.error_email_format)
    val passwordShortError = stringResource(R.string.error_password_short)
    val passwordMismatchError = stringResource(R.string.error_password_mismatch)
    val accountCreatedMessage = stringResource(R.string.account_created_message)

    LaunchedEffect(googleResult) {
        when (val result = googleResult) {
            is AuthResult.SignedIn -> {
                session = result.session
                onGoogleResultConsumed()
            }
            is AuthResult.EmailConfirmationRequired -> {
                pendingConfirmationEmail = result.email
                error = stringResource(R.string.email_confirmation_required)
                onGoogleResultConsumed()
            }
            is AuthResult.Failure -> {
                error = result.message
                onGoogleResultConsumed()
            }
            is AuthResult.ConfirmationEmailSent, null -> Unit
        }
    }

    LaunchedEffect(session) {
        session ?: return@LaunchedEffect
        try {
            val stored = SupabaseSessionStore.load(context)
            val active = if (stored != null && stored.expiresAt > 0L &&
                stored.expiresAt < System.currentTimeMillis() + 60_000L
            ) SupabaseAuth.refresh(context, stored) else stored
            if (active != null) SupabaseSyncManager.sync(context, active)
        } catch (_: Exception) {
            // Local data remains available if sync is unavailable.
        }
        onEnterApp()
    }

    fun submit(signUp: Boolean) {
        when {
            email.isBlank() -> error = emailFormatError
            password.length < 6 -> error = passwordShortError
            signUp && password != confirmPassword -> error = passwordMismatchError
            else -> {
                loading = true
                error = null
                scope.launch {
                    val result = if (signUp) repository.signUpWithPassword(email, password) else repository.signInWithPassword(email, password)
                    loading = false
                    when (result) {
                        is AuthResult.EmailConfirmationRequired -> {
                            createAccount = false
                            password = ""
                            confirmPassword = ""
                            pendingConfirmationEmail = result.email
                            error = accountCreatedMessage
                        }
                        is AuthResult.ConfirmationEmailSent -> {
                            pendingConfirmationEmail = result.email
                            error = context.getString(R.string.confirmation_email_sent, result.email)
                        }
                        is AuthResult.SignedIn -> {
                            pendingConfirmationEmail = null
                            session = result.session
                        }
                        is AuthResult.Failure -> error = result.message
                    }
                }
            }
        }
    }

    AuthShell {
        Text(stringResource(R.string.app_name), color = TextPurple, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Text(stringResource(if (createAccount) R.string.create_account else R.string.sign_in), color = Color(0xFF201A24), fontSize = 29.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.auth_subtitle), color = Color(0xFF5B5560), fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = email, onValueChange = { email = it; error = null }, label = { Text(stringResource(R.string.email)) }, placeholder = { Text(stringResource(R.string.email_hint)) }, singleLine = true, enabled = !loading && !googleLoading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = { password = it; error = null }, label = { Text(stringResource(R.string.password)) }, placeholder = { Text(stringResource(R.string.password_hint)) }, singleLine = true, enabled = !loading && !googleLoading, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        if (createAccount) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it; error = null }, label = { Text(stringResource(R.string.confirm_password)) }, singleLine = true, enabled = !loading && !googleLoading, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error.orEmpty(), color = ErrorRed, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (pendingConfirmationEmail != null && !createAccount) {
            TextButton(onClick = {
                val confirmationEmail = pendingConfirmationEmail ?: return@TextButton
                loading = true
                scope.launch {
                    val result = repository.resendConfirmation(confirmationEmail)
                    loading = false
                    error = when (result) {
                        is AuthResult.ConfirmationEmailSent -> context.getString(R.string.confirmation_email_sent, result.email)
                        is AuthResult.Failure -> result.message
                        else -> stringResource(R.string.email_confirmation_required)
                    }
                }
            }, enabled = !loading && !googleLoading, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.resend_confirmation)) }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { submit(false) }, enabled = !loading && !googleLoading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)) {
            if (loading && !createAccount) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text(stringResource(R.string.sign_in))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { if (createAccount) submit(true) else { createAccount = true; error = null } }, enabled = !loading && !googleLoading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.create_account)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onGoogleSignIn, enabled = !loading && !googleLoading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            if (googleLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sign_in_with_google))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onEnterApp, enabled = !loading && !googleLoading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.continue_offline)) }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.sync_note), color = Color(0xFF6D6572), fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AuthShell(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
}

@Composable
private fun PepersTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = PrimaryPurple, onPrimary = Color.White, background = AuthAppBackground, surface = AuthAppBackground, error = ErrorRed), content = content)
}