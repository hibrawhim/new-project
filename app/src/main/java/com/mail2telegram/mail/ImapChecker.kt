package com.mail2telegram.mail

import com.mail2telegram.data.AccountEntity
import com.mail2telegram.data.AuthType
import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.UIDFolder
import java.util.Properties

class ImapChecker {

    data class Outcome(
        val uidValidity: Long,
        val lastUid: Long,
        val items: List<MailItem>,
        /** true when a new baseline was set (first check, or UIDVALIDITY changed) */
        val baselined: Boolean,
        /** more new messages remain on the server than were processed this round */
        val hasMore: Boolean,
    )

    private fun connect(account: AccountEntity, secret: String): Store {
        val proto = if (account.ssl) "imaps" else "imap"
        val props = Properties().apply {
            put("mail.store.protocol", proto)
            put("mail.$proto.host", account.host)
            put("mail.$proto.port", account.port.toString())
            put("mail.$proto.connectiontimeout", "15000")
            put("mail.$proto.timeout", "20000")
            put("mail.$proto.ssl.checkserveridentity", "true")
            put("mail.$proto.peek", "true")
            put("mail.$proto.partialfetch", "true")
            put("mail.$proto.fetchsize", "65536")
            put("mail.mime.address.strict", "false")
            if (!account.ssl) {
                put("mail.imap.starttls.enable", "true")
            }
            if (account.authType == AuthType.OAUTH2) {
                put("mail.$proto.auth.mechanisms", "XOAUTH2")
                put("mail.$proto.auth.login.disable", "true")
                put("mail.$proto.auth.plain.disable", "true")
            }
        }
        val session = Session.getInstance(props)
        val store = session.getStore(proto)
        store.connect(account.host, account.port, account.email, secret)
        return store
    }

    /** Only verifies that login works and INBOX can be opened. */
    fun testLogin(account: AccountEntity, secret: String): Int {
        val store = connect(account, secret)
        try {
            val folder = store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            val count = folder.messageCount
            runCatching { folder.close(false) }
            return count
        } finally {
            runCatching { store.close() }
        }
    }

    fun check(account: AccountEntity, secret: String, maxMessages: Int = 30): Outcome {
        val store = connect(account, secret)
        try {
            val folder = store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            try {
                val uf = folder as UIDFolder
                val validity = uf.getUIDValidity()
                val uidNext = runCatching { uf.getUIDNext() }.getOrDefault(-1L)

                val prevValidity = account.uidValidity
                if (prevValidity == null || prevValidity != validity) {
                    // First check (or mailbox was recreated): remember the newest UID, forward nothing.
                    val last = if (uidNext > 0) {
                        uidNext - 1
                    } else {
                        val count = folder.messageCount
                        if (count > 0) uf.getUID(folder.getMessage(count)) else 0L
                    }
                    return Outcome(validity, last, emptyList(), baselined = true, hasMore = false)
                }

                val lastUid = account.lastUid
                if (uidNext > 0 && uidNext - 1 <= lastUid) {
                    return Outcome(validity, lastUid, emptyList(), baselined = false, hasMore = false)
                }

                val messages = uf.getMessagesByUID(lastUid + 1, UIDFolder.MAXUID).filterNotNull().toTypedArray()
                if (messages.isEmpty()) return Outcome(validity, lastUid, emptyList(), false, false)
                val fp = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(UIDFolder.FetchProfileItem.UID)
                }
                folder.fetch(messages, fp)

                val fresh = messages
                    .map { uf.getUID(it) to it }
                    .filter { it.first > lastUid }
                    .sortedBy { it.first }
                val batch = fresh.take(maxMessages)
                val items = batch.map { (uid, msg) ->
                    runCatching { MessageParser.parse(uid, msg) }.getOrElse {
                        MailItem(uid, "", "", "(could not read message)", "")
                    }
                }
                val newLast = batch.lastOrNull()?.first ?: lastUid
                return Outcome(validity, newLast, items, baselined = false, hasMore = fresh.size > batch.size)
            } finally {
                runCatching { folder.close(false) }
            }
        } finally {
            runCatching { store.close() }
        }
    }
}
