package com.flowseal.tgwsproxy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AmneziaDark = darkColorScheme(
    primary = AmneziaColors.Accent,
    onPrimary = AmneziaColors.ButtonOn,
    secondary = AmneziaColors.Muted,
    background = AmneziaColors.Bg,
    surface = AmneziaColors.Surface,
    onBackground = AmneziaColors.Text,
    onSurface = AmneziaColors.Text,
    error = AmneziaColors.Error,
    outline = AmneziaColors.Border,
)

@Composable
fun TgWsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmneziaDark,
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
            headlineMedium = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Bold),
            bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
            labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
            labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}
