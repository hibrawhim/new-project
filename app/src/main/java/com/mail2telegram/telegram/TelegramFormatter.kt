package com.mail2telegram.telegram

import com.mail2telegram.data.AccountEntity
import com.mail2telegram.mail.MailItem

object TelegramFormatter {
    private const val PREVIEW = 1500

    fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun format(account: AccountEntity, mail: MailItem): String {
        val sender = when {
            mail.fromName.isNotBlank() && mail.fromAddress.isNotBlank() &&
                !mail.fromName.equals(mail.fromAddress, ignoreCase = true) -> "${mail.fromName} <${mail.fromAddress}>"
            else -> mail.fromName.ifBlank { mail.fromAddress }.ifBlank { "(unknown sender)" }
        }
        val accountText = if (account.label.isBlank() || account.label.equals(account.email, ignoreCase = true)) {
            account.email
        } else {
            "${account.label} (${account.email})"
        }
        var preview = mail.body.trim().let { if (it.length > PREVIEW) it.take(PREVIEW).trimEnd() + "…" else it }
        // Keep well under Telegram's 4096-char limit after HTML escaping.
        while (escape(preview).length > 3300) preview = preview.take(preview.length * 3 / 4) + "…"

        return buildString {
            append("📧 <b>From:</b> ").append(escape(sender.take(300))).append('\n')
            append("📥 <b>Account:</b> ").append(escape(accountText.take(200))).append('\n')
            append("📝 <b>Subject:</b> ").append(escape(mail.subject.take(300)))
            if (preview.isNotEmpty()) append("\n\n").append(escape(preview))
        }
    }

    /** Plain-text fallback if Telegram rejects the HTML. */
    fun toPlain(html: String): String =
        html.replace(Regex("<[^>]+>"), "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
