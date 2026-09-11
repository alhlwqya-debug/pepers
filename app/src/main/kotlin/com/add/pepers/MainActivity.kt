package com.add.pepers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 7001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WorkReminderScheduler.schedule(applicationContext)
        CoroutineScope(Dispatchers.IO).launch { createInternalAutoBackup(applicationContext) }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFFF8FF)) {
                        var session by remember { mutableStateOf(SupabaseSessionStore.load(this@MainActivity)) }
                        var offline by remember { mutableStateOf(false) }
                        LaunchedEffect(session) {
                            session?.let { current ->
                                try {
                                    val active = if (current.expiresAt > 0L && current.expiresAt < System.currentTimeMillis() + 60_000L) SupabaseAuth.refresh(this@MainActivity, current) else current
                                    if (active !== current) session = active
                                    SupabaseSyncManager.sync(this@MainActivity, active)
                                } catch (_: Exception) {
                                    // Local SQLite remains available when the network is unavailable.
                                }
                            }
                        }
                        if (session == null && !offline) {
                            SupabaseAuthScreen(onAuthenticated = { session = it; offline = false }, onContinueOffline = { offline = true })
                        } else {
                            WorkLogSheet()
                        }
                    }
                }
            }
        }
    }
}