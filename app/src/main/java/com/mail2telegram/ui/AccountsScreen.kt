package com.mail2telegram.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mail2telegram.App
import com.mail2telegram.data.AccountEntity
import com.mail2telegram.data.AccountStatus
import com.mail2telegram.data.AuthType
import com.mail2telegram.engine.EngineState
import com.mail2telegram.engine.MailForwardService
import com.mail2telegram.mail.Provider
import com.mail2telegram.security.Crypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun statusColor(status: String): Color = when (status) {
    AccountStatus.OK -> StatusOk
    AccountStatus.AUTH_ERROR -> StatusAuth
    AccountStatus.CONNECTION_ERROR -> StatusConn
    else -> StatusPending
}

fun statusLabel(status: String): String = when (status) {
    AccountStatus.OK -> "OK"
    AccountStatus.AUTH_ERROR -> "Auth error"
    AccountStatus.CONNECTION_ERROR -> "Connection error"
    else -> "Pending"
}

@Composable
fun AccountsScreen(onOpen: (Long) -> Unit) {
    val app = App.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountsFlow = remember { app.db.accounts().observeAll() }
    val accounts by accountsFlow.collectAsState(initial = emptyList())
    val running by EngineState.running.collectAsState()
    val paused by EngineState.paused.collectAsState()
    var query by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(10_000)
            value = System.currentTimeMillis()
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun startService() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        app.settings.serviceEnabled = true
        MailForwardService.start(context)
    }

    val filtered = remember(accounts, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) accounts else accounts.filter {
            it.email.lowercase().contains(q) || it.label.lowercase().contains(q) || statusLabel(it.status).lowercase().contains(q)
        }
    }
    val okCount = accounts.count { it.enabled && it.status == AccountStatus.OK }
    val errCount = accounts.count { it.enabled && (it.status == AccountStatus.AUTH_ERROR || it.status == AccountStatus.CONNECTION_ERROR) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts (${accounts.size})") },
                actions = {
                    IconButton(onClick = { scope.launch(Dispatchers.IO) { app.db.accounts().checkAllNow() } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Check all now")
                    }
                    TextButton(onClick = { showImport = true }) { Text("Import") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpen(0L) }) { Icon(Icons.Filled.Add, contentDescription = "Add account") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Forwarding service", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when {
                                        !running -> "Stopped"
                                        paused -> "Running – paused (offline)"
                                        else -> "Running"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(checked = running, onCheckedChange = { on ->
                                if (on) startService() else {
                                    app.settings.serviceEnabled = false
                                    MailForwardService.stop(context)
                                }
                            })
                        }
                        Text(
                            "${accounts.size} accounts · $okCount OK · $errCount errors",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (!isIgnoringBatteryOptimizations(context) && now > 0) {
                            OutlinedButton(onClick = { requestBatteryExemption(context) }, modifier = Modifier.padding(top = 8.dp)) {
                                Text("Disable battery optimization")
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search accounts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (accounts.isEmpty()) {
                item {
                    Text(
                        "No accounts yet. Tap + to add one, or Import to paste many at once.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(filtered, key = { it.id }) { acc -> AccountRow(acc, now) { onOpen(acc.id) } }
        }
    }

    if (showImport) ImportDialog(onDismiss = { showImport = false })
}

@Composable
private fun AccountRow(acc: AccountEntity, now: Long, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        acc.label.ifBlank { acc.email },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (acc.label.isNotBlank() && acc.label != acc.email) {
                        Text(acc.email, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                val color = if (acc.enabled) statusColor(acc.status) else StatusPending
                Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(
                        if (acc.enabled) statusLabel(acc.status) else "Disabled",
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                "${Provider.of(acc.provider).displayName} · " + if (acc.lastChecked == 0L) "never checked" else
                    "checked " + DateUtils.getRelativeTimeSpanString(acc.lastChecked, maxOf(now, acc.lastChecked), DateUtils.SECOND_IN_MILLIS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!acc.lastError.isNullOrBlank() && acc.status != AccountStatus.OK) {
                Text(
                    acc.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(acc.status),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Parses "email,password[,host[,port]]" or "email:password" lines. */
private fun parseImport(text: String, existing: Set<String>): Pair<List<AccountEntity>, List<String>> {
    val ok = mutableListOf<AccountEntity>()
    val problems = mutableListOf<String>()
    val seen = existing.map { it.lowercase() }.toMutableSet()
    text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEachIndexed { i, line ->
        val parts = if (line.contains(',') || line.contains(';') || line.contains('\t')) {
            line.split(',', ';', '\t').map { it.trim() }
        } else {
            val idx = line.indexOf(':')
            if (idx > 0) listOf(line.substring(0, idx).trim(), line.substring(idx + 1).trim()) else listOf(line)
        }
        val email = parts.getOrNull(0).orEmpty()
        val password = parts.getOrNull(1).orEmpty()
        if (!email.contains('@') || password.isEmpty()) {
            problems += "Line ${i + 1}: expected email,password"
            return@forEachIndexed
        }
        if (!seen.add(email.lowercase())) {
            problems += "Line ${i + 1}: $email already exists"
            return@forEachIndexed
        }
        val customHost = parts.getOrNull(2).orEmpty()
        val provider = if (customHost.isNotEmpty()) Provider.CUSTOM else Provider.detect(email)
        when {
            provider == null -> problems += "Line ${i + 1}: unknown provider for $email – add host (email,password,host,port)"
            provider.oauth -> problems += "Line ${i + 1}: $email is Outlook – needs Microsoft sign-in, add it manually"
            else -> {
                val port = parts.getOrNull(3)?.toIntOrNull() ?: 993
                ok += AccountEntity(
                    label = email,
                    email = email,
                    provider = provider.name,
                    host = if (provider == Provider.CUSTOM) customHost else provider.host,
                    port = if (provider == Provider.CUSTOM) port else provider.port,
                    ssl = if (provider == Provider.CUSTOM) port != 143 else provider.ssl,
                    authType = AuthType.PASSWORD,
                    secretEnc = Crypto.encrypt(password),
                )
            }
        }
    }
    return ok to problems
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk import") },
        text = {
            Column {
                Text(
                    "One account per line:\nemail,app-password\nemail,password,imap.host.com,993\n" +
                        "Gmail, Yahoo and inbox.eu are detected automatically. Outlook accounts must be added manually (Microsoft sign-in).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp).padding(top = 8.dp),
                    placeholder = { Text("me@gmail.com,abcd efgh ijkl mnop") },
                )
                result?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && text.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    val msg = withContext(Dispatchers.IO) {
                        val (list, problems) = parseImport(text, app.db.accounts().allEmails().toSet())
                        if (list.isNotEmpty()) app.db.accounts().insertAll(list)
                        buildString {
                            append("Imported ${list.size} account(s).")
                            if (problems.isNotEmpty()) append("\n" + problems.take(15).joinToString("\n"))
                            if (problems.size > 15) append("\n… and ${problems.size - 15} more")
                        }
                    }
                    result = msg
                    text = ""
                    busy = false
                }
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
