package com.add.pepers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.add.pepers.cloud.AuthResult
import com.add.pepers.cloud.SupabaseAuthRepository
import kotlinx.coroutines.launch

private val AuthAppBackground = Color(0xFFFFF8FC)
private val PrimaryPurple = Color(0xFF6C4AB6)
private val TextPurple = Color(0xFF5B3C9C)
private val ErrorRed = Color(0xFFB3261E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SupabaseAuthRepository(applicationContext)
        setContent {
            PepersTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = AuthAppBackground) {
                        var showMainApp by remember { mutableStateOf(false) }
                        if (showMainApp) {
                            WorkLogSheet()
                        } else {
                            PasswordAuthApp(
                                repository = repository,
                                onEnterApp = { showMainApp = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordAuthApp(
    repository: SupabaseAuthRepository,
    onEnterApp: () -> Unit
) {
    var createAccount by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf(repository.savedSession()) }
    val scope = rememberCoroutineScope()
    val emailFormatError = stringResource(R.string.error_email_format)
    val passwordShortError = stringResource(R.string.error_password_short)
    val passwordMismatchError = stringResource(R.string.error_password_mismatch)
    val accountCreatedMessage = stringResource(R.string.account_created_message)

    LaunchedEffect(session) {
        if (session != null) onEnterApp()
    }

    AuthShell {
        Text(
            text = stringResource(R.string.app_name),
            color = TextPurple,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(if (createAccount) R.string.create_account else R.string.sign_in),
            color = Color(0xFF201A24),
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.password_auth_subtitle),
            color = Color(0xFF5B5560),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AuthModeButton(
                text = stringResource(R.string.create_account),
                selected = createAccount,
                modifier = Modifier.weight(1f),
                onClick = {
                    createAccount = true
                    error = null
                }
            )
            AuthModeButton(
                text = stringResource(R.string.sign_in),
                selected = !createAccount,
                modifier = Modifier.weight(1f),
                onClick = {
                    createAccount = false
                    error = null
                }
            )
        }
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text(stringResource(R.string.email)) },
            placeholder = { Text(stringResource(R.string.email_hint)) },
            singleLine = true,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text(stringResource(R.string.password)) },
            placeholder = { Text(stringResource(R.string.password_hint)) },
            singleLine = true,
            enabled = !loading,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        if (createAccount) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null },
                label = { Text(stringResource(R.string.confirm_password)) },
                singleLine = true,
                enabled = !loading,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = error.orEmpty(),
                color = ErrorRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                when {
                    email.isBlank() -> error = emailFormatError
                    password.length < 6 -> error = passwordShortError
                    createAccount && password != confirmPassword -> error = passwordMismatchError
                    else -> {
                        loading = true
                        error = null
                        scope.launch {
                            val result = if (createAccount) {
                                repository.signUpWithPassword(email, password)
                            } else {
                                repository.signInWithPassword(email, password)
                            }
                            loading = false
                            when (result) {
                                AuthResult.AccountCreated -> {
                                    createAccount = false
                                    password = ""
                                    confirmPassword = ""
                                    error = accountCreatedMessage
                                }
                                is AuthResult.SignedIn -> session = result.session
                                is AuthResult.Failure -> error = result.message
                            }
                        }
                    }
                }
            },
            enabled = !loading,
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
                Text(stringResource(if (createAccount) R.string.create_account else R.string.sign_in))
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onEnterApp,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.continue_offline))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.password_auth_note),
            color = Color(0xFF6D6572),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AuthModeButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) Color.White else TextPurple,
            containerColor = if (selected) PrimaryPurple else Color(0xFFF0E8FB)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(text)
    }
}

@Composable
private fun AuthShell(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
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
