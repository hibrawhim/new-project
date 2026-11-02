package com.mail2telegram

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.mail2telegram.data.AppDatabase
import com.mail2telegram.data.AppLog
import com.mail2telegram.data.Settings
import com.mail2telegram.engine.NetworkMonitor
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: Settings
        private set
    lateinit var network: NetworkMonitor
        private set

    /** Signals the Telegram sender that new items were queued. */
    val outboxSignal = Channel<Unit>(Channel.CONFLATED)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = AppDatabase.build(this)
        settings = Settings(this)
        network = NetworkMonitor(this)
        AppLog.init(db)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Forwarding service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification while Mail2Telegram is checking your mailboxes"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "service"
        lateinit var instance: App
            private set
    }
}
