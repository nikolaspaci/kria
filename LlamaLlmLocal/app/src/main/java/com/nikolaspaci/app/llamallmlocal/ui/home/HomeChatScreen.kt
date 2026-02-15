package com.nikolaspaci.app.llamallmlocal.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.ui.common.AdaptiveTopBar
import com.nikolaspaci.app.llamallmlocal.ui.common.SmartChatInput
import com.nikolaspaci.app.llamallmlocal.ui.common.ModelSelector
import com.nikolaspaci.app.llamallmlocal.viewmodel.HomeViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeChatScreen(
    homeViewModel: HomeViewModel,
    modelFileViewModel: ModelFileViewModel,
    onStartChat: (Long) -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateToHuggingFace: () -> Unit = {}
) {
    var selectedModelPath by remember { mutableStateOf(modelFileViewModel.getModelPath() ?: "") }
    val scope = rememberCoroutineScope()

    val displayModelName = if (selectedModelPath.isNotEmpty()) {
        File(selectedModelPath).nameWithoutExtension
    } else ""

    Scaffold(
        topBar = {
            AdaptiveTopBar(
                modelName = displayModelName,
                onOpenDrawer = onOpenDrawer
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "What are you looking for today?",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SmartChatInput(
                onSendMessage = { userInput ->
                    if (selectedModelPath.isNotEmpty()) {
                        scope.launch {
                            val newConversationId = homeViewModel.startNewConversation(selectedModelPath, userInput)
                            onStartChat(newConversationId)
                        }
                    }
                },
                isEnabled = selectedModelPath.isNotEmpty(),
                isGenerating = false,
                onStopGeneration = {},
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            ModelSelector(
                modelFileViewModel = modelFileViewModel,
                selectedModelPath = selectedModelPath,
                onModelSelected = {
                    selectedModelPath = it
                    modelFileViewModel.saveModelPath(it)
                },
                onDownloadFromHuggingFace = onNavigateToHuggingFace,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
