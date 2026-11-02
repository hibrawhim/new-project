package com.mail2telegram.telegram

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class TelegramClient(private val http: OkHttpClient) {

    sealed interface Result {
        data object Ok : Result
        data class RetryAfter(val seconds: Int) : Result
        data class Failed(val code: Int, val description: String) : Result
        data class NetworkError(val message: String) : Result
    }

    fun send(token: String, chatId: String, text: String, html: Boolean = true): Result {
        return try {
            val form = FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .add("disable_web_page_preview", "true")
                .apply { if (html) add("parse_mode", "HTML") }
                .build()
            val req = Request.Builder().url("https://api.telegram.org/bot$token/sendMessage").post(form).build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (resp.isSuccessful && json?.optBoolean("ok") == true) {
                    Result.Ok
                } else {
                    val code = json?.optInt("error_code", resp.code) ?: resp.code
                    val desc = json?.optString("description")?.ifEmpty { null } ?: resp.message.ifEmpty { "HTTP $code" }
                    if (code == 429) {
                        Result.RetryAfter(json?.optJSONObject("parameters")?.optInt("retry_after", 5) ?: 5)
                    } else {
                        Result.Failed(code, desc)
                    }
                }
            }
        } catch (e: IOException) {
            Result.NetworkError(e.message ?: e.javaClass.simpleName)
        } catch (e: IllegalArgumentException) {
            Result.Failed(400, "Invalid bot token format")
        }
    }
}
