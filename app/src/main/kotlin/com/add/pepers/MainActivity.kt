package com.add.pepers

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.add.pepers.cloud.AuthResult
import com.add.pepers.cloud.AuthSession
import com.add.pepers.cloud.SupabaseAuthRepository
import kotlinx.coroutines.launch
import java.util.Locale

private val AuthAppBackground = Color(0xFFFFF8FC)
private val AuthPrimary = Color(0xFF7B2CBF)
private val AuthPrimaryDark = Color(0xFF5A189A)
private val AuthFieldBorder = Color(0xFFD7D2DB)

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
                            showMainApp -> {
                                val currentSession = authRepository.savedSession()
                                if (currentSession?.role == "ASSISTANT") AssistantAccountScreen(repository = authRepository)
                                else TailorWorkspace(repository = authRepository)
                            }
                            else -> PasswordAuthApp(
                                repository = authRepository,
                                googleResult = googleResult.value,
                                googleLoading = googleLoading.value,
                                onGoogleSignIn = { launchGoogleSignIn() },
                                onGoogleResultConsumed = { googleResult.value = null },
                                onEnterApp = {
                                    val session = authRepository.savedSession()
                                    if (session != null) {
                                        LocalDatabaseAccountManager.activateUser(applicationContext, session.userId)

                                        // Always restore an expired/near-expiry session before the first
                                        // cloud request. This prevents a newly opened app from trying
                                        // to sync with a stale access token.
                                        val restored = runCatching {
                                            authRepository.restoreSession()
                                        }.getOrNull()
                                        val activeSession = when (restored) {
                                            is AuthResult.SignedIn -> restored.session
                                            else -> authRepository.savedSession() ?: session
                                        }
                                        LocalDatabaseAccountManager.activateUser(applicationContext, activeSession.userId)

                                        // Restore cloud data before composing the workspace. If the first
                                        // request returns 401, refresh the Supabase token once and retry
                                        // the complete sync instead of silently abandoning cloud restore.
                                        val syncSession = SupabaseSessionStore.load(applicationContext)
                                        val syncResult = if (syncSession != null) {
                                            runCatching {
                                                try {
                                                    SupabaseSyncManager.sync(applicationContext, syncSession)
                                                } catch (error: IllegalStateException) {
                                                    if (error.message?.contains("Supabase 401") == true &&
                                                        syncSession.refreshToken.isNotBlank()
                                                    ) {
                                                        val refreshed = SupabaseAuth.refresh(applicationContext, syncSession)
                                                        SupabaseSyncManager.sync(applicationContext, refreshed)
                                                    } else {
                                                        throw error
                                                    }
                                                }
                                            }
                                        } else {
                                            null
                                        }
                                        syncResult?.exceptionOrNull()?.let { error ->
                                            android.util.Log.e("PepersSync", "Cloud sync failed", error)
                                            Toast.makeText(
                                                this@MainActivity,
                                                "تعذر مزامنة بيانات الحساب الآن. سيتم الاحتفاظ بالبيانات المحلية.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                    ensureLocalProfile(session)
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
        val editor = prefs.edit()
        if (prefs.getString("user_name", "").orEmpty().trim().isBlank()) {
            val label = session.label.trim()
            if (label.isNotBlank()) editor.putString("user_name", label.substringBefore('@'))
        }
        if (prefs.getString("user_email", "").orEmpty().trim().isBlank()) {
            val label = session.label.trim()
            if (label.contains("@")) editor.putString("user_email", label.lowercase())
        }
        editor.putString("profile_user_id", session.userId)
        editor.apply()
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
    onEnterApp: suspend () -> Unit
) {
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var accountRole by rememberSaveable { mutableStateOf("TAILOR") }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<AuthSession?>(null) }
    var restoringSession by remember { mutableStateOf(true) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var resetLoading by remember { mutableStateOf(false) }
    var resetMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // زر الرجوع في شاشة المصادقة يرجع خطوة داخلية أولاً:
    // إغلاق نافذة استعادة كلمة المرور، ثم الرجوع من إنشاء الحساب إلى تسجيل الدخول.
    BackHandler(enabled = createAccount || showResetDialog) {
        if (showResetDialog) {
            showResetDialog = false
            resetMessage = null
        } else {
            createAccount = false
            error = null
        }
    }


    LaunchedEffect(Unit) {
        when (val result = repository.restoreSession()) {
            is AuthResult.SignedIn -> {
                session = result.session
                onEnterApp()
            }
            is AuthResult.Failure -> error = result.message
            null -> Unit
        }
        restoringSession = false
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

    if (restoringSession) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = AuthPrimary)
            Spacer(Modifier.height(12.dp))
            Text("جارٍ استعادة جلسة الحساب…")
        }
        return
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!resetLoading) showResetDialog = false },
            title = { Text("استعادة كلمة المرور") },
            text = {
                Column {
                    Text("أدخل بريد حسابك وسنرسل لك رابطًا لإعادة تعيين كلمة المرور.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; resetMessage = null },
                        label = { Text("البريد الإلكتروني") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    resetMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !resetLoading,
                    onClick = {
                        resetLoading = true
                        scope.launch {
                            val result = repository.requestPasswordReset(email)
                            resetLoading = false
                            when (result) {
                                is AuthResult.SignedIn -> resetMessage = "تم إرسال رابط إعادة تعيين كلمة المرور إلى بريدك الإلكتروني."
                                is AuthResult.Failure -> resetMessage = result.message
                            }
                        }
                    }
                ) {
                    if (resetLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("إرسال الرابط")
                }
            },
            dismissButton = {
                TextButton(enabled = !resetLoading, onClick = { showResetDialog = false }) { Text("إلغاء") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(AuthPrimaryDark, AuthPrimary)))
                .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.96f))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "شعار حساب الخياطين",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("حساب الخياطين", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (createAccount) "إنشاء حساب جديد" else "مرحبًا بعودتك",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AuthPrimaryDark
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (createAccount) "أنشئ حسابك لحفظ بياناتك ومزامنتها بأمان" else "سجّل الدخول للوصول إلى بياناتك ومتابعة عملك",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            if (createAccount) {
                Text("نوع الحساب", fontWeight = FontWeight.SemiBold, color = AuthPrimaryDark, modifier = Modifier.align(Alignment.Start))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (accountRole == "TAILOR") Button(onClick = { accountRole = "TAILOR" }, modifier = Modifier.weight(1f)) { Text("✂️ خياط") }
                    else OutlinedButton(onClick = { accountRole = "TAILOR" }, modifier = Modifier.weight(1f)) { Text("✂️ خياط") }
                    if (accountRole == "ASSISTANT") Button(onClick = { accountRole = "ASSISTANT" }, modifier = Modifier.weight(1f)) { Text("👥 مساعد خياط") }
                    else OutlinedButton(onClick = { accountRole = "ASSISTANT" }, modifier = Modifier.weight(1f)) { Text("👥 مساعد خياط") }
                }
                Spacer(Modifier.height(10.dp))
                if (accountRole == "ASSISTANT") Text("بعد إنشاء الحساب ستدخل معرف الربط الذي أرسله لك الخياط.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("الاسم") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = authFieldColors()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; error = null },
                    label = { Text("رقم الهاتف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = authFieldColors()
                )
                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null; resetMessage = null },
                label = { Text("البريد الإلكتروني") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = authFieldColors()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("كلمة المرور") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        text = if (passwordVisible) "🙈" else "👁️",
                        modifier = Modifier
                            .clickable { passwordVisible = !passwordVisible }
                            .padding(8.dp),
                        color = AuthPrimary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = authFieldColors()
            )

            if (!createAccount) {
                TextButton(
                    onClick = { showResetDialog = true; resetMessage = null },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("نسيت كلمة المرور؟", color = AuthPrimary, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("تأكيد كلمة المرور") },
                    singleLine = true,
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (confirmPasswordVisible) "🙈" else "👁️",
                            modifier = Modifier
                                .clickable { confirmPasswordVisible = !confirmPasswordVisible }
                                .padding(8.dp),
                            color = AuthPrimary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = authFieldColors()
                )
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !loading && !googleLoading,
                onClick = {
                    if (createAccount && password != confirmPassword) {
                        error = context.getString(R.string.error_password_mismatch)
                        return@Button
                    }
                    loading = true
                    scope.launch {
                        val result = if (createAccount) {
                            repository.signUpWithPassword(name, phone, email, password, accountRole)
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(21.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (createAccount) "إنشاء الحساب" else "تسجيل الدخول", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.height(1.dp).weight(1f).background(AuthFieldBorder))
                Text("  أو  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.height(1.dp).weight(1f).background(AuthFieldBorder))
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                enabled = !loading && !googleLoading,
                onClick = onGoogleSignIn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.google_logo),
                    contentDescription = "Google",
                    modifier = Modifier.size(22.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.size(10.dp))
                Text(if (googleLoading) "جارٍ تسجيل الدخول..." else "المتابعة باستخدام Google")
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { createAccount = !createAccount; error = null }) {
                Text(
                    if (createAccount) "لدي حساب بالفعل" else "ليس لديك حساب؟ سجل الآن",
                    color = AuthPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "بمتابعتك فإنك توافق على شروط الخدمة وسياسة الخصوصية.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "🔄 تتم مزامنة بياناتك تلقائيًا عند توفر الإنترنت.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun authFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AuthPrimary,
    unfocusedBorderColor = AuthFieldBorder,
    focusedLabelColor = AuthPrimary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = AuthPrimary
)

@Composable
private fun AssistantAccountScreen(repository: SupabaseAuthRepository) {
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var linkedJson by rememberSaveable { mutableStateOf("[]") }
    var pending by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var dailyJson by rememberSaveable { mutableStateOf("[]") }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        linkedJson = repository.myAssistantLink()
        dailyJson = repository.myAssistantDaily()
    }
    val linked = remember(linkedJson) {
        runCatching {
            val arr = org.json.JSONArray(linkedJson)
            if (arr.length() == 0) null else arr.getJSONObject(0)
        }.getOrNull()
    }
    if (linked == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(35.dp))
            Text("👥 حساب مساعد الخياط", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuthPrimaryDark)
            Spacer(Modifier.height(8.dp))
            Text("اربط حسابك بحساب الخياط باستخدام المعرف الذي أرسله لك.")
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(code, { code = it.uppercase(Locale.ROOT); message = null }, label = { Text("معرف الربط") }, placeholder = { Text("AST-XXXXXXXXXX") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(12.dp))
            Button(enabled = code.isNotBlank() && !loading && !pending, onClick = {
                loading = true
                scope.launch {
                    when (val result = repository.requestAssistantLink(code)) {
                        is AuthResult.SignedIn -> { pending = true; message = "تم إرسال طلب الربط. بانتظار موافقة الخياط." }
                        is AuthResult.Failure -> message = result.message
                    }
                    loading = false
                }
            }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (loading) "جارٍ الإرسال…" else "طلب الربط") }
            if (pending) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { scope.launch {
                    val current = repository.myAssistantLink()
                    if (current != "[]") { linkedJson = current; pending = false; message = "تم قبول الربط." }
                    else message = "لم تتم الموافقة بعد."
                } }, modifier = Modifier.fillMaxWidth()) { Text("تحقق من موافقة الخياط") }
            }
            message?.let { Spacer(Modifier.height(12.dp)); Text(it, color = AuthPrimaryDark) }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
            Text("مرحبًا ${linked.optString("name")}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuthPrimaryDark)
            Spacer(Modifier.height(6.dp))
            Text("المهمة: ${linked.optString("task").ifBlank { "غير محددة" }}")
            Text("السعر: ${linked.optInt("rate")} ريال/قطعة")
            Spacer(Modifier.height(18.dp))
            Text("تم ربط حسابك بالخياط. سيستخدم الطرفان سجل اليوم نفسه لمنع تكرار العمل أو المصروف.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            AssistantDailyWorkForm(repository)
            Spacer(Modifier.height(20.dp))
            Text("السجلات المعتمدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AuthPrimaryDark)
            val daily = runCatching { org.json.JSONArray(dailyJson) }.getOrDefault(org.json.JSONArray())
            if (daily.length() == 0) Text("لا توجد سجلات معتمدة بعد.", fontSize = 11.sp)
            else for (i in 0 until minOf(daily.length(), 20)) {
                val d = daily.getJSONObject(i)
                Text("• ${d.optString("date")} — ${d.optInt("quantity")} قطع — مصروف ${d.optInt("expense")}", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TailorWorkspace(repository: SupabaseAuthRepository) {
    var requests by remember { mutableStateOf(org.json.JSONArray()) }
    var dailyRequests by remember { mutableStateOf(org.json.JSONArray()) }
    var show by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        scope.launch {
            requests = runCatching { org.json.JSONArray(repository.pendingAssistantLinks()) }.getOrDefault(org.json.JSONArray())
            dailyRequests = runCatching { org.json.JSONArray(repository.pendingAssistantDaily()) }.getOrDefault(org.json.JSONArray())
            show = requests.length() > 0 || dailyRequests.length() > 0
        }
    }
    LaunchedEffect(Unit) {
        while (true) { refresh(); kotlinx.coroutines.delay(10_000) }
    }
    Box(Modifier.fillMaxSize()) {
        WorkLogSheet()
        if (show) {
            AlertDialog(
                onDismissRequest = { show = false },
                title = { Text("طلبات المساعدين") },
                text = {
                    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 0 until requests.length()) {
                            val item = requests.getJSONObject(i)
                            Text("طلب ربط: ${item.optString("name")}", fontWeight = FontWeight.Bold)
                            Text("المهمة: ${item.optString("task").ifBlank { "غير محددة" }}")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { scope.launch { repository.approveAssistantLink(item.optString("request_id"), true); refresh() } }, modifier = Modifier.weight(1f)) { Text("موافقة") }
                                OutlinedButton(onClick = { scope.launch { repository.approveAssistantLink(item.optString("request_id"), false); refresh() } }, modifier = Modifier.weight(1f)) { Text("رفض") }
                            }
                        }
                        for (i in 0 until dailyRequests.length()) {
                            val item = dailyRequests.getJSONObject(i)
                            Text("تسجيل عمل: ${item.optString("name")}", fontWeight = FontWeight.Bold)
                            Text("التاريخ: ${item.optString("date")} • القطع: ${item.optInt("quantity")} • المصروف: ${item.optInt("expense")}")
                            if (item.optString("note").isNotBlank()) Text("ملاحظة: ${item.optString("note")}", fontSize = 10.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { scope.launch { repository.approveAssistantDaily(item.optString("id"), true); refresh() } }, modifier = Modifier.weight(1f)) { Text("تأكيد") }
                                OutlinedButton(onClick = { scope.launch { repository.approveAssistantDaily(item.optString("id"), false); refresh() } }, modifier = Modifier.weight(1f)) { Text("رفض") }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { show = false }) { Text("لاحقًا") } }
            )
        }
    }
}


