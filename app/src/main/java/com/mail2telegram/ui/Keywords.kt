package com.mail2telegram.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mail2telegram.App
import com.mail2telegram.data.KeywordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun targetsText(k: KeywordEntity): String = buildList {
    if (k.matchSubject) add("subject")
    if (k.matchBody) add("body")
    if (k.matchSenderName) add("sender name")
    if (k.matchSenderAddress) add("sender address")
}.joinToString(", ").ifEmpty { "everywhere" }

/** List + editor for global keywords (accountId == null) or one account's keywords. */
@Composable
fun KeywordSection(accountId: Long?) {
    val dao = App.instance.db.keywords()
    val scope = rememberCoroutineScope()
    val flow = remember(accountId) { if (accountId == null) dao.observeGlobal() else dao.observeForAccount(accountId) }
    val keywords by flow.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<KeywordEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (keywords.isEmpty()) {
            Text(
                if (accountId == null) "No global keywords." else "No keywords for this account.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        keywords.forEach { k ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(k.text, style = MaterialTheme.typography.titleSmall)
                        Text(targetsText(k), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { editing = k }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { scope.launch(Dispatchers.IO) { dao.delete(k.id) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
        OutlinedButton(onClick = { adding = true }) { Text("Add keyword") }
    }

    val current = editing
    if (adding || current != null) {
        KeywordDialog(
            initial = current,
            onDismiss = { adding = false; editing = null },
            onSave = { items ->
                adding = false
                editing = null
                scope.launch(Dispatchers.IO) {
                    items.forEach { k ->
                        if (k.id == 0L) dao.insert(k.copy(accountId = accountId)) else dao.update(k)
                    }
                }
            },
        )
    }
}

@Composable
private fun KeywordDialog(initial: KeywordEntity?, onDismiss: () -> Unit, onSave: (List<KeywordEntity>) -> Unit) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var subject by remember { mutableStateOf(initial?.matchSubject ?: true) }
    var body by remember { mutableStateOf(initial?.matchBody ?: true) }
    var senderName by remember { mutableStateOf(initial?.matchSenderName ?: true) }
    var senderAddress by remember { mutableStateOf(initial?.matchSenderAddress ?: true) }
    val anyTarget = subject || body || senderName || senderAddress

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add keyword" else "Edit keyword") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Keyword") },
                    supportingText = {
                        Text(if (initial == null) "Case-insensitive, partial match. Separate several with commas." else "Case-insensitive, partial match.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Look in:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                CheckRow("Subject", subject) { subject = it }
                CheckRow("Body", body) { body = it }
                CheckRow("Sender name", senderName) { senderName = it }
                CheckRow("Sender address", senderAddress) { senderAddress = it }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && anyTarget, onClick = {
                val words = if (initial == null) {
                    text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                } else {
                    listOf(text.trim())
                }
                onSave(words.map { w ->
                    (initial ?: KeywordEntity(text = w)).copy(
                        text = w,
                        matchSubject = subject,
                        matchBody = body,
                        matchSenderName = senderName,
                        matchSenderAddress = senderAddress,
                    )
                })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
fun FiltersScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Filters") }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "An email is forwarded if ANY keyword matches. Global keywords apply to every account; " +
                    "per-account keywords (set in each account) are added on top. Matching ignores case and " +
                    "finds partial words: \"office\" matches \"Office\", \"OFFICE\", \"office1\", \"Office 2\".\n\n" +
                    "If there are no keywords at all for an account, every new email is forwarded.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Global keywords", style = MaterialTheme.typography.titleMedium)
            KeywordSection(accountId = null)
        }
    }
}
