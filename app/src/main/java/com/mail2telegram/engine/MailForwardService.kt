package com.mail2telegram.engine

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mail2telegram.App
import com.mail2telegram.R
import com.mail2telegram.data.AccountStatus
import com.mail2telegram.data.AppLog
import com.mail2telegram.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MailForwardService : Service() {
    private var scope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as App
        if (intent?.action == ACTION_STOP) {
            app.settings.serviceEnabled = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(buildNotification("Starting…"))
        if (scope == null) startEngine(app)
        return START_STICKY
    }

    private fun startInForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }

    private fun startEngine(app: App) {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mail2Telegram:engine").apply {
            setReferenceCounted(false)
            acquire()
        }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        EngineState.running.value = true
        AppLog.info(null, "Service started")
        s.launch { restartOnCrash("poller") { PollingEngine(app).run() } }
        s.launch { restartOnCrash("telegram") { TelegramSender(app).run() } }
        s.launch {
            while (isActive) {
                delay(10_000)
                runCatching { updateNotification(app) }
            }
        }
    }

    private suspend fun restartOnCrash(name: String, block: suspend () -> Unit) {
        while (true) {
            try {
                block()
                return
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.error(null, "Engine component '$name' crashed: ${e.message} – restarting")
                delay(5_000)
            }
        }
    }

    private fun updateNotification(app: App) {
        val counts = app.db.accounts().statusCountsBlocking().associate { it.status to it.count }
        val total = counts.values.sum()
        val ok = counts[AccountStatus.OK] ?: 0
        val errors = (counts[AccountStatus.AUTH_ERROR] ?: 0) + (counts[AccountStatus.CONNECTION_ERROR] ?: 0)
        val queued = app.db.outbox().countBlocking()
        val text = if (EngineState.paused.value) {
            "Offline – paused · $total accounts"
        } else {
            "$total accounts · $ok OK · $errors errors · $queued queued"
        }
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MailForwardService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, App.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mail2Telegram is forwarding")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        EngineState.running.value = false
        runCatching { wakeLock?.release() }
        wakeLock = null
        AppLog.info(null, "Service stopped")
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.mail2telegram.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MailForwardService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MailForwardService::class.java))
        }
    }
}
