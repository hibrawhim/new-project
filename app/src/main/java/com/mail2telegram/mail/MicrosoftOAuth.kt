package com.mail2telegram.mail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

class OAuthException(message: String, val invalidGrant: Boolean = false) : Exception(message)

/**
 * Microsoft identity platform (v2.0) authorization-code flow with PKCE, for IMAP XOAUTH2.
 * Requires the user's own Azure app registration (public client, redirect URI mail2telegram://auth).
 */
object MicrosoftOAuth {
    const val REDIRECT_URI = "mail2telegram://auth"
    private const val SCOPES = "offline_access openid email https://outlook.office.com/IMAP.AccessAsUser.All"

    data class SignInResult(val email: String?, val refreshToken: String?, val error: String?)
    data class Tokens(val accessToken: String, val refreshToken: String?, val expiresAtMillis: Long)

    /** Last sign-in result; the account editor consumes and clears it. */
    val results = MutableStateFlow<SignInResult?>(null)

    private fun base(tenant: String) = "https://login.microsoftonline.com/${tenant.ifBlank { "common" }}/oauth2/v2.0"

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun start(context: Context, clientId: String, tenant: String, loginHint: String?) {
        val verifier = randomUrlSafe(48)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val state = randomUrlSafe(16)
        context.getSharedPreferences("oauth", Context.MODE_PRIVATE).edit()
            .putString("verifier", verifier)
            .putString("state", state)
            .putString("client_id", clientId)
            .putString("tenant", tenant)
            .apply()
        val uri = Uri.parse("${base(tenant)}/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "select_account")
            .apply { if (!loginHint.isNullOrBlank()) appendQueryParameter("login_hint", loginHint) }
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun handleRedirect(context: Context, http: OkHttpClient, uri: Uri) {
        val prefs = context.getSharedPreferences("oauth", Context.MODE_PRIVATE)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                uri.getQueryParameter("error")?.let { err ->
                    val desc = uri.getQueryParameter("error_description").orEmpty()
                    return@runCatching SignInResult(null, null, "$err: $desc")
                }
                val code = uri.getQueryParameter("code") ?: throw OAuthException("No authorization code returned")
                if (uri.getQueryParameter("state") != prefs.getString("state", null)) {
                    throw OAuthException("State mismatch – please try again")
                }
                val clientId = prefs.getString("client_id", null) ?: throw OAuthException("Missing client id")
                val tenant = prefs.getString("tenant", "common") ?: "common"
                val form = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("code_verifier", prefs.getString("verifier", "") ?: "")
                    .add("scope", SCOPES)
                    .build()
                val json = post(http, "${base(tenant)}/token", form)
                val refresh = json.optString("refresh_token").ifEmpty { throw OAuthException("No refresh token returned") }
                val email = json.optString("id_token").takeIf { it.isNotEmpty() }?.let { emailFromIdToken(it) }
                SignInResult(email, refresh, null)
            }.getOrElse { SignInResult(null, null, it.message ?: it.javaClass.simpleName) }
        }
        prefs.edit().clear().apply()
        results.value = result
    }

    /** Blocking. Exchanges a refresh token for a fresh access token. */
    fun refresh(http: OkHttpClient, clientId: String, tenant: String, refreshToken: String): Tokens {
        if (clientId.isBlank()) throw OAuthException("Microsoft client ID not set (Telegram tab → Microsoft sign-in)")
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("scope", SCOPES)
            .build()
        val json = post(http, "${base(tenant)}/token", form)
        val access = json.optString("access_token").ifEmpty { throw OAuthException("No access token returned") }
        val expiresIn = json.optLong("expires_in", 3600)
        return Tokens(access, json.optString("refresh_token").ifEmpty { null }, System.currentTimeMillis() + expiresIn * 1000)
    }

    private fun post(http: OkHttpClient, url: String, form: FormBody): JSONObject {
        val req = Request.Builder().url(url).post(form).build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (!resp.isSuccessful) {
                val err = json.optString("error", "http_${resp.code}")
                val desc = json.optString("error_description").lineSequence().firstOrNull().orEmpty()
                throw OAuthException("$err: $desc".take(300), invalidGrant = err == "invalid_grant")
            }
            return json
        }
    }

    private fun emailFromIdToken(idToken: String): String? = runCatching {
        val payload = idToken.split('.')[1]
        val json = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
        json.optString("email").ifEmpty { json.optString("preferred_username") }.ifEmpty { null }
    }.getOrNull()
}
