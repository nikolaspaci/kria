package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelParameterRepository
import com.nikolaspaci.app.llamallmlocal.data.validation.ParameterValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val repository: ModelParameterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var modelId: String = ""

    fun loadParameters(modelId: String) {
        this.modelId = modelId
        viewModelScope.launch {
            val params = repository.getModelParameter(modelId)
                ?: ParameterValidator.getDefaultParameters(modelId)
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
                repository.insert(state.parameters)
                _uiState.value = SettingsUiState.Saved
            }
        }
    }

    fun resetToDefaults() {
        val defaults = ParameterValidator.getDefaultParameters(modelId)
        _uiState.value = SettingsUiState.Ready(defaults)
    }
}
