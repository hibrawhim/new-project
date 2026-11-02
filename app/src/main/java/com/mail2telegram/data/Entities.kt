package com.mail2telegram.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object AccountStatus {
    const val PENDING = "PENDING"
    const val OK = "OK"
    const val AUTH_ERROR = "AUTH_ERROR"
    const val CONNECTION_ERROR = "CONNECTION_ERROR"
}

object AuthType {
    const val PASSWORD = "PASSWORD"
    const val OAUTH2 = "OAUTH2"
}

@Entity(tableName = "accounts", indices = [Index("nextCheckAt"), Index("email")])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    /** IMAP login / e-mail address */
    val email: String,
    /** [com.mail2telegram.mail.Provider] name */
    val provider: String,
    val host: String,
    val port: Int,
    val ssl: Boolean,
    val authType: String,
    /** Encrypted app password, or encrypted OAuth2 refresh token. */
    val secretEnc: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    // ---- polling state ----
    val uidValidity: Long? = null,
    val lastUid: Long = 0,
    val status: String = AccountStatus.PENDING,
    val lastChecked: Long = 0,
    val lastError: String? = null,
    val failures: Int = 0,
    val nextCheckAt: Long = 0,
)

@Entity(tableName = "keywords", indices = [Index("accountId")])
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    /** null = global keyword */
    val accountId: Long? = null,
    val matchSubject: Boolean = true,
    val matchBody: Boolean = true,
    val matchSenderName: Boolean = true,
    val matchSenderAddress: Boolean = true,
)

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val accountLabel: String,
    val subject: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
)

object LogLevel {
    const val INFO = "INFO"
    const val FORWARD = "FORWARD"
    const val ERROR = "ERROR"
}

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val level: String,
    val account: String?,
    val message: String,
)

data class StatusCount(val status: String, val count: Int)
