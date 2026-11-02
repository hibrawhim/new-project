package com.mail2telegram.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mail2telegram.App
import com.mail2telegram.data.AccountEntity
import com.mail2telegram.data.AuthType
import com.mail2telegram.engine.Credentials
import com.mail2telegram.mail.ImapChecker
import com.mail2telegram.mail.MicrosoftOAuth
import com.mail2telegram.mail.Provider
import com.mail2telegram.security.Crypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountEditorScreen(accountId: Long, onDone: () -> Unit) {
    val app = App.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(accountId == 0L) }
    var existing by remember { mutableStateOf<AccountEntity?>(null) }
    var provider by remember { mutableStateOf(Provider.GMAIL) }
    var label by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("993") }
    var ssl by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(true) }
    var newRefreshToken by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(accountId) {
        if (accountId != 0L) {
            val a = withContext(Dispatchers.IO) { app.db.accounts().get(accountId) }
            if (a != null) {
                existing = a
                provider = Provider.of(a.provider)
                label = a.label
                email = a.email
                host = a.host
                port = a.port.toString()
                ssl = a.ssl
                enabled = a.enabled
            }
            loaded = true
        }
    }

    // Receive Microsoft sign-in results
    val oauthResult by MicrosoftOAuth.results.collectAsState()
    LaunchedEffect(oauthResult) {
        val r = oauthResult ?: return@LaunchedEffect
        MicrosoftOAuth.results.value = null
        if (r.error != null) {
            message = "Microsoft sign-in failed: ${r.error}"
        } else {
            newRefreshToken = r.refreshToken
            if (!r.email.isNullOrBlank()) {
                if (email.isBlank() || email.equals(r.email, ignoreCase = true)) email = r.email
                else message = "Signed in as ${r.email} (differs from the e-mail entered)"
            }
            if (message == null) message = "Signed in with Microsoft ✓ – tap Save"
        }
    }

    fun buildAccount(): Pair<AccountEntity?, String?> {
        val e = email.trim()
        if (!e.contains('@') && provider != Provider.CUSTOM) return null to "Enter a valid e-mail address"
        if (e.isEmpty()) return null to "Enter the login / e-mail"
        val h = if (provider == Provider.CUSTOM) host.trim() else provider.host
        val p = if (provider == Provider.CUSTOM) port.trim().toIntOrNull() else provider.port
        if (h.isEmpty()) return null to "Enter the IMAP host"
        if (p == null || p !in 1..65535) return null to "Enter a valid port"
        val auth = if (provider.oauth) AuthType.OAUTH2 else AuthType.PASSWORD
        val old = existing
        val secretEnc = when {
            provider.oauth && newRefreshToken != null -> Crypto.encrypt(newRefreshToken!!)
            provider.oauth && old?.authType == AuthType.OAUTH2 -> old!!.secretEnc
            provider.oauth -> return null to "Tap “Sign in with Microsoft” first"
            password.isNotEmpty() -> Crypto.encrypt(password.trim().let { if (provider == Provider.GMAIL) it.replace(" ", "") else it })
            old?.authType == AuthType.PASSWORD -> old!!.secretEnc
            else -> return null to "Enter the app password"
        }
        val base = old ?: AccountEntity(label = "", email = "", provider = "", host = "", port = 0, ssl = true, authType = auth, secretEnc = "")
        return base.copy(
            label = label.trim().ifEmpty { e },
            email = e,
            provider = provider.name,
            host = h,
            port = p,
            ssl = if (provider == Provider.CUSTOM) ssl else provider.ssl,
            authType = auth,
            secretEnc = secretEnc,
            enabled = enabled,
        ) to null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (accountId == 0L) "Add account" else "Edit account") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Provider", style = MaterialTheme.typography.titleSmall)
            Provider.entries.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = provider == p, onClick = {
                        provider = p
                        if (p != Provider.CUSTOM) { host = p.host; port = p.port.toString(); ssl = p.ssl }
                    }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = provider == p, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(p.displayName + if (p.oauth) "  (Microsoft sign-in)" else "")
                }
            }
            HorizontalDivider()

            OutlinedTextField(
                value = label, onValueChange = { label = it },
                label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text(if (provider == Provider.CUSTOM) "Login / e-mail" else "E-mail address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            if (provider.oauth) {
                val signedIn = newRefreshToken != null || existing?.authType == AuthType.OAUTH2
                Text(
                    if (signedIn) "Microsoft account linked." else "Outlook/Hotmail requires Microsoft sign-in (OAuth2).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = {
                    val clientId = app.settings.msClientId
                    if (clientId.isBlank()) {
                        message = "Set your Microsoft (Azure) client ID first in the Telegram tab → Microsoft sign-in. See README."
                    } else {
                        MicrosoftOAuth.start(context, clientId, app.settings.msTenant, email.trim().ifEmpty { null })
                    }
                }) { Text(if (signedIn) "Sign in again with Microsoft" else "Sign in with Microsoft") }
            } else {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(if (existing != null) "App password (leave empty to keep)" else "App password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (provider == Provider.CUSTOM) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text("IMAP host") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SSL/TLS (off = STARTTLS when offered)", modifier = Modifier.weight(1f))
                    Switch(checked = ssl, onCheckedChange = {
                        ssl = it
                        if (it && port == "143") port = "993" else if (!it && port == "993") port = "143"
                    })
                }
            } else {
                Text("Server: ${provider.host}:${provider.port} (SSL)", style = MaterialTheme.typography.bodySmall)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    val (acc, err) = buildAccount()
                    if (acc == null) { message = err; return@Button }
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val dao = app.db.accounts()
                            val old = existing
                            if (old == null) {
                                dao.insert(acc)
                            } else {
                                dao.updateConfig(
                                    acc.id, acc.label, acc.email, acc.provider, acc.host, acc.port,
                                    acc.ssl, acc.authType, acc.secretEnc, acc.enabled,
                                )
                                Credentials.forget(acc.id)
                                if (!old.email.equals(acc.email, true) || !old.host.equals(acc.host, true) || old.port != acc.port) {
                                    dao.resetBaseline(acc.id)
                                }
                            }
                        }
                        busy = false
                        onDone()
                    }
                }) { Text("Save") }

                OutlinedButton(enabled = !busy, onClick = {
                    val (acc, err) = buildAccount()
                    if (acc == null) { message = err; return@OutlinedButton }
                    busy = true
                    message = "Testing connection…"
                    scope.launch {
                        message = withContext(Dispatchers.IO) {
                            runCatching {
                                val secret = Credentials.resolve(app, acc.copy(id = -1))
                                val count = ImapChecker().testLogin(acc, secret)
                                "Connection OK ✓ ($count messages in INBOX)"
                            }.getOrElse { "Failed: ${it.message ?: it.javaClass.simpleName}" }
                        }
                        busy = false
                    }
                }) { Text("Test") }
            }

            if (existing != null) {
                OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { app.db.accounts().resetBaseline(accountId) } }) {
                    Text("Reset (skip all current mail)")
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete account", color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                Text("Keywords for this account", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Added on top of the global keywords. Leave both empty to forward everything.",
                    style = MaterialTheme.typography.bodySmall,
                )
                KeywordSection(accountId = accountId)
            } else {
                Text("Save the account to add per-account keywords.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account?") },
            text = { Text("${existing?.email} and its keywords will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            app.db.keywords().deleteForAccount(accountId)
                            app.db.accounts().delete(accountId)
                            Credentials.forget(accountId)
                        }
                        onDone()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
