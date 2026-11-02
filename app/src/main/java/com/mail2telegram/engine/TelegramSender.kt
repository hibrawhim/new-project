package com.mail2telegram.engine

import com.mail2telegram.App
import com.mail2telegram.data.AppLog
import com.mail2telegram.telegram.TelegramClient
import com.mail2telegram.telegram.TelegramFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * Drains the persistent outbox one message at a time, honouring Telegram rate limits
 * (≈1 msg/s per private chat, 20 msg/min per group) and 429 retry_after.
 */
class TelegramSender(private val app: App) {
    private val client = TelegramClient(app.http)

    suspend fun run() = withContext(Dispatchers.IO) {
        var backoff = 2_000L
        var lastConfigError: String? = null
        while (isActive) {
            if (!app.network.online.value) {
                app.network.online.first { it }
                continue
            }
            val token = app.settings.botToken
            val chat = app.settings.chatId
            if (token.isBlank() || chat.isBlank()) {
                if (lastConfigError != "missing") {
                    lastConfigError = "missing"
                    AppLog.error(null, "Telegram bot token / chat ID not set – messages are queued")
                }
                withTimeoutOrNull(10_000) { app.outboxSignal.receive() }
                continue
            }
            val item = app.db.outbox().nextBlocking()
            if (item == null) {
                withTimeoutOrNull(5_000) { app.outboxSignal.receive() }
                continue
            }
            var result = client.send(token, chat, item.text)
            if (result is TelegramClient.Result.Failed && result.code == 400 &&
                result.description.contains("parse", ignoreCase = true)
            ) {
                result = client.send(token, chat, TelegramFormatter.toPlain(item.text), html = false)
            }
            when (result) {
                TelegramClient.Result.Ok -> {
                    app.db.outbox().deleteBlocking(item.id)
                    AppLog.forward(item.accountLabel, item.subject)
                    backoff = 2_000L
                    lastConfigError = null
                    delay(if (chat.startsWith("-")) 3_100L else 1_100L)
                }
                is TelegramClient.Result.RetryAfter -> {
                    AppLog.info(null, "Telegram rate limit – waiting ${result.seconds}s")
                    delay(result.seconds * 1000L + 500L)
                }
                is TelegramClient.Result.NetworkError -> {
                    delay(backoff)
                    backoff = min(backoff * 2, 60_000L)
                }
                is TelegramClient.Result.Failed -> {
                    val configProblem = result.code == 401 || result.code == 403 || result.code == 404 ||
                        result.description.contains("chat not found", ignoreCase = true)
                    when {
                        configProblem -> {
                            val msg = "Telegram error ${result.code}: ${result.description} – check token/chat ID"
                            if (lastConfigError != msg) {
                                lastConfigError = msg
                                AppLog.error(null, msg)
                            }
                            withTimeoutOrNull(60_000) { app.outboxSignal.receive() }
                        }
                        result.code >= 500 || item.attempts < 3 -> {
                            app.db.outbox().incrementAttempts(item.id)
                            delay(backoff)
                            backoff = min(backoff * 2, 60_000L)
                        }
                        else -> {
                            AppLog.error(item.accountLabel, "Dropped after retries (${result.code} ${result.description}): ${item.subject}")
                            app.db.outbox().deleteBlocking(item.id)
                        }
                    }
                }
            }
        }
    }
}
