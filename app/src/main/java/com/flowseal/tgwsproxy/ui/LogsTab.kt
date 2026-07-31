package com.flowseal.tgwsproxy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun LogsTab(
    state: UiState,
    showLogs: Boolean,
    onShowLogsChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Logs", style = MaterialTheme.typography.headlineMedium, color = AmneziaColors.Text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show logs", color = AmneziaColors.Text, modifier = Modifier.weight(1f))
                AmneziaSwitch(checked = showLogs, onCheckedChange = onShowLogsChange)
            }
            if (showLogs) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClear) { Text("Clear", color = AmneziaColors.Text) }
                    OutlinedButton(onClick = onExport) { Text("Save", color = AmneziaColors.Text) }
                    OutlinedButton(onClick = onShare) { Text("Share", color = AmneziaColors.Text) }
                }
                Text(
                    text = state.logTail.ifBlank { "No logs yet" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = AmneziaColors.Text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scroll),
                )
            } else {
                Text(
                    "Logs are off. Enable the switch to stream AppLog.",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmneziaColors.Muted,
                )
            }
        }
        if (showLogs && scroll.value > 240) {
            FloatingActionButton(
                onClick = { scope.launch { scroll.animateScrollTo(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = AmneziaColors.Accent,
                contentColor = AmneziaColors.ButtonOn,
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to top")
            }
        }
    }
}
