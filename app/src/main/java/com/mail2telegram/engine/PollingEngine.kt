package com.mail2telegram.engine

import com.mail2telegram.App
import com.mail2telegram.data.AccountEntity
import com.mail2telegram.data.AccountStatus
import com.mail2telegram.data.AppLog
import com.mail2telegram.data.KeywordEntity
import com.mail2telegram.data.OutboxEntity
import com.mail2telegram.mail.ImapChecker
import com.mail2telegram.mail.KeywordMatcher
import com.mail2telegram.mail.OAuthException
import com.mail2telegram.telegram.TelegramFormatter
import jakarta.mail.AuthenticationFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.random.Random

/**
 * Schedules IMAP checks so every enabled account is polled once per interval (default 60 s),
 * with at most N (default 25) concurrent connections on a fixed thread pool.
 */
class PollingEngine(private val app: App) {
    private val checker = ImapChecker()
    private val inFlight: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val active = AtomicInteger()

    suspend fun run() {
        val threads = app.settings.maxConcurrency
        val pool = Executors.newFixedThreadPool(threads) { r ->
            Thread(r, "imap-worker").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        try {
            coroutineScope {
                while (isActive) {
                    if (!app.network.online.value) {
                        EngineState.paused.value = true
                        AppLog.info(null, "Offline – polling paused")
                        app.network.online.first { it }
                        EngineState.paused.value = false
                        AppLog.info(null, "Back online – polling resumed")
                        continue
                    }
                    val now = System.currentTimeMillis()
                    val due = withContext(Dispatchers.IO) { app.db.accounts().dueBlocking(now) }
                    if (due.isNotEmpty()) {
                        val keywords = withContext(Dispatchers.IO) { app.db.keywords().allBlocking() }
                        for (acc in due) {
                            if (!inFlight.add(acc.id)) continue
                            launch(pool) {
                                EngineState.inFlight.value = active.incrementAndGet()
                                try {
                                    checkAccount(acc, keywords)
                                } finally {
                                    EngineState.inFlight.value = active.decrementAndGet()
                                    inFlight.remove(acc.id)
                                }
                            }
                        }
                    }
                    delay(1000)
                }
            }
        } finally {
            pool.close()
            EngineState.paused.value = false
        }
    }

    private fun checkAccount(acc: AccountEntity, keywords: List<KeywordEntity>) {
        val dao = app.db.accounts()
        val start = System.currentTimeMillis()
        val interval = app.settings.pollIntervalSec * 1000L
        val name = acc.label.ifBlank { acc.email }
        try {
            val secret = Credentials.resolve(app, acc)
            val outcome = checker.check(acc, secret)

            val relevant = keywords.filter { it.accountId == null || it.accountId == acc.id }
            val matched = outcome.items.filter { KeywordMatcher.matches(it, relevant) }
            val now = System.currentTimeMillis()

            var committed = false
            app.db.runInTransaction(Runnable {
                val rows = dao.updateUidIfUnchanged(acc.id, outcome.uidValidity, outcome.lastUid, acc.uidValidity, acc.lastUid)
                if (rows == 1) {
                    for (m in matched) {
                        app.db.outbox().insertBlocking(
                            OutboxEntity(
                                accountId = acc.id,
                                accountLabel = name,
                                subject = m.subject,
                                text = TelegramFormatter.format(acc, m),
                            )
                        )
                    }
                    committed = true
                }
                dao.updateStatus(acc.id, AccountStatus.OK, now, null, 0, if (outcome.hasMore) now else start + interval)
            })
            if (committed && matched.isNotEmpty()) app.outboxSignal.trySend(Unit)

            if (committed && outcome.baselined) {
                if (acc.uidValidity == null) {
                    AppLog.info(name, "Connected. Baseline set – only mail arriving from now on will be forwarded.")
                } else {
                    AppLog.info(name, "UIDVALIDITY changed – mailbox re-baselined, old mail skipped.")
                }
            }
            if (acc.status == AccountStatus.AUTH_ERROR || acc.status == AccountStatus.CONNECTION_ERROR) {
                AppLog.info(name, "Recovered – connection OK")
            }
            val skipped = outcome.items.size - matched.size
            if (committed && skipped > 0) AppLog.info(name, "$skipped new message(s) did not match filters")
        } catch (e: AuthenticationFailedException) {
            fail(acc, name, AccountStatus.AUTH_ERROR, "Authentication failed: ${e.message.orEmpty()}")
        } catch (e: OAuthException) {
            fail(acc, name, AccountStatus.AUTH_ERROR, if (e.invalidGrant) "Microsoft sign-in expired – sign in again" else e.message.orEmpty())
        } catch (e: CredentialException) {
            fail(acc, name, AccountStatus.AUTH_ERROR, e.message.orEmpty())
        } catch (e: Throwable) {
            if (!app.network.online.value) {
                // Lost connectivity mid-check: not the account's fault, retry when back online.
                dao.updateNextCheck(acc.id, System.currentTimeMillis() + 5_000)
                return
            }
            fail(acc, name, AccountStatus.CONNECTION_ERROR, describe(e))
        }
    }

    private fun describe(e: Throwable): String {
        val root = generateSequence(e) { it.cause?.takeIf { c -> c !== it } }.last()
        val msg = e.message?.takeIf { it.isNotBlank() } ?: root.message ?: root.javaClass.simpleName
        return if (root !== e && root.message != null && root.message != msg) "$msg (${root.message})" else msg
    }

    private fun fail(acc: AccountEntity, name: String, status: String, message: String) {
        val failures = acc.failures + 1
        val now = System.currentTimeMillis()
        val backoff = if (status == AccountStatus.AUTH_ERROR) {
            // Be gentle with auth failures to avoid provider lockouts: 5 min, 10, 20 … max 60 min
            min(5 * 60_000L shl min(failures - 1, 4), 60 * 60_000L)
        } else {
            // Network/connection failures: 30 s, 60 s, 2 min … max 15 min, with jitter
            val base = min(30_000L shl min(failures - 1, 5), 15 * 60_000L)
            base + Random.nextLong(base / 5 + 1)
        }
        app.db.accounts().updateStatus(acc.id, status, now, message.take(400), failures, now + backoff)
        if (acc.status != status || acc.lastError != message.take(400)) {
            AppLog.error(name, message.take(400))
        }
    }
}
