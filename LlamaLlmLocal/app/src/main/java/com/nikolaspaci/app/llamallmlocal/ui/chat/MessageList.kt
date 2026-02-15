package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            if (lastVisible == null || totalItems == 0) {
                true
            } else if (lastVisible.index < totalItems - 2) {
                false
            } else {
                val viewportEnd = layoutInfo.viewportEndOffset
                val lastItemEnd = lastVisible.offset + lastVisible.size
                lastItemEnd <= viewportEnd + 100
            }
        }
    }

    // Auto-scroll on new message (always)
    LaunchedEffect(messages.size) {
        kotlinx.coroutines.delay(50)
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    // Auto-scroll during streaming (only if user is at bottom, instant scroll)
    LaunchedEffect(streamingState?.currentText) {
        if (streamingState != null && isAtBottom) {
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastIndex >= 0) {
                listState.scrollToItem(lastIndex)
            }
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

        item(key = "bottom_anchor") {
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}
