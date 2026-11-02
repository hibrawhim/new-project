package com.mail2telegram.mail

data class MailItem(
    val uid: Long,
    val fromName: String,
    val fromAddress: String,
    val subject: String,
    val body: String,
)
