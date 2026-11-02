package com.mail2telegram.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mail2telegram.App

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                val app = context.applicationContext as App
                if (app.settings.serviceEnabled) {
                    runCatching { MailForwardService.start(context) }
                }
            }
        }
    }
}
