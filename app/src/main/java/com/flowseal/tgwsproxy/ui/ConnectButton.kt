package com.flowseal.tgwsproxy.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings as AndroidSettings

@Composable
fun ConnectButton(
    visual: ConnectVisual,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColor = when (visual) {
        ConnectVisual.Connected -> AmneziaColors.Accent
        ConnectVisual.Connecting -> AmneziaColors.Text
        ConnectVisual.Disconnected -> AmneziaColors.Text
    }
    val label = when (visual) {
        ConnectVisual.Connected -> "STOP"
        ConnectVisual.Connecting -> "…"
        ConnectVisual.Disconnected -> "CONNECT"
    }
    val context = LocalContext.current
    val animatorScale = remember {
        runCatching {
            AndroidSettings.Global.getFloat(
                context.contentResolver,
                AndroidSettings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
    }
    val shouldSpin = visual == ConnectVisual.Connecting && animatorScale > 0f
    val spin = if (shouldSpin) {
        val transition = rememberInfiniteTransition(label = "connectSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "angle",
        )
        angle
    } else {
        0f
    }

    Box(
        modifier = modifier
            .size(190.dp)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(190.dp)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = if (visual == ConnectVisual.Connecting) {
                    AmneziaColors.Border.copy(alpha = 0.6f)
                } else {
                    ringColor.copy(alpha = if (enabled) 1f else 0.35f)
                },
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (visual == ConnectVisual.Connecting) {
                drawArc(
                    color = AmneziaColors.Text,
                    startAngle = spin - 90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = label,
            color = ringColor.copy(alpha = if (enabled) 1f else 0.35f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { /* keep label centered */ },
        )
    }
}
