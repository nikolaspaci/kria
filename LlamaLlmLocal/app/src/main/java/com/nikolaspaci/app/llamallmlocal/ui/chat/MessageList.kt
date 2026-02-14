package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.viewmodel.Stats

data class StreamingState(
    val currentText: String,
    val tokensGenerated: Int
)

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    streamingState: StreamingState?,
    lastMessageStats: Stats?,
    onCancelGeneration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streamingState?.currentText) {
        val targetIndex = messages.size + (if (streamingState != null) 1 else 0) - 1
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = messages,
            key = { _, message -> message.id }
        ) { index, message ->
            val isLastBotMessage = index == messages.lastIndex &&
                                   message.sender == Sender.BOT &&
                                   streamingState == null

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = if (message.sender == Sender.USER)
                    Arrangement.End else Arrangement.Start
            ) {
                MessageRow(
                    message = message,
                    stats = if (isLastBotMessage) lastMessageStats else null
                )
            }
        }

        if (streamingState != null) {
            item(key = "streaming") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    StreamingMessageBubble(
                        text = streamingState.currentText,
                        tokensGenerated = streamingState.tokensGenerated,
                        onCancel = onCancelGeneration
                    )
                }
            }
        }
    }
}
