package com.mail2telegram.data

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Small async logger backed by Room; keeps the most recent entries only. */
object AppLog {
    private const val KEEP = 2000
    private val executor = Executors.newSingleThreadExecutor()
    private val counter = AtomicInteger()
    private lateinit var db: AppDatabase

    fun init(database: AppDatabase) {
        db = database
    }

    fun info(account: String?, message: String) = write(LogLevel.INFO, account, message)
    fun forward(account: String?, message: String) = write(LogLevel.FORWARD, account, message)
    fun error(account: String?, message: String) = write(LogLevel.ERROR, account, message)

    private fun write(level: String, account: String?, message: String) {
        executor.execute {
            runCatching {
                db.logs().insertBlocking(LogEntity(level = level, account = account, message = message.take(1000)))
                if (counter.incrementAndGet() % 100 == 0) db.logs().pruneBlocking(KEEP)
            }
        }
    }
}
