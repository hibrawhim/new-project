package com.mail2telegram.engine

import com.mail2telegram.App
import com.mail2telegram.data.AccountEntity
import com.mail2telegram.data.AuthType
import com.mail2telegram.mail.MicrosoftOAuth
import com.mail2telegram.security.Crypto
import java.util.concurrent.ConcurrentHashMap

class CredentialException(message: String) : Exception(message)

/** Resolves the IMAP secret: decrypted app password, or a (cached) OAuth2 access token. */
object Credentials {
    private data class Cached(val token: String, val expiresAt: Long, val refreshEnc: String)

    private val cache = ConcurrentHashMap<Long, Cached>()

    fun resolve(app: App, account: AccountEntity): String {
        val plain = try {
            Crypto.decrypt(account.secretEnc)
        } catch (e: Exception) {
            throw CredentialException("Cannot decrypt stored credentials – please re-enter them")
        }
        if (account.authType != AuthType.OAUTH2) return plain

        val now = System.currentTimeMillis()
        cache[account.id]?.let {
            if (it.refreshEnc == account.secretEnc && it.expiresAt - 120_000 > now) return it.token
        }
        val tokens = MicrosoftOAuth.refresh(app.http, app.settings.msClientId, app.settings.msTenant, plain)
        var refreshEnc = account.secretEnc
        if (tokens.refreshToken != null && tokens.refreshToken != plain && account.id > 0) {
            refreshEnc = Crypto.encrypt(tokens.refreshToken)
            app.db.accounts().updateSecret(account.id, refreshEnc)
        }
        if (account.id > 0) cache[account.id] = Cached(tokens.accessToken, tokens.expiresAtMillis, refreshEnc)
        return tokens.accessToken
    }

    fun forget(accountId: Long) {
        cache.remove(accountId)
    }
}
