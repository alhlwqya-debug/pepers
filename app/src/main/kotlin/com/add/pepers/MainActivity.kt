package com.add.pepers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.add.pepers.cloud.AuthMethod
import com.add.pepers.cloud.AuthResult
import com.add.pepers.cloud.AuthSession
import com.add.pepers.cloud.SupabaseAuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AuthAppBackground = Color(0xFFFFF8FC)
private val PrimaryPurple = Color(0xFF6C4AB6)
private val TextPurple = Color(0xFF5B3C9C)
private val SoftPurple = Color(0xFFF0E8FB)
private val ErrorRed = Color(0xFFB3261E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SupabaseAuthRepository(applicationContext)
        setContent {
            PepersTheme {
                Surface(color = AuthAppBackground) {
                    AuthApp(repository)
                }
            }
        }
    }
}

@Composable
private fun PepersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = PrimaryPurple,
            onPrimary = Color.White,
            background = AuthAppBackground,
            surface = AuthAppBackground,
            error = ErrorRed
        ),
        content = content
    )
}

private enum class AuthStep {
    IDENTIFIER,
    CODE,
    OFFLINE,
    SIGNED_IN
}

@Composable
private fun AuthApp(repository: SupabaseAuthRepository) {
    var step by rememberSaveable { mutableStateOf(AuthStep.IDENTIFIER.name) }
    var methodName by rememberSaveable { mutableStateOf(AuthMethod.EMAIL.name) }
    var createAccount by rememberSaveable { mutableStateOf(true) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var resendSeconds by rememberSaveable { mutableIntStateOf(0) }
    var session by remember { mutableStateOf(repository.savedSession()) }
    val scope = rememberCoroutineScope()
    val method = AuthMethod.valueOf(methodName)

    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0) {
            delay(1_000)
            resendSeconds -= 1
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AnimatedContent(
            targetState = if (session != null && AuthStep.valueOf(step) != AuthStep.CODE) {
                AuthStep.SIGNED_IN
            } else {
                AuthStep.valueOf(step)
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "auth-flow"
        ) { currentStep ->
            when (currentStep) {
                AuthStep.IDENTIFIER -> IdentifierStep(
                    method = method,
                    createAccount = createAccount,
                    identifier = identifier,
                    error = error,
                    loading = loading,
                    onMethodChange = {
                        methodName = it.name
                        error = null
                    },
                    onCreateAccountChange = {
                        createAccount = it
                        error = null
                    },
                    onIdentifierChange = {
                        identifier = it
                        error = null
                    },
                    onContinueOffline = {
                        session = null
                        error = null
                        step = AuthStep.OFFLINE.name
                    },
                    onSubmit = {
                        loading = true
                        error = null
                        scope.launch {
                            val result = repository.sendCode(identifier, method, createAccount)
                            loading = false
                            when (result) {
                                AuthResult.CodeSent -> {
                                    code = ""
                                    resendSeconds = 60
                                    step = AuthStep.CODE.name
                                }
                                is AuthResult.Failure -> error = result.message
                                is AuthResult.SignedIn -> Unit
                            }
                        }
                    }
                )

                AuthStep.CODE -> CodeStep(
                    method = method,
                    identifier = identifier,
                    code = code,
                    resendSeconds = resendSeconds,
                    error = error,
                    loading = loading,
                    onCodeChange = {
                        code = it.filter(Char::isDigit).take(6)
                        error = null
                    },
                    onBack = {
                        step = AuthStep.IDENTIFIER.name
                        error = null
                    },
                    onResend = {
                        loading = true
                        error = null
                        scope.launch {
                            val result = repository.sendCode(identifier, method, createAccount)
                            loading = false
                            when (result) {
                                AuthResult.CodeSent -> resendSeconds = 60
                                is AuthResult.Failure -> error = result.message
                                is AuthResult.SignedIn -> Unit
                            }
                        }
                    },
                    onVerify = {
                        loading = true
                        error = null
                        scope.launch {
                            val result = repository.verifyCode(identifier, method, code)
                            loading = false
                            when (result) {
                                is AuthResult.SignedIn -> {
                                    session = result.session
                                    step = AuthStep.SIGNED_IN.name
                                }
                                is AuthResult.Failure -> error = result.message
                                AuthResult.CodeSent -> Unit
                            }
                        }
                    }
                )

                AuthStep.SIGNED_IN -> SignedInStep(
                    session = session,
                    onSignOut = {
                        repository.clearSession()
                        session = null
                        step = AuthStep.IDENTIFIER.name
                        identifier = ""
                        code = ""
                        error = null
                    }
                )

                AuthStep.OFFLINE -> OfflineStep(
                    onSignIn = {
                        step = AuthStep.IDENTIFIER.name
                        error = null
                    }
                )
            }
        }
    }
}

@Composable
private fun IdentifierStep(
    method: AuthMethod,
    createAccount: Boolean,
    identifier: String,
    error: String?,
    loading: Boolean,
    onMethodChange: (AuthMethod) -> Unit,
    onCreateAccountChange: (Boolean) -> Unit,
    onIdentifierChange: (String) -> Unit,
    onContinueOffline: () -> Unit,
    onSubmit: () -> Unit
) {
    AuthShell {
        BrandHeader(
            title = stringResource(if (createAccount) R.string.create_account else R.string.sign_in),
            subtitle = stringResource(R.string.auth_subtitle)
        )
        Spacer(Modifier.height(28.dp))

        AuthModeSwitch(
            createAccount = createAccount,
            onCreateAccountChange = onCreateAccountChange
        )
        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.choose_method),
            color = TextPurple,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MethodButton(
                label = stringResource(R.string.email),
                icon = Icons.Default.Email,
                selected = method == AuthMethod.EMAIL,
                modifier = Modifier.weight(1f),
                onClick = { onMethodChange(AuthMethod.EMAIL) }
            )
            MethodButton(
                label = stringResource(R.string.phone),
                icon = Icons.Default.Phone,
                selected = method == AuthMethod.PHONE,
                modifier = Modifier.weight(1f),
                onClick = { onMethodChange(AuthMethod.PHONE) }
            )
        }
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = identifier,
            onValueChange = onIdentifierChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(stringResource(if (method == AuthMethod.EMAIL) R.string.email else R.string.phone))
            },
            placeholder = {
                Text(
                    stringResource(
                        if (method == AuthMethod.EMAIL) R.string.email_hint else R.string.phone_hint
                    )
                )
            },
            leadingIcon = {
                Icon(
                    if (method == AuthMethod.EMAIL) Icons.Default.Email else Icons.Default.Phone,
                    contentDescription = null
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (method == AuthMethod.EMAIL) KeyboardType.Email else KeyboardType.Phone
            ),
            shape = RoundedCornerShape(14.dp)
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            ErrorMessage(error)
        }

        Spacer(Modifier.height(18.dp))
        PrimaryActionButton(
            text = stringResource(R.string.send_code),
            loading = loading,
            enabled = identifier.isNotBlank(),
            onClick = onSubmit
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onContinueOffline,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPurple)
        ) {
            Text(stringResource(R.string.continue_offline))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.otp_privacy_note),
            textAlign = TextAlign.Center,
            color = Color(0xFF6D6572),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CodeStep(
    method: AuthMethod,
    identifier: String,
    code: String,
    resendSeconds: Int,
    error: String?,
    loading: Boolean,
    onCodeChange: (String) -> Unit,
    onBack: () -> Unit,
    onResend: () -> Unit,
    onVerify: () -> Unit
) {
    AuthShell {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        BrandHeader(
            title = stringResource(R.string.enter_code),
            subtitle = stringResource(
                if (method == AuthMethod.EMAIL) R.string.code_sent_email else R.string.code_sent_phone,
                identifier
            )
        )
        Spacer(Modifier.height(30.dp))

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.verification_code)) },
            placeholder = { Text("000000") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                letterSpacing = 8.sp,
                textAlign = TextAlign.Center
            ),
            shape = RoundedCornerShape(14.dp)
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            ErrorMessage(error)
        }

        Spacer(Modifier.height(18.dp))
        PrimaryActionButton(
            text = stringResource(R.string.verify_and_continue),
            loading = loading,
            enabled = code.length == 6,
            onClick = onVerify
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onResend,
            enabled = resendSeconds == 0 && !loading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(
                if (resendSeconds > 0) {
                    stringResource(R.string.resend_after, resendSeconds)
                } else {
                    stringResource(R.string.resend_code)
                }
            )
        }
    }
}

@Composable
private fun SignedInStep(
    session: AuthSession?,
    onSignOut: () -> Unit
) {
    AuthShell {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = PrimaryPurple,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.signed_in_title),
            color = Color(0xFF201A24),
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = session?.label.orEmpty(),
            color = TextPurple,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(26.dp))
        Text(
            text = stringResource(R.string.signed_in_body),
            color = Color(0xFF5B5560),
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.sign_out))
        }
    }
}

@Composable
private fun OfflineStep(onSignIn: () -> Unit) {
    AuthShell {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = PrimaryPurple,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.offline_title),
            color = Color(0xFF201A24),
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.offline_body),
            color = Color(0xFF5B5560),
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))
        PrimaryActionButton(
            text = stringResource(R.string.sign_in_for_sync),
            loading = false,
            enabled = true,
            onClick = onSignIn
        )
    }
}

@Composable
private fun AuthShell(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthAppBackground)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun BrandHeader(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.app_name),
            color = PrimaryPurple,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            color = Color(0xFF201A24),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            color = Color(0xFF5B5560),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun AuthModeSwitch(
    createAccount: Boolean,
    onCreateAccountChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoftPurple, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeButton(
            label = stringResource(R.string.create_account),
            selected = createAccount,
            modifier = Modifier.weight(1f),
            onClick = { onCreateAccountChange(true) }
        )
        ModeButton(
            label = stringResource(R.string.sign_in),
            selected = !createAccount,
            modifier = Modifier.weight(1f),
            onClick = { onCreateAccountChange(false) }
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PrimaryPurple else Color.Transparent,
            contentColor = if (selected) Color.White else TextPurple
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (selected) 2.dp else 0.dp)
    ) {
        Text(label)
    }
}

@Composable
private fun MethodButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) SoftPurple else Color.Transparent,
            contentColor = if (selected) TextPurple else Color(0xFF6D6572)
        ),
        border = BorderStroke(1.dp, if (selected) PrimaryPurple else Color(0xFFB8AFBB))
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(7.dp))
        Text(label)
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(text)
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Text(
        text = message,
        color = ErrorRed,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}