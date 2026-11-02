package com.mail2telegram.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY label COLLATE NOCASE, email COLLATE NOCASE")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE enabled = 1 AND nextCheckAt <= :now ORDER BY nextCheckAt")
    fun dueBlocking(now: Long): List<AccountEntity>

    @Query("SELECT status AS status, COUNT(*) AS count FROM accounts WHERE enabled = 1 GROUP BY status")
    fun statusCountsBlocking(): List<StatusCount>

    @Query("SELECT email FROM accounts")
    suspend fun allEmails(): List<String>

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "UPDATE accounts SET label = :label, email = :email, provider = :provider, host = :host, port = :port, " +
            "ssl = :ssl, authType = :authType, secretEnc = :secretEnc, enabled = :enabled, " +
            "nextCheckAt = 0, failures = 0 WHERE id = :id"
    )
    suspend fun updateConfig(
        id: Long, label: String, email: String, provider: String, host: String, port: Int,
        ssl: Boolean, authType: String, secretEnc: String, enabled: Boolean,
    )

    @Query("UPDATE accounts SET uidValidity = NULL, lastUid = 0, status = 'PENDING', lastError = NULL WHERE id = :id")
    suspend fun resetBaseline(id: Long)

    @Query(
        "UPDATE accounts SET status = :status, lastChecked = :checked, lastError = :error, " +
            "failures = :failures, nextCheckAt = :next WHERE id = :id"
    )
    fun updateStatus(id: Long, status: String, checked: Long, error: String?, failures: Int, next: Long)

    @Query("UPDATE accounts SET nextCheckAt = :next WHERE id = :id")
    fun updateNextCheck(id: Long, next: Long)

    /** Compare-and-set so a concurrent edit/reset can never cause duplicates. Returns rows updated. */
    @Query(
        "UPDATE accounts SET uidValidity = :validity, lastUid = :uid " +
            "WHERE id = :id AND uidValidity IS :oldValidity AND lastUid = :oldUid"
    )
    fun updateUidIfUnchanged(id: Long, validity: Long, uid: Long, oldValidity: Long?, oldUid: Long): Int

    @Query("UPDATE accounts SET secretEnc = :secretEnc WHERE id = :id")
    fun updateSecret(id: Long, secretEnc: String)

    @Query("UPDATE accounts SET nextCheckAt = 0")
    suspend fun checkAllNow()
}

@Dao
interface KeywordDao {
    @Query("SELECT * FROM keywords WHERE accountId IS NULL ORDER BY text COLLATE NOCASE")
    fun observeGlobal(): Flow<List<KeywordEntity>>

    @Query("SELECT * FROM keywords WHERE accountId = :accountId ORDER BY text COLLATE NOCASE")
    fun observeForAccount(accountId: Long): Flow<List<KeywordEntity>>

    @Query("SELECT * FROM keywords")
    fun allBlocking(): List<KeywordEntity>

    @Insert
    suspend fun insert(keyword: KeywordEntity): Long

    @Update
    suspend fun update(keyword: KeywordEntity)

    @Query("DELETE FROM keywords WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM keywords WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}

@Dao
interface OutboxDao {
    @Insert
    fun insertBlocking(item: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY id LIMIT 1")
    fun nextBlocking(): OutboxEntity?

    @Query("DELETE FROM outbox WHERE id = :id")
    fun deleteBlocking(id: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE id = :id")
    fun incrementAttempts(id: Long)

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox")
    fun countBlocking(): Int

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
interface LogDao {
    @Insert
    fun insertBlocking(entry: LogEntity): Long

    @Query("SELECT * FROM logs ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LogEntity>>

    @Query("DELETE FROM logs WHERE id <= (SELECT MAX(id) FROM logs) - :keep")
    fun pruneBlocking(keep: Int)

    @Query("DELETE FROM logs")
    suspend fun clear()
}
