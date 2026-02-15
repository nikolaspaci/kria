# Phase 2: Streaming & UI de Base

## Objectif
Streaming fluide des tokens avec UI réactive et gestion d'états claire.

---

## 1. Enrichir PredictionEvent

**Fichier à modifier**: `jni/PredictionEvent.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.jni

sealed class PredictionEvent {
    data class Token(
        val value: String,
        val tokenIndex: Int = 0
    ) : PredictionEvent()

    data class Progress(
        val tokensGenerated: Int,
        val estimatedTotal: Int? = null
    ) : PredictionEvent()

    data class Completion(
        val tokensPerSecond: Double,
        val durationInSeconds: Long,
        val totalTokens: Int = 0
    ) : PredictionEvent()

    data class Error(
        val message: String,
        val isRecoverable: Boolean = false
    ) : PredictionEvent()
}
```

---

## 2. Nouveau ChatUiState

**Fichier à modifier**: `viewmodel/ChatViewModel.kt`

```kotlin
sealed class ChatUiState {
    object Idle : ChatUiState()

    data class ModelLoading(
        val progress: Float = 0f,
        val modelName: String = ""
    ) : ChatUiState()

    data class Ready(
        val messages: List<ChatMessage>,
        val modelName: String
    ) : ChatUiState()

    data class Generating(
        val messages: List<ChatMessage>,
        val currentResponse: String,
        val tokensGenerated: Int,
        val modelName: String
    ) : ChatUiState()

    data class MessageComplete(
        val messages: List<ChatMessage>,
        val stats: Stats,
        val modelName: String
    ) : ChatUiState()

    data class Error(
        val message: String,
        val previousMessages: List<ChatMessage>? = null,
        val canRetry: Boolean = true
    ) : ChatUiState()
}

data class Stats(
    val tokensPerSecond: Double,
    val durationInSeconds: Long,
    val totalTokens: Int = 0
)
```

---

## 3. Refactoring ChatViewModel

**Fichier à modifier**: `viewmodel/ChatViewModel.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import com.nikolaspaci.app.llamallmlocal.engine.ModelEngine
import com.nikolaspaci.app.llamallmlocal.engine.ModelParameterProvider
import com.nikolaspaci.app.llamallmlocal.jni.PredictionEvent
import com.nikolaspaci.app.llamallmlocal.usecase.PredictUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val engine: ModelEngine,
    private val predictUseCase: PredictUseCase,
    private val parameterProvider: ModelParameterProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: Long = savedStateHandle.get<Long>("conversationId") ?: 0L

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var predictionJob: Job? = null
    private var currentMessages: List<ChatMessage> = emptyList()
    private var modelPath: String? = null

    init {
        observeConversation()
        observeModelState()
    }

    private fun observeConversation() {
        viewModelScope.launch {
            chatRepository.getConversation(conversationId)
                .distinctUntilChanged()
                .collect { conversationWithMessages ->
                    conversationWithMessages?.let { cwm ->
                        currentMessages = cwm.messages

                        // Charger le modèle si nécessaire
                        if (modelPath != cwm.conversation.modelPath) {
                            modelPath = cwm.conversation.modelPath
                            loadModel(cwm.conversation.modelPath)
                        }

                        updateUiState()
                    }
                }
        }
    }

    private fun observeModelState() {
        viewModelScope.launch {
            engine.loadState.collect { state ->
                when (state) {
                    is ModelEngine.LoadState.Loading -> {
                        _uiState.value = ChatUiState.ModelLoading(
                            progress = state.progress,
                            modelName = state.modelName
                        )
                    }
                    is ModelEngine.LoadState.Loaded -> {
                        updateUiState()
                        // Restaurer l'historique après chargement
                        engine.restoreHistory(currentMessages)
                    }
                    is ModelEngine.LoadState.Error -> {
                        _uiState.value = ChatUiState.Error(
                            message = state.message,
                            previousMessages = currentMessages,
                            canRetry = true
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun loadModel(path: String) {
        val parameters = parameterProvider.getParametersForModel(path)
        engine.loadModel(path, parameters)
    }

    private fun updateUiState() {
        if (_uiState.value !is ChatUiState.Generating) {
            _uiState.value = ChatUiState.Ready(
                messages = currentMessages,
                modelName = getModelName()
            )
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Sauvegarder le message utilisateur
            val userMessage = ChatMessage(
                conversationId = conversationId,
                sender = Sender.USER,
                message = text.trim()
            )
            chatRepository.addMessageToConversation(userMessage)

            // Lancer la prédiction
            startPrediction(text.trim())
        }
    }

    private fun startPrediction(prompt: String) {
        predictionJob?.cancel()

        predictionJob = viewModelScope.launch {
            var accumulatedResponse = StringBuilder()
            var tokenCount = 0

            _uiState.value = ChatUiState.Generating(
                messages = currentMessages,
                currentResponse = "",
                tokensGenerated = 0,
                modelName = getModelName()
            )

            predictUseCase(prompt, modelPath ?: "")
                .catch { e ->
                    _uiState.value = ChatUiState.Error(
                        message = e.message ?: "Erreur de prédiction",
                        previousMessages = currentMessages,
                        canRetry = true
                    )
                }
                .collect { event ->
                    when (event) {
                        is PredictionEvent.Token -> {
                            accumulatedResponse.append(event.value)
                            tokenCount++

                            _uiState.value = ChatUiState.Generating(
                                messages = currentMessages,
                                currentResponse = accumulatedResponse.toString(),
                                tokensGenerated = tokenCount,
                                modelName = getModelName()
                            )
                        }

                        is PredictionEvent.Completion -> {
                            // Sauvegarder le message du bot
                            val botMessage = ChatMessage(
                                conversationId = conversationId,
                                sender = Sender.BOT,
                                message = accumulatedResponse.toString()
                            )
                            chatRepository.addMessageToConversation(botMessage)

                            _uiState.value = ChatUiState.MessageComplete(
                                messages = currentMessages + botMessage,
                                stats = Stats(
                                    tokensPerSecond = event.tokensPerSecond,
                                    durationInSeconds = event.durationInSeconds,
                                    totalTokens = tokenCount
                                ),
                                modelName = getModelName()
                            )
                        }

                        is PredictionEvent.Error -> {
                            _uiState.value = ChatUiState.Error(
                                message = event.message,
                                previousMessages = currentMessages,
                                canRetry = event.isRecoverable
                            )
                        }

                        else -> {}
                    }
                }
        }
    }

    fun cancelPrediction() {
        predictionJob?.cancel()
        predictionJob = null

        _uiState.value = ChatUiState.Ready(
            messages = currentMessages,
            modelName = getModelName()
        )
    }

    fun retry() {
        currentMessages.lastOrNull { it.sender == Sender.USER }?.let { lastUserMessage ->
            startPrediction(lastUserMessage.message)
        }
    }

    fun changeModel(newModelPath: String) {
        viewModelScope.launch {
            chatRepository.updateConversationModelPath(conversationId, newModelPath)
        }
    }

    private fun getModelName(): String {
        return modelPath?.let { File(it).nameWithoutExtension } ?: "Aucun modèle"
    }

    override fun onCleared() {
        super.onCleared()
        predictionJob?.cancel()
    }
}
```

---

## 4. Composant StreamingMessageBubble

**Fichier à créer**: `ui/chat/StreamingMessageBubble.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun StreamingMessageBubble(
    text: String,
    tokensGenerated: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCursor by remember { mutableStateOf(true) }

    // Animation du curseur clignotant
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            showCursor = !showCursor
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = modifier
            .fillMaxWidth(0.85f)
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            if (text.isEmpty()) {
                // État de chargement initial
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Génération en cours...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            } else {
                // Texte en cours de génération avec curseur
                Text(
                    text = text + if (showCursor) "▋" else " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Indicateur de progression
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$tokensGenerated tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Annuler",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
```

---

## 5. Modifier MessageList

**Fichier à modifier**: `ui/chat/MessageList.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
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

    // Auto-scroll vers le dernier message
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
        // Messages existants
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

        // Message en cours de génération (streaming)
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
```

---

## 6. Modifier ChatScreen

**Fichier à modifier**: `ui/chat/ChatScreen.kt`

```kotlin
// Extraire l'état UI
val uiState by chatViewModel.uiState.collectAsState()

// Dans le Scaffold content:
when (val state = uiState) {
    is ChatUiState.Idle -> {
        // Écran vide initial
        EmptyStateMessage()
    }

    is ChatUiState.ModelLoading -> {
        LoadingScreen(
            modelName = state.modelName,
            progress = state.progress
        )
    }

    is ChatUiState.Ready -> {
        MessageList(
            messages = state.messages,
            streamingState = null,
            lastMessageStats = null,
            onCancelGeneration = {}
        )
    }

    is ChatUiState.Generating -> {
        MessageList(
            messages = state.messages,
            streamingState = StreamingState(
                currentText = state.currentResponse,
                tokensGenerated = state.tokensGenerated
            ),
            lastMessageStats = null,
            onCancelGeneration = { chatViewModel.cancelPrediction() }
        )
    }

    is ChatUiState.MessageComplete -> {
        MessageList(
            messages = state.messages,
            streamingState = null,
            lastMessageStats = state.stats,
            onCancelGeneration = {}
        )
    }

    is ChatUiState.Error -> {
        ErrorScreen(
            message = state.message,
            onRetry = if (state.canRetry) {{ chatViewModel.retry() }} else null
        )
    }
}
```

---

## 7. Composants Auxiliaires

### LoadingScreen

```kotlin
@Composable
fun LoadingScreen(
    modelName: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Chargement de $modelName...",
            style = MaterialTheme.typography.bodyLarge
        )
        if (progress > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(200.dp)
            )
        }
    }
}
```

### ErrorScreen

```kotlin
@Composable
fun ErrorScreen(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        onRetry?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = it) {
                Text("Réessayer")
            }
        }
    }
}
```

---

## 8. Fichiers à Créer/Modifier

### Fichiers à Créer
| Fichier | Description |
|---------|-------------|
| `ui/chat/StreamingMessageBubble.kt` | Bulle de message en streaming |
| `ui/chat/LoadingScreen.kt` | Écran de chargement modèle |
| `ui/chat/ErrorScreen.kt` | Écran d'erreur avec retry |

### Fichiers à Modifier
| Fichier | Modification |
|---------|--------------|
| `jni/PredictionEvent.kt` | Ajouter Progress et Error |
| `viewmodel/ChatViewModel.kt` | Refactoring complet |
| `ui/chat/MessageList.kt` | Ajouter StreamingState |
| `ui/chat/ChatScreen.kt` | Gérer tous les états UI |

---

## 9. Séquence d'Implémentation

1. Enrichir `PredictionEvent.kt`
2. Créer `StreamingMessageBubble.kt`
3. Refactorer `ChatViewModel.kt` avec Hilt
4. Modifier `MessageList.kt`
5. Créer `LoadingScreen.kt` et `ErrorScreen.kt`
6. Modifier `ChatScreen.kt`
7. Tester le streaming complet
