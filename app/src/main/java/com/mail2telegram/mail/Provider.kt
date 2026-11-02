package com.mail2telegram.mail

enum class Provider(
    val displayName: String,
    val host: String,
    val port: Int,
    val ssl: Boolean,
    val oauth: Boolean,
) {
    GMAIL("Gmail", "imap.gmail.com", 993, true, false),
    YAHOO("Yahoo", "imap.mail.yahoo.com", 993, true, false),
    OUTLOOK("Outlook / Hotmail", "outlook.office365.com", 993, true, true),
    INBOX_EU("inbox.eu", "mail.inbox.eu", 993, true, false),
    CUSTOM("Custom IMAP", "", 993, true, false);

    companion object {
        fun of(name: String): Provider = entries.firstOrNull { it.name == name } ?: CUSTOM

        fun detect(email: String): Provider? {
            val domain = email.substringAfterLast('@', "").lowercase().trim()
            return when {
                domain == "gmail.com" || domain == "googlemail.com" -> GMAIL
                domain.startsWith("yahoo.") || domain == "ymail.com" || domain == "rocketmail.com" -> YAHOO
                domain.startsWith("outlook.") || domain.startsWith("hotmail.") ||
                    domain.startsWith("live.") || domain == "msn.com" -> OUTLOOK
                domain == "inbox.eu" -> INBOX_EU
                else -> null
            }
        }
    }
}
