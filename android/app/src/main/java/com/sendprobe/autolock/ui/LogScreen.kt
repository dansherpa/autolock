package com.sendprobe.autolock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sendprobe.autolock.model.LogEntry
import com.sendprobe.autolock.storage.LogStore
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val logStore = remember { LogStore.get(context) }
    var entries by remember { mutableStateOf(logStore.all()) }
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            logStore.clear()
                            entries = emptyList()
                        },
                        enabled = entries.isNotEmpty()
                    ) { Text("Clear") }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text("No log entries yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    LogEntryRow(entry, formatter)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry, formatter: DateFormat) {
    Column {
        Text(entry.event, style = MaterialTheme.typography.bodyLarge)
        if (entry.detail.isNotEmpty()) {
            Text(entry.detail, style = MaterialTheme.typography.bodySmall)
        }
        Text(formatter.format(Date(entry.timestamp)), style = MaterialTheme.typography.labelSmall)
    }
}
