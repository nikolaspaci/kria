package com.nikolaspaci.app.llamallmlocal.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.database.Conversation
import com.nikolaspaci.app.llamallmlocal.data.database.ConversationWithMessages
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

class HistoryViewModel(
    private val chatRepository: ChatRepository,
    private val sharedPreferences: SharedPreferences // Keep for other potential settings
) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        viewModelScope.launch {
            combine(chatRepository.getAllConversations(), _searchQuery) { conversations, query ->
                if (query.isBlank()) {
                    HistoryUiState.Success(conversations)
                } else {
                    val filtered = conversations.filter { it.matchesQuery(query) }
                    HistoryUiState.Success(filtered)
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteConversation(conversation: ConversationWithMessages) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversation.conversation)
        }
    }

    private fun ConversationWithMessages.matchesQuery(query: String): Boolean {
        val q = query.lowercase()
        return messages.any { it.message.lowercase().contains(q) } ||
            conversation.title?.lowercase()?.contains(q) == true ||
            File(conversation.modelPath).name.lowercase().contains(q)
    }
}

sealed class HistoryUiState {
    object Loading : HistoryUiState()
    data class Success(val conversations: List<ConversationWithMessages>) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}
