package com.mail2telegram.mail

import com.mail2telegram.data.KeywordEntity

/**
 * Case-insensitive substring matching: "office" matches "Office", "OFFICE", "office1", "Office 2".
 * No (usable) keywords -> everything matches.
 */
object KeywordMatcher {
    fun matches(mail: MailItem, keywords: List<KeywordEntity>): Boolean {
        val active = keywords.filter { it.text.trim().isNotEmpty() }
        if (active.isEmpty()) return true
        val subject = mail.subject.lowercase()
        val body = mail.body.lowercase()
        val name = mail.fromName.lowercase()
        val address = mail.fromAddress.lowercase()
        return active.any { k ->
            val needle = k.text.trim().lowercase()
            val none = !k.matchSubject && !k.matchBody && !k.matchSenderName && !k.matchSenderAddress
            ((k.matchSubject || none) && subject.contains(needle)) ||
                ((k.matchBody || none) && body.contains(needle)) ||
                ((k.matchSenderName || none) && name.contains(needle)) ||
                ((k.matchSenderAddress || none) && address.contains(needle))
        }
    }
}
