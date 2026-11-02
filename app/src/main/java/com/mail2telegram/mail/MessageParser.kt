package com.mail2telegram.mail

import android.text.Html
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.InternetAddress
import java.io.InputStream

object MessageParser {
    private const val MAX_BODY = 100_000

    fun parse(uid: Long, message: Message): MailItem {
        val from = runCatching { message.from?.firstOrNull() }.getOrNull()
        val name: String
        val address: String
        if (from is InternetAddress) {
            name = from.personal.orEmpty()
            address = from.address.orEmpty()
        } else {
            name = ""
            address = from?.toString().orEmpty()
        }
        val subject = runCatching { message.subject }.getOrNull()?.trim().orEmpty().ifEmpty { "(no subject)" }
        val body = runCatching { extractText(message, 0) }.getOrNull().orEmpty()
        return MailItem(uid, name.trim(), address.trim(), subject, normalize(body).take(MAX_BODY))
    }

    private fun isAttachment(p: Part): Boolean =
        runCatching { Part.ATTACHMENT.equals(p.disposition, ignoreCase = true) }.getOrDefault(false)

    private fun extractText(p: Part, depth: Int): String? {
        if (depth > 12) return null
        return when {
            p.isMimeType("text/plain") && !isAttachment(p) -> contentString(p)
            p.isMimeType("text/html") && !isAttachment(p) -> htmlToText(contentString(p))
            p.isMimeType("multipart/alternative") -> {
                val mp = p.content as? Multipart ?: return null
                var fallback: String? = null
                for (i in 0 until mp.count) {
                    val bp = mp.getBodyPart(i)
                    if (bp.isMimeType("text/plain")) {
                        val t = contentString(bp)
                        if (t.isNotBlank()) return t
                    } else if (fallback == null) {
                        fallback = extractText(bp, depth + 1)
                    }
                }
                fallback
            }
            p.isMimeType("multipart/*") -> {
                val mp = p.content as? Multipart ?: return null
                for (i in 0 until mp.count) {
                    val t = extractText(mp.getBodyPart(i), depth + 1)
                    if (!t.isNullOrBlank()) return t
                }
                null
            }
            p.isMimeType("message/rfc822") -> (p.content as? Part)?.let { extractText(it, depth + 1) }
            else -> null
        }
    }

    private fun contentString(p: Part): String = when (val c = p.content) {
        is String -> c
        is InputStream -> c.use { it.readNBytesCompat(MAX_BODY * 2).toString(Charsets.UTF_8) }
        else -> c?.toString().orEmpty()
    }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - total))
            if (n <= 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private val dropBlocks = Regex("(?is)<(style|script|head|title)\\b[^>]*>.*?</\\1\\s*>")
    private val comments = Regex("(?s)<!--.*?-->")

    fun htmlToText(html: String): String {
        val cleaned = html.replace(dropBlocks, " ").replace(comments, " ")
        @Suppress("DEPRECATION")
        val spanned = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY)
        return spanned.toString()
    }

    private val spaces = Regex("[ \\t\\u00A0\\u200B\\u200C\\u200D\\uFEFF]+")
    private val blankLines = Regex("\\n{3,}")

    fun normalize(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')
            .replace("￼", "")
            .replace(spaces, " ")
            .lines().joinToString("\n") { it.trim() }
            .replace(blankLines, "\n\n")
            .trim()
}
