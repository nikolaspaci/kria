package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.data.database.SystemPromptPreset
import com.nikolaspaci.app.llamallmlocal.data.repository.SystemPromptPresetRepository
import com.nikolaspaci.app.llamallmlocal.data.validation.ParameterValidator
import com.nikolaspaci.app.llamallmlocal.engine.ModelParameterProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SettingsUiState {
    object Loading : SettingsUiState()
    data class Ready(
        val parameters: ModelParameter,
        val validationErrors: Map<String, String> = emptyMap()
    ) : SettingsUiState()
    object Saved : SettingsUiState()
    data class Error(val message: String) : SettingsUiState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val parameterProvider: ModelParameterProvider,
    private val presetRepository: SystemPromptPresetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val systemPromptPresets: StateFlow<List<SystemPromptPreset>> =
        presetRepository.getAllPresets().stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )

    private var filePath: String = ""
    private var currentParameterId: Long = 0L
    private var isConversationMode: Boolean = false
    private var conversationId: Long = -1L

    fun loadParameters(filePath: String) {
        this.filePath = filePath
        this.isConversationMode = false
        viewModelScope.launch {
            val params = parameterProvider.getPendingOrDefaultParameters(filePath)
            currentParameterId = params.id
            _uiState.value = SettingsUiState.Ready(params)
        }
    }

    fun loadParametersForConversation(conversationId: Long, filePath: String) {
        this.filePath = filePath
        this.conversationId = conversationId
        this.isConversationMode = true
        viewModelScope.launch {
            val params = parameterProvider.getParametersForConversation(conversationId, filePath)
            currentParameterId = params.id
            _uiState.value = SettingsUiState.Ready(params)
        }
    }

    fun updateParameter(params: ModelParameter) {
        val validation = ParameterValidator.validate(params)
        _uiState.value = SettingsUiState.Ready(params, validation.errors)
    }

    fun saveParameters() {
        val state = _uiState.value
        if (state is SettingsUiState.Ready && state.validationErrors.isEmpty()) {
            viewModelScope.launch {
                if (isConversationMode) {
                    val toSave = state.parameters.copy(id = currentParameterId)
                    parameterProvider.saveParameters(toSave)
                } else {
                    parameterProvider.savePendingParameters(filePath, state.parameters)
                }
                _uiState.value = SettingsUiState.Saved
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            val defaults = parameterProvider.getDefaultParameters(filePath)
            _uiState.value = SettingsUiState.Ready(defaults.copy(id = currentParameterId))
        }
    }

    fun loadSystemPromptPreset(preset: SystemPromptPreset) {
        val state = _uiState.value
        if (state is SettingsUiState.Ready) {
            val updated = state.parameters.copy(systemPrompt = preset.prompt)
            updateParameter(updated)
        }
    }

    fun saveCurrentAsPreset(name: String) {
        val state = _uiState.value
        if (state is SettingsUiState.Ready) {
            viewModelScope.launch {
                presetRepository.insert(
                    SystemPromptPreset(name = name, prompt = state.parameters.systemPrompt)
                )
            }
        }
    }

    fun deletePreset(preset: SystemPromptPreset) {
        viewModelScope.launch {
            presetRepository.delete(preset)
        }
    }
}
