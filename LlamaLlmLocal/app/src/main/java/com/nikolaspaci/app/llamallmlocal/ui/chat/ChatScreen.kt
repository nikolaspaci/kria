package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.ui.common.AppTopAppBar
import com.nikolaspaci.app.llamallmlocal.ui.common.MessageInput
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatUiState
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToSettings: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedModelPath by remember { mutableStateOf("") }
    val viewModelModelPath by viewModel.currentModelPath.collectAsState()

    LaunchedEffect(viewModelModelPath) {
        viewModelModelPath?.let { path ->
            if (path.isNotEmpty()) {
                selectedModelPath = path
            }
        }
    }

    // Update selectedModelPath from uiState model name context
    val currentModelName = when (val state = uiState) {
        is ChatUiState.Ready -> state.modelName
        is ChatUiState.Generating -> state.modelName
        is ChatUiState.MessageComplete -> state.modelName
        is ChatUiState.ModelLoading -> state.modelName
        else -> ""
    }

    val isGenerating = uiState is ChatUiState.Generating
    val isModelReady = uiState is ChatUiState.Ready ||
                       uiState is ChatUiState.Generating ||
                       uiState is ChatUiState.MessageComplete

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            AppTopAppBar(
                title = "",
                onOpenDrawer = onOpenDrawer,
                onNavigateToSettings = {
                    onNavigateToSettings(selectedModelPath)
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(8.dp)) {
                MessageInput(
                    onSendMessage = { viewModel.sendMessage(it) },
                    isEnabled = isModelReady && !isGenerating
                )
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is ChatUiState.Idle -> {
                // Empty initial state
            }

            is ChatUiState.ModelLoading -> {
                LoadingScreen(
                    modelName = state.modelName,
                    progress = state.progress,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is ChatUiState.Ready -> {
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    ModelNameHeader(currentModelName)
                    MessageList(
                        messages = state.messages,
                        streamingState = null,
                        lastMessageStats = null,
                        onCancelGeneration = {},
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }

            is ChatUiState.Generating -> {
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    ModelNameHeader(currentModelName)
                    MessageList(
                        messages = state.messages,
                        streamingState = StreamingState(
                            currentText = state.currentResponse,
                            tokensGenerated = state.tokensGenerated
                        ),
                        lastMessageStats = null,
                        onCancelGeneration = { viewModel.cancelPrediction() },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }

            is ChatUiState.MessageComplete -> {
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    ModelNameHeader(currentModelName)
                    MessageList(
                        messages = state.messages,
                        streamingState = null,
                        lastMessageStats = state.stats,
                        onCancelGeneration = {},
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }

            is ChatUiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = if (state.canRetry) {{ viewModel.retry() }} else null,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun ModelNameHeader(modelName: String) {
    if (modelName.isNotEmpty()) {
        Text(
            text = File(modelName).name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}
