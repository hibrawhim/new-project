package com.mail2telegram.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mail2telegram.App
import com.mail2telegram.engine.EngineState
import com.mail2telegram.engine.MailForwardService
import com.mail2telegram.mail.MicrosoftOAuth
import com.mail2telegram.telegram.TelegramClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TelegramScreen() {
    val app = App.instance
    val settings = app.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf(settings.botToken) }
    var chatId by remember { mutableStateOf(settings.chatId) }
    var showToken by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    var clientId by remember { mutableStateOf(settings.msClientId) }
    var tenant by remember { mutableStateOf(settings.msTenant) }
    var msStatus by remember { mutableStateOf<String?>(null) }

    var interval by remember { mutableStateOf(settings.pollIntervalSec.toString()) }
    var concurrency by remember { mutableStateOf(settings.maxConcurrency.toString()) }
    var engineStatus by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Telegram & settings") }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Telegram bot", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Create a bot with @BotFather and paste its token. For the chat ID, send your bot a message " +
                            "and open https://api.telegram.org/bot<TOKEN>/getUpdates – or use @userinfobot. " +
                            "Group IDs start with -100.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = token, onValueChange = { token = it.trim() },
                        label = { Text("Bot token") }, singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = chatId, onValueChange = { chatId = it.trim() },
                        label = { Text("Chat ID") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            settings.botToken = token
                            settings.chatId = chatId
                            app.outboxSignal.trySend(Unit)
                            status = "Saved ✓"
                        }) { Text("Save") }
                        OutlinedButton(enabled = !testing && token.isNotBlank() && chatId.isNotBlank(), onClick = {
                            testing = true
                            status = "Sending…"
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    TelegramClient(app.http).send(
                                        token, chatId,
                                        "✅ <b>Mail2Telegram</b> test message – your bot is configured correctly.",
                                    )
                                }
                                status = when (r) {
                                    TelegramClient.Result.Ok -> "Test message sent ✓"
                                    is TelegramClient.Result.RetryAfter -> "Rate limited – retry in ${r.seconds}s"
                                    is TelegramClient.Result.Failed -> "Error ${r.code}: ${r.description}"
                                    is TelegramClient.Result.NetworkError -> "Network error: ${r.message}"
                                }
                                testing = false
                            }
                        }) { Text("Send test message") }
                    }
                    OutlinedButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Hide token" else "Show token") }
                    status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Microsoft sign-in (Outlook / Hotmail)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Outlook needs OAuth2. Register a free app in the Azure portal (see README) with redirect URI " +
                            "${MicrosoftOAuth.REDIRECT_URI} and paste its Application (client) ID here.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = clientId, onValueChange = { clientId = it.trim() },
                        label = { Text("Application (client) ID") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = tenant, onValueChange = { tenant = it.trim() },
                        label = { Text("Tenant (common / consumers / organizations / tenant ID)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        settings.msClientId = clientId
                        settings.msTenant = tenant.ifBlank { "common" }
                        msStatus = "Saved ✓"
                    }) { Text("Save") }
                    msStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Engine", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = interval, onValueChange = { interval = it.filter(Char::isDigit) },
                        label = { Text("Check every (seconds, 15–3600)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = concurrency, onValueChange = { concurrency = it.filter(Char::isDigit) },
                        label = { Text("Concurrent connections (1–64)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        settings.pollIntervalSec = interval.toIntOrNull() ?: 60
                        settings.maxConcurrency = concurrency.toIntOrNull() ?: 25
                        interval = settings.pollIntervalSec.toString()
                        concurrency = settings.maxConcurrency.toString()
                        if (EngineState.running.value) {
                            MailForwardService.stop(context)
                            MailForwardService.start(context)
                        }
                        engineStatus = "Saved ✓"
                    }) { Text("Save") }
                    if (!isIgnoringBatteryOptimizations(context)) {
                        OutlinedButton(onClick = { requestBatteryExemption(context) }) { Text("Disable battery optimization") }
                    } else {
                        Text("Battery optimization: exempt ✓", style = MaterialTheme.typography.bodySmall)
                    }
                    engineStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}
