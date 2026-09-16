package com.add.pepers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.add.pepers.cloud.SupabaseAuthRepository

class WorkReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("daily_work_reminder_enabled", true)

        if (!enabled) {
            WorkReminderScheduler.cancel(appContext)
            return
        }

        val session = SupabaseAuthRepository(appContext).savedSession()

        // Do not inspect local data while no account is authenticated. This also
        // prevents a scheduled receiver from touching the previous account after logout.
        if (session == null) {
            WorkReminderScheduler.schedule(appContext)
            return
        }

        LocalDatabaseAccountManager.activateUser(appContext, session.userId)
        val database = Database(appContext)

        try {
            if (!database.hasWorkRecordedToday()) {
                showNotification(appContext)
            }
        } finally {
            database.close()
        }

        WorkReminderScheduler.schedule(appContext)
    }

    private fun showNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = WorkReminderScheduler.CHANNEL_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تذكير تسجيل العمل",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيه يومي عند عدم تسجيل العمل"
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            WorkReminderScheduler.OPEN_APP_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("تذكير تسجيل العمل")
            .setContentText("نسيت تسجيل عمل اليوم؟ افتح Pepers وسجّل عملك الآن")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("نسيت تسجيل عمل اليوم؟ افتح Pepers وسجّل عملك الآن")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.app.ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(WorkReminderScheduler.NOTIFICATION_ID, notification)
        }
    }
}

object WorkReminderScheduler {
    const val CHANNEL_ID = "daily_work_reminder"
    const val NOTIFICATION_ID = 2001
    const val ALARM_REQUEST_CODE = 2002
    const val OPEN_APP_REQUEST_CODE = 2003

    private const val HOUR = 20
    private const val MINUTE = 0

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("daily_work_reminder_enabled", true)) return

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(appContext, WorkReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, HOUR)
            set(java.util.Calendar.MINUTE, MINUTE)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setInexactRepeating(
            android.app.AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            android.app.AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(appContext, WorkReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
