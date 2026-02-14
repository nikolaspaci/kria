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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class Stats(
    val tokensPerSecond: Double,
    val durationInSeconds: Long,
    val totalTokens: Int = 0
)

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
            val userMessage = ChatMessage(
                conversationId = conversationId,
                sender = Sender.USER,
                message = text.trim()
            )
            chatRepository.addMessageToConversation(userMessage)

            startPrediction(text.trim())
        }
    }

    private fun startPrediction(prompt: String) {
        predictionJob?.cancel()

        predictionJob = viewModelScope.launch {
            val accumulatedResponse = StringBuilder()
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
                        message = e.message ?: "Erreur de prediction",
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
            chatRepository.updateConversationModel(conversationId, newModelPath)
        }
    }

    suspend fun deleteConversation() {
        chatRepository.getConversation(conversationId).firstOrNull()?.let {
            chatRepository.deleteConversation(it.conversation)
        }
    }

    private fun getModelName(): String {
        return modelPath?.let { File(it).nameWithoutExtension } ?: "Aucun modele"
    }

    override fun onCleared() {
        super.onCleared()
        predictionJob?.cancel()
    }
}
