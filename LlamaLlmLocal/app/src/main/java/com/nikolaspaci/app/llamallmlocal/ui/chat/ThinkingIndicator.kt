package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
fun ThinkingIndicator(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "thinking")

    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            PulsingDot(
                transition = transition,
                delayMs = index * 200,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PulsingDot(
    transition: InfiniteTransition,
    delayMs: Int,
    color: androidx.compose.ui.graphics.Color
) {
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at delayMs
                1.0f at delayMs + 300
                0.3f at delayMs + 600
                0.3f at 1200
            }
        ),
        label = "dot_alpha_$delayMs"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}
