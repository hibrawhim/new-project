package com.mail2telegram.data

import android.content.Context
import com.mail2telegram.security.Crypto

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Volatile
    private var tokenCache: String? = null

    var botToken: String
        get() {
            tokenCache?.let { return it }
            val enc = prefs.getString(KEY_TOKEN, null) ?: return ""
            val plain = runCatching { Crypto.decrypt(enc) }.getOrDefault("")
            tokenCache = plain
            return plain
        }
        set(value) {
            val v = value.trim()
            prefs.edit().putString(KEY_TOKEN, if (v.isEmpty()) null else Crypto.encrypt(v)).apply()
            tokenCache = v
        }

    var chatId: String
        get() = prefs.getString("chat_id", "") ?: ""
        set(value) = prefs.edit().putString("chat_id", value.trim()).apply()

    var msClientId: String
        get() = prefs.getString("ms_client_id", "") ?: ""
        set(value) = prefs.edit().putString("ms_client_id", value.trim()).apply()

    var msTenant: String
        get() = prefs.getString("ms_tenant", "common")?.ifBlank { "common" } ?: "common"
        set(value) = prefs.edit().putString("ms_tenant", value.trim()).apply()

    var pollIntervalSec: Int
        get() = prefs.getInt("poll_interval", 60).coerceIn(15, 3600)
        set(value) = prefs.edit().putInt("poll_interval", value.coerceIn(15, 3600)).apply()

    var maxConcurrency: Int
        get() = prefs.getInt("concurrency", 25).coerceIn(1, 64)
        set(value) = prefs.edit().putInt("concurrency", value.coerceIn(1, 64)).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", false)
        set(value) = prefs.edit().putBoolean("service_enabled", value).apply()

    var batteryPromptShown: Boolean
        get() = prefs.getBoolean("battery_prompt_shown", false)
        set(value) = prefs.edit().putBoolean("battery_prompt_shown", value).apply()

    companion object {
        private const val KEY_TOKEN = "bot_token_enc"
    }
}
