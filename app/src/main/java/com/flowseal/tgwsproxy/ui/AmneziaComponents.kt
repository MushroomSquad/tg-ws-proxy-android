package com.flowseal.tgwsproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppTab { Home, Settings, Logs }

@Composable
fun AmneziaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AmneziaColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AmneziaColors.Border),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@Composable
fun AmneziaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AmneziaColors.ButtonFill,
            contentColor = AmneziaColors.ButtonOn,
            disabledContainerColor = AmneziaColors.Border,
            disabledContentColor = AmneziaColors.Muted,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AmneziaTabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AmneziaColors.Border),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AmneziaColors.Surface)
                .padding(vertical = 8.dp, horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TabIcon(
                selected = selected == AppTab.Home,
                onClick = { onSelect(AppTab.Home) },
            ) { c -> Icon(Icons.Filled.Home, contentDescription = "Home", tint = c, modifier = Modifier) }
            TabIcon(
                selected = selected == AppTab.Settings,
                onClick = { onSelect(AppTab.Settings) },
            ) { c -> Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = c) }
            TabIcon(
                selected = selected == AppTab.Logs,
                onClick = { onSelect(AppTab.Logs) },
            ) { c -> Icon(Icons.Filled.Article, contentDescription = "Logs", tint = c) }
        }
    }
}

@Composable
private fun TabIcon(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val color = if (selected) AmneziaColors.Accent else AmneziaColors.Text
    IconButton(onClick = onClick) { icon(color) }
}
