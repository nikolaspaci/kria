package com.nikolaspaci.app.llamallmlocal.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

private enum class InputState { EMPTY, HAS_TEXT, GENERATING }

@Composable
fun SmartChatInput(
    onSendMessage: (String) -> Unit,
    isEnabled: Boolean,
    isGenerating: Boolean,
    onStopGeneration: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }

    val inputState = when {
        isGenerating -> InputState.GENERATING
        text.isNotBlank() -> InputState.HAS_TEXT
        else -> InputState.EMPTY
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 5,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }

            Crossfade(
                targetState = inputState,
                animationSpec = tween(200),
                label = "input_action"
            ) { state ->
                when (state) {
                    InputState.EMPTY -> {
                        IconButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    InputState.HAS_TEXT -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                            onClick = {
                                if (text.isNotBlank() && isEnabled) {
                                    onSendMessage(text)
                                    text = ""
                                }
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    InputState.GENERATING -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                            onClick = onStopGeneration
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Stop generation",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Keep old API for backwards compatibility with HomeChatScreen
@Composable
fun MessageInput(
    onSendMessage: (String) -> Unit,
    isEnabled: Boolean
) {
    SmartChatInput(
        onSendMessage = onSendMessage,
        isEnabled = isEnabled,
        isGenerating = false,
        onStopGeneration = {}
    )
}
