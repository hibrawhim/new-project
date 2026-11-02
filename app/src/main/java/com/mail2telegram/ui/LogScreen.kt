package com.mail2telegram.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mail2telegram.App
import com.mail2telegram.data.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen() {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val logsFlow = remember { app.db.logs().observeRecent(500) }
    val logs by logsFlow.collectAsState(initial = emptyList())
    val queuedFlow = remember { app.db.outbox().observeCount() }
    val queued by queuedFlow.collectAsState(initial = 0)
    var filter by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("MMM d HH:mm:ss", Locale.getDefault()) }
    val shown = remember(logs, filter) { if (filter == null) logs else logs.filter { it.level == filter } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log") },
                actions = { TextButton(onClick = { scope.launch(Dispatchers.IO) { app.db.logs().clear() } }) { Text("Clear") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "$queued message(s) waiting to be sent to Telegram",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                FilterChip(selected = filter == LogLevel.FORWARD, onClick = { filter = LogLevel.FORWARD }, label = { Text("Forwards") })
                FilterChip(selected = filter == LogLevel.ERROR, onClick = { filter = LogLevel.ERROR }, label = { Text("Errors") })
                FilterChip(selected = filter == LogLevel.INFO, onClick = { filter = LogLevel.INFO }, label = { Text("Info") })
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                if (shown.isEmpty()) item { Text("Nothing here yet.", modifier = Modifier.padding(16.dp)) }
                items(shown, key = { it.id }) { e ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val color = when (e.level) {
                            LogLevel.ERROR -> StatusAuth
                            LogLevel.FORWARD -> StatusOk
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            "${fmt.format(Date(e.time))} · " + when (e.level) {
                                LogLevel.FORWARD -> "Forwarded"
                                LogLevel.ERROR -> "Error"
                                else -> "Info"
                            } + (e.account?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = color,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(e.message, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
